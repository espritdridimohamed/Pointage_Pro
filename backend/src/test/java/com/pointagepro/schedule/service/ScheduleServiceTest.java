package com.pointagepro.schedule.service;

import com.pointagepro.attendance.entity.EmployeeSchedule;
import com.pointagepro.attendance.entity.WorkSchedule;
import com.pointagepro.attendance.repository.AttendanceSummaryRepository;
import com.pointagepro.attendance.repository.EmployeeScheduleRepository;
import com.pointagepro.attendance.repository.WorkScheduleLineRepository;
import com.pointagepro.attendance.repository.WorkScheduleRepository;
import com.pointagepro.attendance.service.ScheduleAssignmentValidationService;
import com.pointagepro.company.entity.Company;
import com.pointagepro.employee.entity.Employee;
import com.pointagepro.schedule.dto.ScheduleLineRequest;
import com.pointagepro.schedule.dto.WorkScheduleRequest;
import com.pointagepro.shared.exception.ConflictException;
import com.pointagepro.shared.exception.DuplicateResourceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScheduleServiceTest {

    @Mock private WorkScheduleRepository scheduleRepository;
    @Mock private WorkScheduleLineRepository lineRepository;
    @Mock private EmployeeScheduleRepository employeeScheduleRepository;
    @Mock private AttendanceSummaryRepository attendanceSummaryRepository;
    @Mock private ScheduleAssignmentValidationService validationService;

    @InjectMocks private ScheduleService service;

    private Company company;
    private Employee employee;
    private WorkSchedule schedule;

    @BeforeEach
    void setUp() {
        company = new Company();
        company.setId(1L);
        employee = new Employee();
        employee.setId(10L);
        employee.setCompany(company);
        employee.setHiringDate(LocalDate.of(2026, 1, 1));
        schedule = new WorkSchedule();
        schedule.setId(5L);
        schedule.setCompany(company);
        schedule.setCode("STD");
        schedule.setName("Standard");
        schedule.setIsActive(true);
        schedule.setIsDefault(true);
        lenient().when(scheduleRepository.save(any(WorkSchedule.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(employeeScheduleRepository.save(any(EmployeeSchedule.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void createScheduleRejectsDuplicateCode() {
        when(scheduleRepository.existsByCompanyIdAndCode(1L, "STD")).thenReturn(true);

        assertThatThrownBy(() -> service.createSchedule(company, request("STD", "Standard")))
                .isInstanceOf(DuplicateResourceException.class);
    }

    @Test
    void createScheduleUnsetsExistingDefault() {
        WorkSchedule existingDefault = new WorkSchedule();
        existingDefault.setId(3L);
        existingDefault.setCompany(company);
        existingDefault.setCode("DEFAULT");
        existingDefault.setName("Default");
        existingDefault.setIsDefault(true);
        existingDefault.setIsActive(true);
        when(scheduleRepository.existsByCompanyIdAndCode(1L, "STD")).thenReturn(false);
        when(scheduleRepository.findFirstByCompanyIdAndIsDefaultTrue(1L)).thenReturn(Optional.of(existingDefault));

        var response = service.createSchedule(company, request("STD", "Standard"));

        assertThat(existingDefault.getIsDefault()).isFalse();
        assertThat(response.getIsDefault()).isTrue();
        assertThat(response.getCode()).isEqualTo("STD");
    }

    @Test
    void createScheduleRejectsDuplicateWeekday() {
        when(scheduleRepository.existsByCompanyIdAndCode(1L, "STD")).thenReturn(false);
        WorkScheduleRequest req = request("STD", "Standard");
        req.setLines(List.of(
                line(1, true, LocalTime.of(8, 0), LocalTime.of(17, 0), 60),
                line(1, false, null, null, 0)));

        assertThatThrownBy(() -> service.createSchedule(company, req))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void createScheduleRejectsWorkdayWithoutTimes() {
        when(scheduleRepository.existsByCompanyIdAndCode(1L, "STD")).thenReturn(false);
        WorkScheduleRequest req = request("STD", "Standard");
        req.setLines(List.of(line(1, true, null, null, 0)));

        assertThatThrownBy(() -> service.createSchedule(company, req))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void deleteScheduleReferencedByAssignmentIsRejected() {
        when(scheduleRepository.findById(5L)).thenReturn(Optional.of(schedule));
        when(employeeScheduleRepository.countByScheduleId(5L)).thenReturn(1L);

        assertThatThrownBy(() -> service.deleteSchedule(company, 5L))
                .isInstanceOf(ConflictException.class);
        verify(scheduleRepository, never()).deleteById(5L);
    }

    @Test
    void deleteScheduleUnreferencedDeletes() {
        when(scheduleRepository.findById(5L)).thenReturn(Optional.of(schedule));
        when(employeeScheduleRepository.countByScheduleId(5L)).thenReturn(0L);
        when(attendanceSummaryRepository.countByScheduleId(5L)).thenReturn(0L);

        service.deleteSchedule(company, 5L);

        verify(scheduleRepository).deleteById(5L);
    }

    @Test
    void assignOverlappingWindowThrowsConflict() {
        when(scheduleRepository.findById(5L)).thenReturn(Optional.of(schedule));
        org.mockito.Mockito.doThrow(new IllegalArgumentException("Schedule assignment overlaps"))
                .when(validationService).assertNoOverlap(any(Employee.class), any(LocalDate.class),
                        any(), any());

        com.pointagepro.schedule.dto.ScheduleAssignmentRequest req =
                new com.pointagepro.schedule.dto.ScheduleAssignmentRequest();
        req.setScheduleId(5L);
        req.setValidFrom(LocalDate.of(2026, 2, 1));

        assertThatThrownBy(() -> service.assign(company, employee, req))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void assignRejectsValidFromBeforeHiringDate() {
        when(scheduleRepository.findById(5L)).thenReturn(Optional.of(schedule));
        com.pointagepro.schedule.dto.ScheduleAssignmentRequest req =
                new com.pointagepro.schedule.dto.ScheduleAssignmentRequest();
        req.setScheduleId(5L);
        req.setValidFrom(LocalDate.of(2025, 12, 1));

        assertThatThrownBy(() -> service.assign(company, employee, req))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void assignByCodeRejectsUnknownCode() {
        when(scheduleRepository.findByCompanyIdAndCode(1L, "NOPE")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.assignByCode(company, employee, "NOPE", LocalDate.of(2026, 2, 1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void updateScheduleReplacesLinesWholesale() {
        when(scheduleRepository.findById(5L)).thenReturn(Optional.of(schedule));
        when(lineRepository.findByScheduleId(5L)).thenReturn(List.of());
        WorkScheduleRequest req = request("STD", "Standard updated");
        req.setLines(List.of(line(2, true, LocalTime.of(9, 0), LocalTime.of(18, 0), 30)));

        var response = service.updateSchedule(company, 5L, req);

        assertThat(response.getName()).isEqualTo("Standard updated");
        verify(lineRepository).deleteAll(anyList());
    }

    @Test
    void closeOpenAssignmentSetsValidTo() {
        EmployeeSchedule open = new EmployeeSchedule();
        open.setId(1L);
        open.setEmployee(employee);
        open.setSchedule(schedule);
        open.setValidFrom(LocalDate.of(2026, 1, 1));
        open.setValidTo(null);
        when(employeeScheduleRepository.findFirstByEmployeeIdAndValidToIsNullOrderByValidFromDesc(10L))
                .thenReturn(Optional.of(open));

        service.closeOpenAssignment(employee, LocalDate.of(2026, 8, 6));

        assertThat(open.getValidTo()).isEqualTo(LocalDate.of(2026, 8, 6));
    }

    // ------------------------------------------------------------- helpers

    private WorkScheduleRequest request(String code, String name) {
        WorkScheduleRequest req = new WorkScheduleRequest();
        req.setCode(code);
        req.setName(name);
        req.setIsDefault(true);
        return req;
    }

    private ScheduleLineRequest line(int weekday, boolean workday, LocalTime start, LocalTime end, int breakMinutes) {
        ScheduleLineRequest l = new ScheduleLineRequest();
        l.setWeekday(weekday);
        l.setIsWorkday(workday);
        l.setStartTime(start);
        l.setEndTime(end);
        l.setBreakMinutes(breakMinutes);
        return l;
    }
}
