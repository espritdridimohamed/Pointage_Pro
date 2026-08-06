package com.pointagepro.schedule.service;

import com.pointagepro.attendance.entity.EmployeeSchedule;
import com.pointagepro.attendance.entity.WorkSchedule;
import com.pointagepro.attendance.entity.WorkScheduleLine;
import com.pointagepro.attendance.repository.AttendanceSummaryRepository;
import com.pointagepro.attendance.repository.EmployeeScheduleRepository;
import com.pointagepro.attendance.repository.WorkScheduleLineRepository;
import com.pointagepro.attendance.repository.WorkScheduleRepository;
import com.pointagepro.attendance.service.ScheduleAssignmentValidationService;
import com.pointagepro.company.entity.Company;
import com.pointagepro.employee.entity.Employee;
import com.pointagepro.schedule.dto.ScheduleAssignmentRequest;
import com.pointagepro.schedule.dto.ScheduleAssignmentResponse;
import com.pointagepro.schedule.dto.ScheduleLineRequest;
import com.pointagepro.schedule.dto.ScheduleLineResponse;
import com.pointagepro.schedule.dto.WorkScheduleRequest;
import com.pointagepro.schedule.dto.WorkScheduleResponse;
import com.pointagepro.shared.dto.LookupItem;
import com.pointagepro.shared.exception.ConflictException;
import com.pointagepro.shared.exception.DuplicateResourceException;
import com.pointagepro.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Schedule module: work_schedules + work_schedule_lines CRUD and employee_schedules
 * assignment (overlap-guarded via ScheduleAssignmentValidationService). A schedule is
 * deleted only when no employee assignment or attendance summary references it; editing
 * replaces the lines wholesale. At most one default schedule per company.
 */
@Service
@RequiredArgsConstructor
public class ScheduleService {

    private static final int MAX_BREAK_MINUTES = 1440;

    private final WorkScheduleRepository scheduleRepository;
    private final WorkScheduleLineRepository lineRepository;
    private final EmployeeScheduleRepository employeeScheduleRepository;
    private final AttendanceSummaryRepository attendanceSummaryRepository;
    private final ScheduleAssignmentValidationService validationService;

    // ------------------------------------------------------------- work schedules

    @Transactional
    public WorkScheduleResponse createSchedule(Company company, WorkScheduleRequest req) {
        validateScheduleRequest(req);
        String code = req.getCode().trim();
        if (scheduleRepository.existsByCompanyIdAndCode(company.getId(), code)) {
            throw new DuplicateResourceException("WorkSchedule", "code", code);
        }
        WorkSchedule s = new WorkSchedule();
        s.setCompany(company);
        s.setCode(code);
        s.setName(requireText(req.getName(), "name"));
        s.setIsActive(req.getIsActive() != null ? req.getIsActive() : true);
        boolean isDefault = Boolean.TRUE.equals(req.getIsDefault());
        if (isDefault) {
            unsetDefault(company);
        }
        s.setIsDefault(isDefault);
        WorkSchedule saved = scheduleRepository.save(s);
        applyLines(saved, req.getLines());
        return toResponse(saved);
    }

    @Transactional
    public WorkScheduleResponse updateSchedule(Company company, Long id, WorkScheduleRequest req) {
        validateScheduleRequest(req);
        WorkSchedule s = requireSchedule(company, id);
        String code = req.getCode().trim();
        if (!code.equals(s.getCode())
                && scheduleRepository.existsByCompanyIdAndCodeAndIdNot(company.getId(), code, id)) {
            throw new DuplicateResourceException("WorkSchedule", "code", code);
        }
        s.setCode(code);
        s.setName(requireText(req.getName(), "name"));
        if (req.getIsActive() != null) {
            s.setIsActive(req.getIsActive());
        }
        boolean isDefault = Boolean.TRUE.equals(req.getIsDefault());
        if (isDefault && !Boolean.TRUE.equals(s.getIsDefault())) {
            unsetDefault(company);
        }
        s.setIsDefault(isDefault);
        scheduleRepository.save(s);
        lineRepository.deleteAll(lineRepository.findByScheduleId(id));
        applyLines(s, req.getLines());
        return toResponse(s);
    }

    @Transactional
    public void deleteSchedule(Company company, Long id) {
        requireSchedule(company, id);
        if (employeeScheduleRepository.countByScheduleId(id) > 0
                || attendanceSummaryRepository.countByScheduleId(id) > 0) {
            throw new ConflictException(
                    "Planning référencé par des affectations ou des résumés de pointage; "
                            + "désactivez-le (isActive=false) au lieu de le supprimer");
        }
        scheduleRepository.deleteById(id);
    }

