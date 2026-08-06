package com.pointagepro.attendance.service;

import com.pointagepro.attendance.dto.AttendanceEventResult;
import com.pointagepro.attendance.entity.AttendanceEvent;
import com.pointagepro.attendance.entity.EventType;
import com.pointagepro.attendance.repository.AttendanceEventRepository;
import com.pointagepro.attendance.repository.EventTypeRepository;
import com.pointagepro.auth.entity.User;
import com.pointagepro.company.entity.Company;
import com.pointagepro.employee.entity.Employee;
import com.pointagepro.employee.repository.EmployeeRepository;
import com.pointagepro.shared.exception.DuplicateResourceException;
import com.pointagepro.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;

/**
 * Raw event intake (terminal, online, or manual/offline). Guarantees:
 * <ul>
 *   <li>external_ref idempotency per terminal (replays are no-ops);</li>
 *   <li>duplicate suppression within a 60s window — type-agnostic for type-less terminal
 *       punches (a badge double-read must not create a bogus IN→OUT pair), typed for
 *       explicit-type entries (a manual IN then OUT at the same time is legitimate);</li>
 *   <li>future timestamps beyond now+5min are stored with time_warning=1, never rejected;</li>
 *   <li>company-consistency: the badge's employee must belong to the same company as the
 *       terminal / acting user, otherwise the punch is treated as unknown;</li>
 *   <li>single-punch classification: a type-less scan toggles against the employee's most
 *       recent stored event (IN → next OUT, otherwise IN).</li>
 * </ul>
 * Every stored event triggers a recompute of the affected shift date(s).
 */
@Service
@RequiredArgsConstructor
public class AttendanceEventService {

    private static final Duration DUPLICATE_WINDOW = Duration.ofSeconds(60);
    private static final Duration FUTURE_SKEW = Duration.ofMinutes(5);

    private final AttendanceEventRepository eventRepository;
    private final EventTypeRepository eventTypeRepository;
    private final EmployeeRepository employeeRepository;
    private final AttendanceEngineService engineService;

    /**
     * Records a type-less terminal punch. The event type is derived by alternation unless an
     * explicit override is supplied. Rejections (unknown badge, replay, duplicate, invalid
     * type) are returned as a result, never thrown, so the device gets a flat success:false
     * body.
     */
    @Transactional
    public AttendanceEventResult recordPunch(Company company, Long terminalId, String rfidUid,
                                             String timestampIso, String eventTypeOverride,
                                             String externalRef) {
        Employee employee = rfidUid == null ? null : employeeRepository.findByRfidUid(rfidUid).orElse(null);
        if (employee == null || !employee.getCompany().getId().equals(company.getId())) {
            return AttendanceEventResult.unknownBadge();
        }

        LocalDateTime eventTime = parsePunchTimestamp(timestampIso);

        if (terminalId != null && externalRef != null
                && eventRepository.existsByTerminalIdAndExternalRef(terminalId, externalRef)) {
            return AttendanceEventResult.replay();
        }

        if (eventRepository.findFirstByEmployeeIdAndTerminalIdAndEventTimeBetween(
                employee.getId(), terminalId,
                eventTime.minus(DUPLICATE_WINDOW), eventTime).isPresent()) {
            return AttendanceEventResult.duplicate();
        }

        String typeCode = resolvePunchType(employee.getId(), eventTime, eventTypeOverride);
        if (typeCode == null) {
            return AttendanceEventResult.invalidType();
        }
        EventType eventType = eventTypeRepository.findByCode(typeCode).orElse(null);
        if (eventType == null) {
            return AttendanceEventResult.invalidType();
        }

        AttendanceEvent event = persistEvent(company, employee, eventType, eventTime,
                "TERMINAL", terminalId, externalRef, null);
        return AttendanceEventResult.stored(event, employee);
    }

