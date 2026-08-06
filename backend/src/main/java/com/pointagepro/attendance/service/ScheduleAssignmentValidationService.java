package com.pointagepro.attendance.service;

import com.pointagepro.attendance.entity.EmployeeSchedule;
import com.pointagepro.attendance.repository.EmployeeScheduleRepository;
import com.pointagepro.employee.entity.Employee;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

/**
 * Enforces that an employee has at most one active schedule assignment at any date:
 * new assignments must not overlap any existing assignment range (open-ended ranges
 * with {@code valid_to IS NULL} extend to the future, so two open-ended rows are
 * always overlapping).
 */
@Service
@RequiredArgsConstructor
public class ScheduleAssignmentValidationService {

    private final EmployeeScheduleRepository employeeScheduleRepository;

    public void assertNoOverlap(Employee employee, LocalDate validFrom, LocalDate validTo, Long excludeId) {
        List<EmployeeSchedule> existing = employeeScheduleRepository.findByEmployeeIdOrderByValidFromDesc(employee.getId());
        for (EmployeeSchedule es : existing) {
            if (excludeId != null && excludeId.equals(es.getId())) {
                continue;
            }
            if (overlaps(validFrom, validTo, es.getValidFrom(), es.getValidTo())) {
                throw new IllegalArgumentException("Schedule assignment overlaps existing assignment "
                        + es.getSchedule().getCode() + " [" + es.getValidFrom() + " .. "
                        + (es.getValidTo() == null ? "open" : es.getValidTo()) + "]");
            }
        }
    }

    private boolean overlaps(LocalDate newFrom, LocalDate newTo, LocalDate existFrom, LocalDate existTo) {
        LocalDate newEnd = newTo == null ? LocalDate.MAX : newTo;
        LocalDate existEnd = existTo == null ? LocalDate.MAX : existTo;
        return !newFrom.isAfter(existEnd) && !existFrom.isAfter(newEnd);
    }
}