    @Transactional(readOnly = true)
    public List<WorkScheduleResponse> listSchedules(Company company) {
        return scheduleRepository.findByCompanyIdOrderByCodeAsc(company.getId()).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public WorkScheduleResponse getSchedule(Company company, Long id) {
        return toResponse(requireSchedule(company, id));
    }

    @Transactional(readOnly = true)
    public List<LookupItem> scheduleLookup(Company company) {
        return scheduleRepository.findByCompanyIdOrderByCodeAsc(company.getId()).stream()
                .map(s -> new LookupItem(s.getId(), s.getCode(), s.getName()))
                .toList();
    }

    // ------------------------------------------------------------- assignments

    @Transactional
    public ScheduleAssignmentResponse assign(Company company, Employee employee,
                                             ScheduleAssignmentRequest req) {
        WorkSchedule s = requireSchedule(company, req.getScheduleId());
        LocalDate from = req.getValidFrom();
        if (from == null) {
            throw new IllegalArgumentException("validFrom is required");
        }
        LocalDate to = req.getValidTo();
        validateWindow(employee, from, to);
        assertNoOverlap(employee, from, to, null);
        EmployeeSchedule es = new EmployeeSchedule();
        es.setEmployee(employee);
        es.setSchedule(s);
        es.setValidFrom(from);
        es.setValidTo(to);
        return toAssignmentResponse(employeeScheduleRepository.save(es));
    }

    /** Flat employee flow: resolves a schedule by company-scoped code (unknown → 400). */
    @Transactional
    public ScheduleAssignmentResponse assignByCode(Company company, Employee employee,
                                                   String code, LocalDate validFrom) {
        WorkSchedule s = scheduleRepository.findByCompanyIdAndCode(company.getId(), code)
                .orElseThrow(() -> new IllegalArgumentException("Planning inconnu: " + code));
        LocalDate from = validFrom == null ? LocalDate.now() : validFrom;
        validateWindow(employee, from, null);
        assertNoOverlap(employee, from, null, null);
        EmployeeSchedule es = new EmployeeSchedule();
        es.setEmployee(employee);
        es.setSchedule(s);
        es.setValidFrom(from);
        es.setValidTo(null);
        return toAssignmentResponse(employeeScheduleRepository.save(es));
    }

    /** Closes the employee's open-ended assignment at {@code validTo} (used by the flat update). */
    @Transactional
    public void closeOpenAssignment(Employee employee, LocalDate validTo) {
        employeeScheduleRepository
                .findFirstByEmployeeIdAndValidToIsNullOrderByValidFromDesc(employee.getId())
                .ifPresent(es -> {
                    es.setValidTo(validTo);
                    employeeScheduleRepository.save(es);
                });
    }

    @Transactional
    public ScheduleAssignmentResponse updateAssignment(Company company, Employee employee,
                                                       Long asgId, ScheduleAssignmentRequest req) {
        EmployeeSchedule es = requireAssignment(company, employee, asgId);
        LocalDate from = req.getValidFrom() != null ? req.getValidFrom() : es.getValidFrom();
        LocalDate to = req.getValidTo();
        validateWindow(employee, from, to);
        if (req.getScheduleId() != null) {
            es.setSchedule(requireSchedule(company, req.getScheduleId()));
        }
        assertNoOverlap(employee, from, to, asgId);
        es.setValidFrom(from);
        es.setValidTo(to);
        return toAssignmentResponse(employeeScheduleRepository.save(es));
    }

    @Transactional
    public void deleteAssignment(Company company, Employee employee, Long asgId) {
        requireAssignment(company, employee, asgId);
        employeeScheduleRepository.deleteById(asgId);
    }

    @Transactional(readOnly = true)
    public List<ScheduleAssignmentResponse> listAssignments(Company company, Employee employee) {
        return employeeScheduleRepository.findByEmployeeIdOrderByValidFromDesc(employee.getId()).stream()
                .map(this::toAssignmentResponse)
                .toList();
    }

    // ------------------------------------------------------------- private

    private void validateScheduleRequest(WorkScheduleRequest req) {
        if (req.getCode() == null || req.getCode().isBlank()) {
            throw new IllegalArgumentException("code is required");
        }
        requireText(req.getName(), "name");
    }

    private void unsetDefault(Company company) {
        scheduleRepository.findFirstByCompanyIdAndIsDefaultTrue(company.getId())
                .ifPresent(s -> {
                    s.setIsDefault(false);
                    scheduleRepository.save(s);
                });
    }

    private void applyLines(WorkSchedule s, List<ScheduleLineRequest> lines) {
        if (lines == null || lines.isEmpty()) {
            return;
        }
        Set<Integer> seen = new HashSet<>();
        for (ScheduleLineRequest line : lines) {
            Integer weekday = line.getWeekday();
            if (weekday == null || weekday < 1 || weekday > 7) {
                throw new IllegalArgumentException("weekday doit être entre 1 et 7");
            }
            if (!seen.add(weekday)) {
                throw new IllegalArgumentException("weekday dupliqué dans le planning: " + weekday);
            }
            boolean workday = line.getIsWorkday() == null || line.getIsWorkday();
            WorkScheduleLine l = new WorkScheduleLine();
            l.setSchedule(s);
            l.setWeekday(weekday);
            l.setIsWorkday(workday);
            if (workday) {
                LocalTime start = line.getStartTime();
                LocalTime end = line.getEndTime();
                if (start == null || end == null) {
                    throw new IllegalArgumentException(
                            "startTime et endTime sont requis pour un jour ouvré (weekday " + weekday + ")");
                }
                if (start.equals(end)) {
                    throw new IllegalArgumentException("startTime et endTime doivent différer (weekday " + weekday + ")");
                }
                l.setStartTime(start);
                l.setEndTime(end);
            }
            int brk = line.getBreakMinutes() != null ? line.getBreakMinutes() : 0;
            if (brk < 0 || brk > MAX_BREAK_MINUTES) {
                throw new IllegalArgumentException("breakMinutes doit être entre 0 et 1440");
            }
            l.setBreakMinutes(brk);
            lineRepository.save(l);
        }
    }

    private void validateWindow(Employee employee, LocalDate from, LocalDate to) {
        if (from.isBefore(employee.getHiringDate())) {
            throw new IllegalArgumentException("validFrom doit être >= date d'embauche "
                    + employee.getHiringDate());
        }
        if (to != null && to.isBefore(from)) {
            throw new IllegalArgumentException("validTo doit être >= validFrom");
        }
    }

    private void assertNoOverlap(Employee employee, LocalDate from, LocalDate to, Long excludeId) {
        try {
            validationService.assertNoOverlap(employee, from, to, excludeId);
        } catch (IllegalArgumentException ex) {
            throw new ConflictException(ex.getMessage());
        }
    }

    private WorkSchedule requireSchedule(Company company, Long id) {
        if (id == null) {
            throw new IllegalArgumentException("scheduleId is required");
        }
        WorkSchedule s = scheduleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("WorkSchedule", "id", id));
        if (!s.getCompany().getId().equals(company.getId())) {
            throw new ResourceNotFoundException("WorkSchedule", "id", id);
        }
        return s;
    }

