package com.pointagepro.attendance.controller;

import com.pointagepro.attendance.dto.AdjustmentResponse;
import com.pointagepro.shared.approval.dto.ApprovalStepResponse;
import com.pointagepro.attendance.entity.AdjustmentStatus;
import com.pointagepro.attendance.entity.AdjustmentType;
import com.pointagepro.attendance.entity.AttendanceAdjustment;
import com.pointagepro.attendance.service.AttendanceAdjustmentService;
import com.pointagepro.auth.entity.User;
import com.pointagepro.auth.service.CurrentUserService;
import com.pointagepro.company.entity.Company;
import com.pointagepro.employee.entity.Employee;
import com.pointagepro.shared.approval.entity.Approval;
import com.pointagepro.shared.approval.entity.ApprovalStatus;
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
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AttendanceAdjustmentControllerTest {

    private static final Company COMPANY;

    static {
        COMPANY = new Company();
        COMPANY.setId(1L);
    }

    @Mock
    private AttendanceAdjustmentService adjustmentService;
    @Mock
    private CurrentUserService currentUserService;

    private MockMvc mockMvc;
    private User user;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(3L);
        user.setUsername("hr");
        user.setEmployeeId(20L);

        mockMvc = MockMvcBuilders
                .standaloneSetup(new AttendanceAdjustmentController(adjustmentService, currentUserService))
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

    private static MappingJackson2HttpMessageConverter jacksonConverter() {
        ObjectMapper mapper = JsonMapper.builder()
                .addModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .build();
        return new MappingJackson2HttpMessageConverter(mapper);
    }

    private static AdjustmentResponse response(String statusCode) {
        AttendanceAdjustment a = new AttendanceAdjustment();
        a.setId(2001L);
        a.setCompany(COMPANY);
        Employee employee = new Employee();
        employee.setId(12L);
        employee.setFirstName("Ahmed");
        employee.setLastName("Ben Salah");
        a.setEmployee(employee);
        a.setWorkDate(LocalDate.of(2026, 8, 5));
        AdjustmentType type = new AdjustmentType();
        type.setCode("ADD_MINUTES");
        type.setLabel("Ajout minutes");
        a.setAdjustmentType(type);
        a.setMinutes(30);
        a.setReason("heure de pause oubliée");
        AdjustmentStatus status = new AdjustmentStatus();
        status.setCode(statusCode);
        status.setLabel(statusCode.equals("PENDING") ? "En attente" : statusCode);
        a.setStatus(status);
        a.setCreatedAt(LocalDateTime.of(2026, 8, 5, 9, 0));
        User creator = new User();
        creator.setId(3L);
        creator.setFullName("Sami Trabelsi");
        a.setCreatedBy(creator);
        if (statusCode.equals("APPLIED")) {
            User approver = new User();
            approver.setId(5L);
            approver.setFullName("Nadia Bouzid");
            a.setApprovedBy(approver);
            a.setApprovedAt(LocalDateTime.of(2026, 8, 5, 9, 30));
        }
        ApprovalStatus approvalPending = new ApprovalStatus();
        approvalPending.setCode("PENDING");
        Approval step = new Approval();
        step.setId(9001L);
        step.setStepOrder(1);
        step.setApproverRole("HR");
        step.setStatus(approvalPending);
        step.setApprover(null);
        step.setDecidedAt(null);
        return AdjustmentResponse.from(a, List.of(ApprovalStepResponse.from(step)));
    }

    @Test
    void createReturns201WithAdjustment() throws Exception {
        when(currentUserService.requireCompany(user)).thenReturn(COMPANY);
        when(adjustmentService.create(eq(COMPANY), eq(user), eq(12L),
                eq(LocalDate.of(2026, 8, 5)), eq("ADD_MINUTES"), eq(30), eq("heure de pause oubliée")))
                .thenReturn(response("PENDING"));

        mockMvc.perform(post("/attendance/adjustments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"employeeId":12,"workDate":"2026-08-05","adjustmentType":"ADD_MINUTES",
                                 "minutes":30,"reason":"heure de pause oubliée"}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Demande d'ajustement créée"))
                .andExpect(jsonPath("$.data.id").value(2001))
                .andExpect(jsonPath("$.data.workDate").value("2026-08-05"))
                .andExpect(jsonPath("$.data.statusCode").value("PENDING"))
                .andExpect(jsonPath("$.data.approvals[0].approverRole").value("HR"));
    }

    @Test
    void createMissingFieldsRejected() throws Exception {
        mockMvc.perform(post("/attendance/adjustments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void listReturnsAdjustments() throws Exception {
        when(currentUserService.requireCompany(user)).thenReturn(COMPANY);
        when(adjustmentService.list(eq(COMPANY), eq(12L), eq("PENDING"),
                eq(LocalDate.of(2026, 8, 1)), eq(LocalDate.of(2026, 8, 31))))
                .thenReturn(List.of(response("PENDING")));

        mockMvc.perform(get("/attendance/adjustments")
                        .param("employeeId", "12")
                        .param("status", "PENDING")
                        .param("from", "2026-08-01")
                        .param("to", "2026-08-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].employeeName").value("Ahmed Ben Salah"))
                .andExpect(jsonPath("$.data[0].adjustmentTypeCode").value("ADD_MINUTES"))
                .andExpect(jsonPath("$.data[0].minutes").value(30));
    }

    @Test
    void pendingReturnsQueue() throws Exception {
        when(currentUserService.requireCompany(user)).thenReturn(COMPANY);
        when(adjustmentService.pendingQueue(COMPANY, user)).thenReturn(List.of(response("PENDING")));

        mockMvc.perform(get("/attendance/adjustments/pending"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].statusCode").value("PENDING"));
    }

    @Test
    void getByIdReturnsAdjustment() throws Exception {
        when(currentUserService.requireCompany(user)).thenReturn(COMPANY);
        when(adjustmentService.get(COMPANY, 2001L)).thenReturn(response("PENDING"));

        mockMvc.perform(get("/attendance/adjustments/2001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(2001))
                .andExpect(jsonPath("$.data.reason").value("heure de pause oubliée"));
    }

    @Test
    void getByIdNotFoundReturns404() throws Exception {
        when(currentUserService.requireCompany(user)).thenReturn(COMPANY);
        when(adjustmentService.get(COMPANY, 999L))
                .thenThrow(new ResourceNotFoundException("Attendance adjustment not found: 999"));

        mockMvc.perform(get("/attendance/adjustments/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void approveReturnsAppliedAdjustment() throws Exception {
        when(currentUserService.requireCompany(user)).thenReturn(COMPANY);
        when(adjustmentService.approve(COMPANY, user, 2001L, "ok pour moi"))
                .thenReturn(response("APPLIED"));

        mockMvc.perform(post("/attendance/adjustments/2001/approve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"comment\":\"ok pour moi\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Ajustement approuvé"))
                .andExpect(jsonPath("$.data.statusCode").value("APPLIED"));
    }

    @Test
    void approveWithoutBodyAllowed() throws Exception {
        when(currentUserService.requireCompany(user)).thenReturn(COMPANY);
        when(adjustmentService.approve(COMPANY, user, 2001L, null))
                .thenReturn(response("APPLIED"));

        mockMvc.perform(post("/attendance/adjustments/2001/approve"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.statusCode").value("APPLIED"));
    }

    @Test
    void rejectReturnsRejectedAdjustment() throws Exception {
        when(currentUserService.requireCompany(user)).thenReturn(COMPANY);
        when(adjustmentService.reject(COMPANY, user, 2001L, "pièce manquante"))
                .thenReturn(response("REJECTED"));

        mockMvc.perform(post("/attendance/adjustments/2001/reject")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"comment\":\"pièce manquante\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Ajustement rejeté"))
                .andExpect(jsonPath("$.data.statusCode").value("REJECTED"));
    }

    @Test
    void cancelReturnsCancelledAdjustment() throws Exception {
        when(currentUserService.requireCompany(user)).thenReturn(COMPANY);
        when(adjustmentService.cancel(COMPANY, user, 2001L, "saisie en double"))
                .thenReturn(response("CANCELLED"));

        mockMvc.perform(post("/attendance/adjustments/2001/cancel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"saisie en double\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Ajustement annulé"))
                .andExpect(jsonPath("$.data.statusCode").value("CANCELLED"));
    }

    @Test
    void cancelWithoutReasonRejected() throws Exception {
        mockMvc.perform(post("/attendance/adjustments/2001/cancel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }
}