    /**
     * Records a manual/online entry with an explicit type. Missing employee and invalid type
     * are hard errors (the caller is a human), so they throw.
     */
    @Transactional
    public AttendanceEventResult recordManual(Company company, Long employeeId, String eventTypeCode,
                                              LocalDateTime eventTime, User computedBy) {
        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found: " + employeeId));
        if (!employee.getCompany().getId().equals(company.getId())) {
            throw new ResourceNotFoundException("Employee not found: " + employeeId);
        }

        EventType eventType = eventTypeRepository.findByCode(eventTypeCode)
                .orElseThrow(() -> new IllegalArgumentException("Unknown event type: " + eventTypeCode));

        if (eventRepository.findFirstByEmployeeIdAndTerminalIdAndEventTypeCodeAndEventTimeBetween(
                employee.getId(), null, eventType.getCode(),
                eventTime.minus(DUPLICATE_WINDOW), eventTime).isPresent()) {
            throw new DuplicateResourceException(
                    "Duplicate manual event for employee " + employeeId + " within 60 seconds");
        }

        AttendanceEvent event = persistEvent(company, employee, eventType, eventTime,
                "MANUAL", null, null, computedBy);
        return AttendanceEventResult.stored(event, employee);
    }

    /**
     * Lists raw events for one employee of the given company, ascending by event time.
     * Tenant rule enforced here: an employee of another company is not found.
     */
    @Transactional(readOnly = true)
    public List<AttendanceEvent> listForEmployee(Company company, Long employeeId,
                                                 LocalDateTime from, LocalDateTime to) {
        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found: " + employeeId));
        if (!employee.getCompany().getId().equals(company.getId())) {
            throw new ResourceNotFoundException("Employee not found: " + employeeId);
        }
        return eventRepository.findByEmployeeIdAndEventTimeBetweenOrderByEventTimeAsc(employeeId, from, to);
    }

    private AttendanceEvent persistEvent(Company company, Employee employee, EventType eventType,
                                         LocalDateTime eventTime, String source, Long terminalId,
                                         String externalRef, User computedBy) {
        AttendanceEvent event = new AttendanceEvent();
        event.setCompany(company);
        event.setEmployee(employee);
        event.setTerminalId(terminalId);
        event.setEventType(eventType);
        event.setEventTime(eventTime);
        event.setRfidUid(employee.getRfidUid());
        event.setSource(source != null ? source : "TERMINAL");
        event.setExternalRef(externalRef);
        event.setTimeWarning(eventTime.isAfter(LocalDateTime.now().plus(FUTURE_SKEW)));
        event = eventRepository.save(event);

        // Initialize lazy associations while the session is open so the returned entity can
        // be mapped to DTOs by controllers after the transaction commits.
        event.getEventType().getCode();
        event.getEmployee().getFirstName();

        LocalDate eventDate = eventTime.toLocalDate();
        recomputeAffected(company.getId(), employee.getId(), eventDate.minusDays(1), eventDate, computedBy);
        return event;
    }

    private String resolvePunchType(Long employeeId, LocalDateTime eventTime, String override) {
        if (override != null) {
            if (!"IN".equals(override) && !"OUT".equals(override)) {
                return null;
            }
            return override;
        }
        AttendanceEvent last = eventRepository
                .findFirstByEmployeeIdAndEventTimeLessThanOrderByEventTimeDesc(employeeId, eventTime)
                .orElse(null);
        return last != null && "IN".equals(last.getEventType().getCode()) ? "OUT" : "IN";
    }

    private LocalDateTime parsePunchTimestamp(String iso) {
        if (iso == null || iso.isBlank()) {
            return LocalDateTime.now();
        }
        try {
            return LocalDateTime.parse(iso);
        } catch (DateTimeParseException ex) {
            throw new IllegalArgumentException(
                    "timestamp must be ISO-8601 yyyy-MM-dd'T'HH:mm:ss, got: " + iso);
        }
    }

    private void recomputeAffected(Long companyId, Long employeeId, LocalDate from, LocalDate to, User computedBy) {
        engineService.recompute(companyId, employeeId, from, to, computedBy, "event:" + from);
    }
}
