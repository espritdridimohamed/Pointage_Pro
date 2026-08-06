package com.pointagepro.attendance.controller;

import com.pointagepro.attendance.dto.RecomputeStats;
import com.pointagepro.attendance.entity.AttendanceStatus;
import com.pointagepro.attendance.entity.AttendanceSummary;
import com.pointagepro.attendance.entity.DayType;
import com.pointagepro.attendance.entity.WorkSchedule;
import com.pointagepro.attendance.service.AttendanceSummaryService;
import com.pointagepro.auth.entity.User;
import com.pointagepro.auth.service.CurrentUserService;
import com.pointagepro.company.entity.Company;
import com.pointagepro.employee.entity.Employee;
import com.pointagepro.shared.exception.GlobalExceptionHandler;
import com.pointagepro.shared.exception.ResourceNotFoundException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AttendanceSummaryControllerTest {

    private static final Company COMPANY;

    static {
        COMPANY = new Company();
        COMPANY.setId(1L);
    }

    @Mock
    private AttendanceSummaryService attendanceSummaryService;
    @Mock
    private CurrentUserService currentUserService;

    private MockMvc mockMvc;
    private User user;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(3L);
        user.setUsername("sami");
        user.setEmployeeId(20L);

        mockMvc = MockMvcBuilders
                .standaloneSetup(new AttendanceSummaryController(attendanceSummaryService, currentUserService))
                .setMessageConverters(jacksonConverter())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, "token", List.of()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    /**
     * Production ObjectMapper has the JavaTimeModule with WRITE_DATES_AS_TIMESTAMPS disabled
     * (Spring Boot auto-config). Standalone MockMvc does not, so register it here to keep
     * wire-format assertions realistic (LocalDate -> "2026-08-05", LocalTime -> "08:02").
     */
    private static MappingJackson2HttpMessageConverter jacksonConverter() {
        ObjectMapper mapper = JsonMapper.builder()
                .addModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .build();
        return new MappingJackson2HttpMessageConverter(mapper);
    }

    private static AttendanceSummary summary() {
        AttendanceSummary s = new AttendanceSummary();
        s.setId(1001L);
        s.setCompany(COMPANY);
        Employee employee = new Employee();
        employee.setId(12L);
        employee.setCompany(COMPANY);
        employee.setFirstName("Ahmed");
        employee.setLastName("Ben Salah");
        s.setEmployee(employee);
        s.setWorkDate(LocalDate.of(2026, 8, 5));
        DayType dayType = new DayType();
        dayType.setCode("WORKDAY");
        dayType.setLabel("Workday");
        s.setDayType(dayType);
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
        AttendanceStatus status = new AttendanceStatus();
        status.setCode("PRESENT");
        status.setLabel("Present");
        s.setStatus(status);
        s.setComputedAt(LocalDateTime.of(2026, 8, 5, 17, 5));
        s.setRecomputeReason("event:2026-08-04");
        User u = new User();
        u.setId(3L);
        u.setFullName("Sami Trabelsi");
        s.setComputedBy(u);
        return s;
    }

    @Test
    void listReturnsEnvelopeWithSummaries() throws Exception {
        when(currentUserService.requireCompany(user)).thenReturn(COMPANY);
        when(attendanceSummaryService.listForEmployee(
                eq(COMPANY), eq(12L), any(), any())).thenReturn(List.of(summary()));

        mockMvc.perform(get("/attendance/summaries")
                        .param("employeeId", "12")
                        .param("from", "2026-08-01")
                        .param("to", "2026-08-05"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].id").value(1001))
                .andExpect(jsonPath("$.data[0].employeeName").value("Ahmed Ben Salah"))
                .andExpect(jsonPath("$.data[0].workDate").value("2026-08-05"))
                .andExpect(jsonPath("$.data[0].firstIn").value("08:02"))
                .andExpect(jsonPath("$.data[0].statusCode").value("PRESENT"))
                .andExpect(jsonPath("$.data[0].recomputeReason").value("event:2026-08-04"))
                .andExpect(jsonPath("$.data[0].computedBy.id").value(3));
    }

    @Test
    void listDefaultsRangeWhenNotProvided() throws Exception {
        when(currentUserService.requireCompany(user)).thenReturn(COMPANY);
        when(attendanceSummaryService.listForEmployee(
                eq(COMPANY), eq(12L), any(), any())).thenReturn(List.of());

        mockMvc.perform(get("/attendance/summaries").param("employeeId", "12"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    void getByIdReturnsSummary() throws Exception {
        when(currentUserService.requireCompany(user)).thenReturn(COMPANY);
        when(attendanceSummaryService.getById(COMPANY, 1001L)).thenReturn(summary());

        mockMvc.perform(get("/attendance/summaries/1001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.workDate").value("2026-08-05"))
                .andExpect(jsonPath("$.data.statusLabel").value("Present"));
    }

    @Test
    void getByIdNotFoundReturns404() throws Exception {
        when(currentUserService.requireCompany(user)).thenReturn(COMPANY);
        when(attendanceSummaryService.getById(COMPANY, 999L))
                .thenThrow(new ResourceNotFoundException("Attendance summary not found: 999"));

        mockMvc.perform(get("/attendance/summaries/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void dayReturnsSummary() throws Exception {
        when(currentUserService.requireCompany(user)).thenReturn(COMPANY);
        when(attendanceSummaryService.day(eq(COMPANY), eq(12L), any(), eq(user)))
                .thenReturn(summary());

        mockMvc.perform(get("/attendance/summaries/day")
                        .param("employeeId", "12")
                        .param("date", "2026-08-05"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.statusCode").value("PRESENT"));
    }

    @Test
    void todayReturnsOwnSummary() throws Exception {
        Employee employee = new Employee();
        employee.setId(12L);
        when(currentUserService.requireCompany(user)).thenReturn(COMPANY);
        when(currentUserService.requireEmployee(user)).thenReturn(employee);
        when(attendanceSummaryService.day(eq(COMPANY), eq(12L), any(), eq(user)))
                .thenReturn(summary());

        mockMvc.perform(get("/attendance/summaries/today"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.employeeId").value(12));
    }

    @Test
    void recomputeReturnsSummaries() throws Exception {
        when(currentUserService.requireCompany(user)).thenReturn(COMPANY);
        when(attendanceSummaryService.recomputeForEmployee(
                eq(COMPANY), eq(12L), any(), any(), eq(user), eq("correction")))
                .thenReturn(List.of(summary()));

        mockMvc.perform(post("/attendance/recompute")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"employeeId":12,"from":"2026-08-01","to":"2026-08-31",
                                 "reason":"correction"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Recalcul terminé"))
                .andExpect(jsonPath("$.data[0].statusCode").value("PRESENT"));
    }

    @Test
    void recomputeAllReturnsStats() throws Exception {
        when(currentUserService.requireCompany(user)).thenReturn(COMPANY);
        when(attendanceSummaryService.recomputeCompany(
                eq(COMPANY), any(), any(), eq(user), eq("fermeture")))
                .thenReturn(new RecomputeStats(1L, LocalDate.of(2026, 8, 1),
                        LocalDate.of(2026, 8, 31), 3, 93));

        mockMvc.perform(post("/attendance/recompute/all")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"from":"2026-08-01","to":"2026-08-31",
                                 "reason":"fermeture"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.employeeCount").value(3))
                .andExpect(jsonPath("$.data.dayCount").value(93));
    }

    @Test
    void recomputeMissingFieldsRejected() throws Exception {
        mockMvc.perform(post("/attendance/recompute")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }
}
