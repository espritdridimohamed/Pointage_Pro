package com.pointagepro.attendance.controller;

import com.pointagepro.attendance.dto.AttendanceEventResult;
import com.pointagepro.attendance.entity.AttendanceEvent;
import com.pointagepro.attendance.entity.EventType;
import com.pointagepro.attendance.service.AttendanceEventService;
import com.pointagepro.auth.entity.User;
import com.pointagepro.auth.service.CurrentUserService;
import com.pointagepro.company.entity.Company;
import com.pointagepro.employee.entity.Employee;
import com.pointagepro.shared.exception.DuplicateResourceException;
import com.pointagepro.shared.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AttendanceEventControllerTest {

    private static final Company COMPANY;

    static {
        COMPANY = new Company();
        COMPANY.setId(1L);
    }

    @Mock
    private AttendanceEventService attendanceEventService;
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
                .standaloneSetup(new AttendanceEventController(attendanceEventService, currentUserService))
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

    private static AttendanceEventResult stored(EventType type) {
        Employee employee = new Employee();
        employee.setId(12L);
        employee.setCompany(COMPANY);
        employee.setFirstName("Ahmed");
        employee.setLastName("Ben Salah");
        AttendanceEvent event = new AttendanceEvent();
        event.setId(551L);
        event.setCompany(COMPANY);
        event.setEmployee(employee);
        event.setEventType(type);
        event.setEventTime(LocalDateTime.of(2026, 8, 5, 8, 0));
        event.setSource("MANUAL");
        return AttendanceEventResult.stored(event, employee);
    }

    private static EventType type(String code) {
        EventType t = new EventType();
        t.setCode(code);
        return t;
    }

    @Test
    void recordReturnsCreatedWithComputedBy() throws Exception {
        when(currentUserService.requireCompany(user)).thenReturn(COMPANY);
        when(attendanceEventService.recordManual(
                eq(COMPANY), eq(12L), eq("IN"), any(), eq(user))).thenReturn(stored(type("IN")));

        mockMvc.perform(post("/attendance/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"employeeId":12,"eventType":"IN",
                                 "timestamp":"2026-08-05T08:00:00"}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.employeeId").value(12))
                .andExpect(jsonPath("$.data.eventType").value("IN"))
                .andExpect(jsonPath("$.data.source").value("MANUAL"))
                .andExpect(jsonPath("$.data.computedBy.id").value(3))
                .andExpect(jsonPath("$.data.computedBy.fullName").isEmpty());
    }

    @Test
    void recordDuplicateReturnsConflict() throws Exception {
        when(currentUserService.requireCompany(user)).thenReturn(COMPANY);
        when(attendanceEventService.recordManual(eq(COMPANY), eq(12L), eq("IN"), any(), eq(user)))
                .thenThrow(new DuplicateResourceException(
                        "Duplicate manual event for employee 12 within 60 seconds"));

        mockMvc.perform(post("/attendance/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"employeeId":12,"eventType":"IN",
                                 "timestamp":"2026-08-05T08:00:00"}"""))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void recordMissingFieldsRejectedByValidation() throws Exception {
        mockMvc.perform(post("/attendance/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"eventType\":\"IN\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void recordUnknownEventTypeRejected() throws Exception {
        mockMvc.perform(post("/attendance/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"employeeId":12,"eventType":"LUNCH",
                                 "timestamp":"2026-08-05T08:00:00"}"""))
                .andExpect(status().isBadRequest());
    }

    @Test
    void listReturnsEnvelopeWithEvents() throws Exception {
        when(currentUserService.requireCompany(user)).thenReturn(COMPANY);
        AttendanceEvent event = stored(type("IN")).event();
        when(attendanceEventService.listForEmployee(
                eq(COMPANY), eq(12L), any(), any())).thenReturn(List.of(event));

        mockMvc.perform(get("/attendance/events")
                        .param("employeeId", "12")
                        .param("from", "2026-08-01")
                        .param("to", "2026-08-05"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].id").value(551))
                .andExpect(jsonPath("$.data[0].employeeName").value("Ahmed Ben Salah"))
                .andExpect(jsonPath("$.data[0].eventType").value("IN"))
                .andExpect(jsonPath("$.data[0].source").value("MANUAL"));
    }

    @Test
    void listDefaultsRangeWhenNotProvided() throws Exception {
        when(currentUserService.requireCompany(user)).thenReturn(COMPANY);
        when(attendanceEventService.listForEmployee(
                eq(COMPANY), eq(12L), any(), any())).thenReturn(List.of());

        mockMvc.perform(get("/attendance/events").param("employeeId", "12"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data").isEmpty());
    }
}
