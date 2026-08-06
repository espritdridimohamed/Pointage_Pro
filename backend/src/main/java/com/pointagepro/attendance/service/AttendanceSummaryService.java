package com.pointagepro.attendance.service;

import com.pointagepro.attendance.dto.RecomputeStats;
import com.pointagepro.attendance.entity.AttendanceSummary;
import com.pointagepro.attendance.repository.AttendanceSummaryRepository;
import com.pointagepro.auth.entity.User;
import com.pointagepro.company.entity.Company;
import com.pointagepro.employee.entity.Employee;
import com.pointagepro.employee.repository.EmployeeRepository;
import com.pointagepro.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Summary / day-status / recompute API facade. Thin rules live here, the arithmetic stays in
 * {@link AttendanceEngineService} + {@code DayCalculator}.
 * <ul>
 *   <li>Tenant rule: the target employee (or the summary's employee) must belong to the
 *       authenticated user's company, otherwise the resource is reported 404.</li>
 *   <li>Range rule: {@code from <= to} and span ≤ 366 calendar days, else 400 (protects the
 *       DB from accidental full-history recomputes).</li>
 *   <li>Day-status compute-on-miss: {@link #day} materializes the summary for the requested
 *       date if absent (idempotent single-day recompute, audit reason {@code api:day}).
 *       Reads never leak entities: the returned summaries are mapped to DTOs with the
 *       repository's entity graphs.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class AttendanceSummaryService {

    private static final long MAX_SPAN_DAYS = 365;

    private final AttendanceSummaryRepository summaryRepository;
    private final AttendanceEngineService engineService;
    private final EmployeeRepository employeeRepository;

    @Transactional(readOnly = true)
    public List<AttendanceSummary> listForEmployee(Company company, Long employeeId,
                                                   LocalDate from, LocalDate to) {
        Employee employee = requireSameCompany(company, employeeId);
        validateRange(from, to);
        return summaryRepository.findWithDetailsByEmployeeIdAndWorkDateBetweenOrderByWorkDateAsc(
                employee.getId(), from, to);
    }

    @Transactional(readOnly = true)
    public AttendanceSummary getById(Company company, Long summaryId) {
        AttendanceSummary summary = summaryRepository.findWithDetailsById(summaryId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Attendance summary not found: " + summaryId));
        if (!summary.getEmployee().getCompany().getId().equals(company.getId())) {
            throw new ResourceNotFoundException("Attendance summary not found: " + summaryId);
        }
        return summary;
    }

    /**
     * Day status for one employee on one date. If no summary exists the engine recomputes that
     * single day on demand (idempotent, audit reason {@code api:day}); the summary row is
     * always materialized for a recomputed day, so a recomputable date never 404s.
     */
    @Transactional
    public AttendanceSummary day(Company company, Long employeeId, LocalDate date, User computedBy) {
        Employee employee = requireSameCompany(company, employeeId);
        return summaryRepository.findWithDetailsByEmployeeIdAndWorkDate(employee.getId(), date)
                .orElseGet(() -> {
                    engineService.recompute(company.getId(), employee.getId(), date, date,
                            computedBy, "api:day");
                    return summaryRepository.findWithDetailsByEmployeeIdAndWorkDate(employee.getId(), date)
                            .orElseThrow(() -> new ResourceNotFoundException(
                                    "Attendance summary not found: " + employeeId + " on " + date));
                });
    }

    @Transactional
    public List<AttendanceSummary> recomputeForEmployee(Company company, Long employeeId,
                                                        LocalDate from, LocalDate to,
                                                        User computedBy, String reason) {
        Employee employee = requireSameCompany(company, employeeId);
        validateRange(from, to);
        String effReason = reason != null && !reason.isBlank() ? reason : "api:recompute";
        engineService.recompute(company.getId(), employee.getId(), from, to, computedBy, effReason);
        return summaryRepository.findWithDetailsByEmployeeIdAndWorkDateBetweenOrderByWorkDateAsc(
                employee.getId(), from, to);
    }

    @Transactional
    public RecomputeStats recomputeCompany(Company company, LocalDate from, LocalDate to,
                                           User computedBy, String reason) {
        validateRange(from, to);
        String effReason = reason != null && !reason.isBlank() ? reason : "api:recompute-all";
        int employees = engineService.recomputeAll(company.getId(), from, to, computedBy, effReason);
        int dayCount = Math.toIntExact(employees * (ChronoUnit.DAYS.between(from, to) + 1));
        return new RecomputeStats(company.getId(), from, to, employees, dayCount);
    }

    private Employee requireSameCompany(Company company, Long employeeId) {
        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found: " + employeeId));
        if (!employee.getCompany().getId().equals(company.getId())) {
            throw new ResourceNotFoundException("Employee not found: " + employeeId);
        }
        return employee;
    }

    private void validateRange(LocalDate from, LocalDate to) {
        if (from.isAfter(to)) {
            throw new IllegalArgumentException("'from' must not be after 'to'");
        }
        if (ChronoUnit.DAYS.between(from, to) > MAX_SPAN_DAYS) {
            throw new IllegalArgumentException(
                    "Date range must not span more than " + (MAX_SPAN_DAYS + 1) + " days");
        }
    }
}
