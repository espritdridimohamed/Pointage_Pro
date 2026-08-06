package com.pointagepro.attendance.service;

import com.pointagepro.attendance.dto.RecomputeStats;
import com.pointagepro.attendance.entity.AttendanceStatus;
import com.pointagepro.attendance.entity.AttendanceSummary;
import com.pointagepro.attendance.entity.DayType;
import com.pointagepro.attendance.entity.WorkSchedule;
import com.pointagepro.attendance.repository.AttendanceSummaryRepository;
import com.pointagepro.auth.entity.User;
import com.pointagepro.company.entity.Company;
import com.pointagepro.employee.entity.Employee;
import com.pointagepro.employee.repository.EmployeeRepository;
import com.pointagepro.shared.exception.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AttendanceSummaryServiceTest {

    private static final long COMPANY_ID = 1L;
    private static final long EMPLOYEE_ID = 10L;

    @Mock
    private AttendanceSummaryRepository summaryRepository;
    @Mock
    private AttendanceEngineService engineService;
    @Mock
    private EmployeeRepository employeeRepository;

    @InjectMocks
    private AttendanceSummaryService service;

    private static Company company() {
        Company c = new Company();
        c.setId(COMPANY_ID);
        return c;
    }

    private static Employee employee() {
        Employee e = new Employee();
        e.setId(EMPLOYEE_ID);
        e.setCompany(company());
        e.setFirstName("Ahmed");
        e.setLastName("Ben Salah");
        return e;
    }

    private static Employee employeeOfOtherCompany() {
        Employee e = employee();
        Company other = new Company();
        other.setId(99L);
        e.setCompany(other);
        return e;
    }

    private static AttendanceStatus status(String code) {
        AttendanceStatus s = new AttendanceStatus();
        s.setCode(code);
        s.setLabel(code.toLowerCase());
        return s;
    }

    private static DayType dayType(String code) {
        DayType d = new DayType();
        d.setCode(code);
        d.setLabel(code.toLowerCase());
        return d;
    }

    private static AttendanceSummary summary(LocalDate workDate) {
        AttendanceSummary s = new AttendanceSummary();
        s.setId(1001L);
        s.setCompany(company());
        s.setEmployee(employee());
        s.setWorkDate(workDate);
        s.setDayType(dayType("WORKDAY"));
        WorkSchedule schedule = new WorkSchedule();
        schedule.setId(3L);
        schedule.setCode("STD-08-17");
        s.setSchedule(schedule);
        s.setFirstIn(LocalTime.of(8, 2));
        s.setLastOut(LocalTime.of(17, 5));
        s.setWorkedMinutes(480);
        s.setLateMinutes(2);
        s.setEarlyExitMinutes(0);
        s.setMissingMinutes(0);
        s.setOvertimeMinutes(5);
        s.setNettedWorkMinutes(0);
        s.setAdjustmentMinutes(0);
        s.setIsWeekend(false);
        s.setIsHoliday(false);
        s.setStatus(status("PRESENT"));
        s.setComputedAt(LocalDateTime.of(2026, 8, 5, 17, 5));
        s.setRecomputeReason("event:2026-08-04");
        User u = new User();
        u.setId(3L);
        u.setFullName("Sami Trabelsi");
        s.setComputedBy(u);
        return s;
    }

    @Test
    void listForEmployeeReturnsSummariesOfSameCompany() {
        when(employeeRepository.findById(EMPLOYEE_ID)).thenReturn(Optional.of(employee()));
        AttendanceSummary s = summary(LocalDate.of(2026, 8, 5));
        when(summaryRepository.findWithDetailsByEmployeeIdAndWorkDateBetweenOrderByWorkDateAsc(
                eq(EMPLOYEE_ID), any(), any())).thenReturn(List.of(s));

        List<AttendanceSummary> result = service.listForEmployee(
                company(), EMPLOYEE_ID, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo(1001L);
    }

    @Test
    void listForEmployeeCrossCompanyThrows() {
        when(employeeRepository.findById(EMPLOYEE_ID)).thenReturn(Optional.of(employeeOfOtherCompany()));

        assertThatThrownBy(() -> service.listForEmployee(
                company(), EMPLOYEE_ID, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void listForEmployeeRejectsFromAfterTo() {
        when(employeeRepository.findById(EMPLOYEE_ID)).thenReturn(Optional.of(employee()));

        assertThatThrownBy(() -> service.listForEmployee(
                company(), EMPLOYEE_ID, LocalDate.of(2026, 8, 31), LocalDate.of(2026, 8, 1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("'from' must not be after 'to'");
    }

    @Test
    void listForEmployeeRejectsSpanOver366Days() {
        when(employeeRepository.findById(EMPLOYEE_ID)).thenReturn(Optional.of(employee()));

        assertThatThrownBy(() -> service.listForEmployee(
                company(), EMPLOYEE_ID, LocalDate.of(2025, 8, 1), LocalDate.of(2026, 8, 31)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not span more than 366 days");
    }

    @Test
    void getByIdReturnsSummaryOfSameCompany() {
        AttendanceSummary s = summary(LocalDate.of(2026, 8, 5));
        when(summaryRepository.findWithDetailsById(1001L)).thenReturn(Optional.of(s));

        AttendanceSummary result = service.getById(company(), 1001L);

        assertThat(result.getId()).isEqualTo(1001L);
    }

    @Test
    void getByIdNotFoundThrows() {
        when(summaryRepository.findWithDetailsById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getById(company(), 999L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getByIdCrossCompanyThrows() {
        AttendanceSummary s = summary(LocalDate.of(2026, 8, 5));
        Employee other = employeeOfOtherCompany();
        s.setEmployee(other);
        when(summaryRepository.findWithDetailsById(1001L)).thenReturn(Optional.of(s));

        assertThatThrownBy(() -> service.getById(company(), 1001L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void dayReturnsExistingSummaryWithoutRecompute() {
        when(employeeRepository.findById(EMPLOYEE_ID)).thenReturn(Optional.of(employee()));
        AttendanceSummary s = summary(LocalDate.of(2026, 8, 5));
        when(summaryRepository.findWithDetailsByEmployeeIdAndWorkDate(eq(EMPLOYEE_ID), any()))
                .thenReturn(Optional.of(s));

        AttendanceSummary result = service.day(
                company(), EMPLOYEE_ID, LocalDate.of(2026, 8, 5), null);

        assertThat(result.getId()).isEqualTo(1001L);
        verify(engineService, never()).recompute(any(), any(), any(), any(), any(), any());
    }

    @Test
    void dayMaterializesMissingSummaryOnDemand() {
        when(employeeRepository.findById(EMPLOYEE_ID)).thenReturn(Optional.of(employee()));
        AttendanceSummary s = summary(LocalDate.of(2026, 8, 5));
        when(summaryRepository.findWithDetailsByEmployeeIdAndWorkDate(eq(EMPLOYEE_ID), any()))
                .thenReturn(Optional.empty(), Optional.of(s));

        AttendanceSummary result = service.day(
                company(), EMPLOYEE_ID, LocalDate.of(2026, 8, 5), null);

        assertThat(result.getId()).isEqualTo(1001L);
        verify(engineService).recompute(
                eq(COMPANY_ID), eq(EMPLOYEE_ID), any(), any(), any(), eq("api:day"));
    }

    @Test
    void recomputeForEmployeeCallsEngineAndReturnsSummaries() {
        when(employeeRepository.findById(EMPLOYEE_ID)).thenReturn(Optional.of(employee()));
        AttendanceSummary s = summary(LocalDate.of(2026, 8, 5));
        when(summaryRepository.findWithDetailsByEmployeeIdAndWorkDateBetweenOrderByWorkDateAsc(
                eq(EMPLOYEE_ID), any(), any())).thenReturn(List.of(s));

        List<AttendanceSummary> result = service.recomputeForEmployee(
                company(), EMPLOYEE_ID, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31),
                null, "correction");

        assertThat(result).hasSize(1);
        verify(engineService).recompute(
                eq(COMPANY_ID), eq(EMPLOYEE_ID), any(), any(), any(), eq("correction"));
    }

    @Test
    void recomputeForEmployeeDefaultsReasonWhenBlank() {
        when(employeeRepository.findById(EMPLOYEE_ID)).thenReturn(Optional.of(employee()));
        when(summaryRepository.findWithDetailsByEmployeeIdAndWorkDateBetweenOrderByWorkDateAsc(
                eq(EMPLOYEE_ID), any(), any())).thenReturn(List.of());

        service.recomputeForEmployee(
                company(), EMPLOYEE_ID, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31),
                null, "  ");

        verify(engineService).recompute(
                eq(COMPANY_ID), eq(EMPLOYEE_ID), any(), any(), any(), eq("api:recompute"));
    }

    @Test
    void recomputeForEmployeeCrossCompanyThrows() {
        when(employeeRepository.findById(EMPLOYEE_ID)).thenReturn(Optional.of(employeeOfOtherCompany()));

        assertThatThrownBy(() -> service.recomputeForEmployee(
                company(), EMPLOYEE_ID, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31),
                null, null))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void recomputeForEmployeeRejectsWideRange() {
        when(employeeRepository.findById(EMPLOYEE_ID)).thenReturn(Optional.of(employee()));

        assertThatThrownBy(() -> service.recomputeForEmployee(
                company(), EMPLOYEE_ID, LocalDate.of(2025, 8, 1), LocalDate.of(2026, 8, 31),
                null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void recomputeCompanyReturnsStats() {
        when(engineService.recomputeAll(eq(COMPANY_ID), any(), any(), any(), any()))
                .thenReturn(3);

        RecomputeStats stats = service.recomputeCompany(
                company(), LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31), null, "fermeture");

        assertThat(stats.companyId()).isEqualTo(COMPANY_ID);
        assertThat(stats.employeeCount()).isEqualTo(3);
        assertThat(stats.dayCount()).isEqualTo(3 * 31);
        verify(engineService).recomputeAll(
                eq(COMPANY_ID), any(), any(), any(), eq("fermeture"));
    }

    @Test
    void recomputeCompanyRejectsFromAfterTo() {
        assertThatThrownBy(() -> service.recomputeCompany(
                company(), LocalDate.of(2026, 8, 31), LocalDate.of(2026, 8, 1), null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