    private EmployeeSchedule requireAssignment(Company company, Employee employee, Long asgId) {
        EmployeeSchedule es = employeeScheduleRepository.findById(asgId)
                .orElseThrow(() -> new ResourceNotFoundException("EmployeeSchedule", "id", asgId));
        if (!es.getEmployee().getId().equals(employee.getId())) {
            throw new ResourceNotFoundException("EmployeeSchedule", "id", asgId);
        }
        if (es.getSchedule() != null && !es.getSchedule().getCompany().getId().equals(company.getId())) {
            throw new ResourceNotFoundException("EmployeeSchedule", "id", asgId);
        }
        return es;
    }

    private WorkScheduleResponse toResponse(WorkSchedule s) {
        WorkScheduleResponse dto = new WorkScheduleResponse();
        dto.setId(s.getId());
        dto.setCode(s.getCode());
        dto.setName(s.getName());
        dto.setIsDefault(s.getIsDefault());
        dto.setIsActive(s.getIsActive());
        List<WorkScheduleLine> lines = lineRepository.findByScheduleId(s.getId()).stream()
                .sorted(java.util.Comparator.comparing(WorkScheduleLine::getWeekday))
                .toList();
        dto.setLineCount(lines.size());
        dto.setLines(lines.stream().map(this::toLineResponse).toList());
        return dto;
    }

    private ScheduleLineResponse toLineResponse(WorkScheduleLine l) {
        ScheduleLineResponse dto = new ScheduleLineResponse();
        dto.setWeekday(l.getWeekday());
        dto.setIsWorkday(l.getIsWorkday());
        dto.setStartTime(l.getStartTime());
        dto.setEndTime(l.getEndTime());
        dto.setBreakMinutes(l.getBreakMinutes());
        return dto;
    }

    private ScheduleAssignmentResponse toAssignmentResponse(EmployeeSchedule es) {
        ScheduleAssignmentResponse dto = new ScheduleAssignmentResponse();
        dto.setId(es.getId());
        if (es.getSchedule() != null) {
            dto.setScheduleId(es.getSchedule().getId());
            dto.setScheduleCode(es.getSchedule().getCode());
            dto.setScheduleName(es.getSchedule().getName());
        }
        dto.setValidFrom(es.getValidFrom());
        dto.setValidTo(es.getValidTo());
        dto.setCreatedAt(es.getCreatedAt());
        return dto;
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.trim();
    }
}
