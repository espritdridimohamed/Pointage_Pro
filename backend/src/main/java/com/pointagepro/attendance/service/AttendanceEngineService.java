package com.pointagepro.attendance.service;

import com.pointagepro.attendance.engine.*;
import com.pointagepro.attendance.entity.*;
import com.pointagepro.attendance.repository.*;
import com.pointagepro.auth.entity.User;
import com.pointagepro.auth.repository.UserRepository;
import com.pointagepro.employee.entity.Employee;
import com.pointagepro.employee.repository.EmployeeRepository;
import com.pointagepro.leave.repository.LeaveRequestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Orchestrates one day's computation: resolves the schedule, classifies the day
 * (leave / holiday / weekend / workday), gathers raw events + applied adjustments,
 * delegates the arithmetic to {@link DayCalculator}, and upserts the summary row.
 * Idempotent: recomputing a range always overwrites the same summaries.
 *
 * Adjustments attach to a summary via {@code attendance_adjustments.summary_id}.
 * {@link #computeDay} gathers APPLIED adjustments (attached to the existing summary,
 * if any, plus unattached ones) and delegates the rest to {@link DayCalculator}.
 * {@link #previewDay} runs the same computation read-only without persisting, so the
 * adjustment workflow can dry-run a correction before approving it.
 */
@Service
@RequiredArgsConstructor
public class AttendanceEngineService {

    private final AttendanceEventRepository eventRepository;
    private final AttendanceSummaryRepository summaryRepository;
    private final AttendanceAdjustmentRepository adjustmentRepository;
    private final AttendanceStatusRepository statusRepository;
    private final DayTypeRepository dayTypeRepository;
    private final HolidayRepository holidayRepository;
    private final EmployeeScheduleRepository employeeScheduleRepository;
    private final WorkScheduleRepository workScheduleRepository;
    private final WorkScheduleLineRepository workScheduleLineRepository;
    private final LeaveRequestRepository leaveRequestRepository;
    private final EmployeeRepository employeeRepository;
    private final UserRepository userRepository;

    @Transactional
    public void recompute(Long companyId, Long employeeId, LocalDate from, LocalDate to, User computedBy, String reason) {
        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new IllegalArgumentException("Employee not found: " + employeeId));
        Long effCompanyId = companyId != null ? companyId : employee.getCompany().getId();
        for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
            recomputeDay(effCompanyId, employee, date, computedBy, reason);
        }
    }

    @Transactional
    public int recomputeAll(Long companyId, LocalDate from, LocalDate to, User computedBy, String reason) {
        List<Employee> employees = employeeRepository.findByCompanyIdOrderByLastNameAsc(companyId);
        for (Employee employee : employees) {
            recompute(companyId, employee.getId(), from, to, computedBy, reason);
        }
        return employees.size();
    }

    /**
     * Read-only dry run: computes a day exactly as a recompute would (events + APPLIED
     * adjustments), without persisting anything. Used by the adjustment workflow to
     * validate a correction against the daily cap before approving it.
     */
    @Transactional(readOnly = true)
    public DayResult previewDay(Long companyId, Long employeeId, LocalDate date) {
        return previewDay(companyId, employeeId, date, List.of());
    }

    /**
     * Read-only dry run including prospective (not yet applied) adjustment inputs, so the
     * adjustment workflow can validate a correction against the daily cap before approving.
     */
    @Transactional(readOnly = true)
    public DayResult previewDay(Long companyId, Long employeeId, LocalDate date,
                                List<AdjustmentInput> extraAdjustments) {
        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new IllegalArgumentException("Employee not found: " + employeeId));
        Long effCompanyId = companyId != null ? companyId : employee.getCompany().getId();
        return computeDay(effCompanyId, employee, date, extraAdjustments).result();
    }

    /** Single-day recompute entry point used by the adjustment workflow when a correction is applied. */
    @Transactional
    public void recomputeDay(Long companyId, Long employeeId, LocalDate date, User computedBy, String reason) {
        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new IllegalArgumentException("Employee not found: " + employeeId));
        Long effCompanyId = companyId != null ? companyId : employee.getCompany().getId();
        recomputeDay(effCompanyId, employee, date, computedBy, reason);
    }

    private void recomputeDay(Long companyId, Employee employee, LocalDate date, User computedBy, String reason) {
        ComputedDay computed = computeDay(companyId, employee, date, List.of());
        upsertSummary(companyId, employee, date, computed.dayType(), computed.schedule(), computed.result(),
                computedBy, reason, computed.onApprovedLeave());
    }

    private ComputedDay computeDay(Long companyId, Employee employee, LocalDate date,
                                   List<AdjustmentInput> extraAdjustments) {
        boolean onApprovedLeave = leaveRequestRepository
                .existsByEmployeeIdAndStatusCodeAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
                        employee.getId(), "APPROVED", date, date);

        DayKind kind = DayKind.NOT_SCHEDULED;
        DayType dayType = null;
        WorkSchedule schedule = null;
        WorkScheduleLine line = null;

        boolean holiday = holidayRepository.existsByCompanyIdAndHolidayDate(companyId, date);
        if (holiday) {
            kind = DayKind.HOLIDAY;
            dayType = dayTypeRepository.findByCode("HOLIDAY").orElse(null);
        } else {
            Optional<WorkSchedule> resolved = resolveSchedule(employee, date);
            if (resolved.isPresent()) {
                schedule = resolved.get();
                line = workScheduleLineRepository
                        .findByScheduleIdAndWeekday(schedule.getId(), date.getDayOfWeek().getValue()).orElse(null);
                if (line != null) {
                    if (Boolean.TRUE.equals(line.getIsWorkday())) {
                        kind = DayKind.WORKDAY;
                        dayType = dayTypeRepository.findByCode("WORKDAY").orElse(null);
                    } else {
                        kind = DayKind.WEEKEND;
                        dayType = dayTypeRepository.findByCode("WEEKEND").orElse(null);
                    }
                }
            }
        }

        LocalDate windowEnd = kind == DayKind.WORKDAY && isNight(line) ? date.plusDays(1) : date;
        List<EventInput> eventInputs = new ArrayList<>();
        for (AttendanceEvent ev : eventRepository.findByEmployeeIdAndEventTimeBetweenOrderByEventTimeAsc(
                employee.getId(), date.atStartOfDay(), windowEnd.atTime(LocalTime.MAX))) {
            long minute = Duration.between(date.atStartOfDay(), ev.getEventTime()).toMinutes();
            eventInputs.add(new EventInput(ev.getEventType().getCode(), (int) minute));
        }

        DayInput input = new DayInput(employee.getId(), date, kind, onApprovedLeave,
                line == null ? null : line.getStartTime(),
                line == null ? null : line.getEndTime(),
                line == null ? 0 : line.getBreakMinutes(),
                eventInputs, List.of());

        List<AdjustmentInput> adjustmentInputs = new ArrayList<>();
        summaryRepository.findByEmployeeIdAndWorkDate(employee.getId(), date).ifPresent(summary ->
                adjustmentRepository.findBySummaryId(summary.getId()).stream()
                        .filter(a -> "APPLIED".equals(a.getStatus().getCode()))
                        .forEach(a -> adjustmentInputs.add(
                                new AdjustmentInput(a.getAdjustmentType().getCode(), a.getMinutes()))));
        adjustmentRepository.findByEmployeeIdAndSummaryIsNull(employee.getId()).stream()
                .filter(a -> "APPLIED".equals(a.getStatus().getCode()))
                .forEach(a -> adjustmentInputs.add(
                        new AdjustmentInput(a.getAdjustmentType().getCode(), a.getMinutes())));
        adjustmentInputs.addAll(extraAdjustments);

        DayResult base = DayCalculator.calculate(input);
        DayResult result;
        if (adjustmentInputs.isEmpty()) {
            result = base;
        } else {
            DayInput withAdj = new DayInput(employee.getId(), date, kind, onApprovedLeave,
                    line == null ? null : line.getStartTime(),
                    line == null ? null : line.getEndTime(),
                    line == null ? 0 : line.getBreakMinutes(),
                    eventInputs, adjustmentInputs);
            result = DayCalculator.calculate(withAdj);
        }
        return new ComputedDay(dayType, schedule, onApprovedLeave, result);
    }

    private record ComputedDay(DayType dayType, WorkSchedule schedule, boolean onApprovedLeave,
                               DayResult result) {
    }

    private AttendanceSummary upsertSummary(Long companyId, Employee employee, LocalDate date, DayType dayType,
                                            WorkSchedule schedule, DayResult result, User computedBy,
                                            String reason, boolean onApprovedLeave) {
        AttendanceSummary summary = summaryRepository.findByEmployeeIdAndWorkDate(employee.getId(), date)
                .orElseGet(AttendanceSummary::new);
        summary.setCompany(employee.getCompany());
        summary.setEmployee(employee);
        summary.setWorkDate(date);
        summary.setDayType(dayType);
        summary.setSchedule(schedule);
        summary.setFirstIn(toTime(result.getFirstInMinute()));
        summary.setLastOut(toTime(result.getLastOutMinute()));
        summary.setWorkedMinutes(result.getWorkedMinutes());
        summary.setLateMinutes(result.getLateMinutes());
        summary.setEarlyExitMinutes(result.getEarlyExitMinutes());
        summary.setMissingMinutes(result.getMissingMinutes());
        summary.setOvertimeMinutes(result.getOvertimeMinutes());
        summary.setNettedWorkMinutes(0);
        summary.setIsWeekend(DayKind.WEEKEND == kindOf(result, dayType));
        summary.setIsHoliday(DayKind.HOLIDAY == kindOf(result, dayType));
        summary.setStatus(statusRepository.findByCode(result.getStatusCode()).orElse(null));
        summary.setAdjustmentMinutes(result.getAdjustmentMinutes());
        summary.setComputedAt(LocalDateTime.now());
        summary.setComputedBy(resolveComputedBy(computedBy));
        summary.setRecomputeReason(reason != null ? reason : "recompute");
        return summaryRepository.save(summary);
    }

    private User resolveComputedBy(User computedBy) {
        if (computedBy == null || computedBy.getId() == null) {
            return null;
        }
        return userRepository.getReferenceById(computedBy.getId());
    }

    private Optional<WorkSchedule> resolveSchedule(Employee employee, LocalDate date) {
        for (EmployeeSchedule es : employeeScheduleRepository.findByEmployeeIdOrderByValidFromDesc(employee.getId())) {
            if (!es.getValidFrom().isAfter(date)
                    && (es.getValidTo() == null || !es.getValidTo().isBefore(date))) {
                return Optional.of(es.getSchedule());
            }
        }
        return workScheduleRepository.findFirstByCompanyIdAndIsDefaultTrue(employee.getCompany().getId());
    }

    private boolean isNight(WorkScheduleLine line) {
        return line != null && line.getStartTime() != null && line.getEndTime() != null
                && line.getEndTime().isBefore(line.getStartTime());
    }

    private DayKind kindOf(DayResult result, DayType dayType) {
        if (dayType != null && "WEEKEND".equals(dayType.getCode())) return DayKind.WEEKEND;
        if (dayType != null && "HOLIDAY".equals(dayType.getCode())) return DayKind.HOLIDAY;
        return DayKind.WORKDAY;
    }

    private LocalTime toTime(Integer minute) {
        return minute == null ? null : LocalTime.of(minute / 60, minute % 60);
    }
}
