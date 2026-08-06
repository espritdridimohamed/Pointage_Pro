package com.pointagepro.leave.controller;

import com.pointagepro.auth.entity.User;
import com.pointagepro.auth.service.CurrentUserService;
import com.pointagepro.company.entity.Company;
import com.pointagepro.employee.entity.Employee;
import com.pointagepro.leave.dto.LeaveBalanceResponse;
import com.pointagepro.leave.dto.LeaveResponse;
import com.pointagepro.leave.entity.LeaveBalance;
import com.pointagepro.leave.entity.LeaveRequest;
import com.pointagepro.leave.entity.LeaveRequestStatus;
import com.pointagepro.leave.entity.LeaveType;
import com.pointagepro.leave.service.LeaveService;
import com.pointagepro.shared.approval.dto.ApprovalStepResponse;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class LeaveControllerTest {

    private static final Company COMPANY;

    static {
        COMPANY = new Company();
        COMPANY.setId(1L);
    }

    @Mock
    private LeaveService leaveService;
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
                .standaloneSetup(new LeaveController(leaveService, currentUserService))
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

    private static LeaveResponse response(String statusCode) {
        LeaveRequest r = new LeaveRequest();
        r.setId(2001L);
        Employee employee = new Employee();
        employee.setId(12L);
        employee.setFirstName("Ahmed");
        employee.setLastName("Ben Salah");
        r.setEmployee(employee);
        LeaveType type = new LeaveType();
        type.setCode("ANNUAL");
        type.setName("Annual leave");
        r.setLeaveType(type);
        r.setStartDate(LocalDate.of(2026, 8, 10));
        r.setEndDate(LocalDate.of(2026, 8, 14));
        r.setDaysRequested(new BigDecimal("5"));
        r.setReason("vacances");
        LeaveRequestStatus status = new LeaveRequestStatus();
        status.setCode(statusCode);
        status.setLabel(statusCode.equals("PENDING") ? "En attente" : statusCode);
        r.setStatus(status);
        r.setCreatedAt(LocalDateTime.of(2026, 8, 5, 9, 0));
        User creator = new User();
        creator.setId(3L);
        creator.setFullName("Sami Trabelsi");
        r.setCreatedBy(creator);
        if (statusCode.equals("APPROVED")) {
            User approver = new User();
            approver.setId(5L);
            approver.setFullName("Nadia Bouzid");
            r.setApprovedBy(approver);
            r.setApprovedAt(LocalDateTime.of(2026, 8, 5, 9, 30));
        }
        ApprovalStatus approvalPending = new ApprovalStatus();
        approvalPending.setCode("PENDING");
        Approval step = new Approval();
        step.setId(9001L);
        step.setStepOrder(1);
        step.setApproverRole("MANAGER");
        step.setStatus(approvalPending);
        return LeaveResponse.from(r, List.of(ApprovalStepResponse.from(step)));
    }

    private static LeaveBalanceResponse balanceResponse() {
        LeaveBalance balance = new LeaveBalance();
        Employee employee = new Employee();
        employee.setId(12L);
        balance.setEmployee(employee);
        LeaveType type = new LeaveType();
        type.setCode("ANNUAL");
        type.setName("Annual leave");
        balance.setLeaveType(type);
        balance.setYear(2026);
        balance.setEntitlementDays(new BigDecimal("18"));
        balance.setTakenDays(new BigDecimal("3"));
        return LeaveBalanceResponse.from(balance);
    }

    @Test
    void createReturns201WithLeave() throws Exception {
        when(currentUserService.requireCompany(user)).thenReturn(COMPANY);
        when(leaveService.create(eq(COMPANY), eq(user), eq(12L),
                eq("ANNUAL"), eq(LocalDate.of(2026, 8, 10)), eq(LocalDate.of(2026, 8, 14)),
                eq("vacances"), eq(null)))
                .thenReturn(response("PENDING"));

        mockMvc.perform(post("/leaves")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"employeeId":12,"leaveTypeCode":"ANNUAL","startDate":"2026-08-10",
                                 "endDate":"2026-08-14","reason":"vacances"}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Demande de congé créée"))
                .andExpect(jsonPath("$.data.id").value(2001))
                .andExpect(jsonPath("$.data.statusCode").value("PENDING"))
                .andExpect(jsonPath("$.data.daysRequested").value(5))
                .andExpect(jsonPath("$.data.approvals[0].approverRole").value("MANAGER"));
    }

    @Test
    void createMissingFieldsRejected() throws Exception {
        mockMvc.perform(post("/leaves")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void listReturnsLeaves() throws Exception {
        when(currentUserService.requireCompany(user)).thenReturn(COMPANY);
        when(leaveService.list(eq(COMPANY), eq(12L), eq("PENDING"),
                eq(LocalDate.of(2026, 8, 1)), eq(LocalDate.of(2026, 8, 31))))
                .thenReturn(List.of(response("PENDING")));

        mockMvc.perform(get("/leaves")
                        .param("employeeId", "12")
                        .param("status", "PENDING")
                        .param("from", "2026-08-01")
                        .param("to", "2026-08-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].employeeName").value("Ahmed Ben Salah"))
                .andExpect(jsonPath("$.data[0].leaveTypeCode").value("ANNUAL"));
    }

    @Test
    void pendingReturnsQueue() throws Exception {
        when(currentUserService.requireCompany(user)).thenReturn(COMPANY);
        when(leaveService.pendingQueue(COMPANY, user)).thenReturn(List.of(response("PENDING")));

        mockMvc.perform(get("/leaves/pending"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].statusCode").value("PENDING"));
    }

    @Test
    void getByIdReturnsLeave() throws Exception {
        when(currentUserService.requireCompany(user)).thenReturn(COMPANY);
        when(leaveService.get(COMPANY, 2001L)).thenReturn(response("PENDING"));

        mockMvc.perform(get("/leaves/2001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(2001))
                .andExpect(jsonPath("$.data.reason").value("vacances"));
    }

    @Test
    void getByIdNotFoundReturns404() throws Exception {
        when(currentUserService.requireCompany(user)).thenReturn(COMPANY);
        when(leaveService.get(COMPANY, 999L))
                .thenThrow(new ResourceNotFoundException("Leave request not found: 999"));

        mockMvc.perform(get("/leaves/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void approveReturnsApprovedLeave() throws Exception {
        when(currentUserService.requireCompany(user)).thenReturn(COMPANY);
        when(leaveService.approve(COMPANY, user, 2001L, "ok pour moi"))
                .thenReturn(response("APPROVED"));

        mockMvc.perform(post("/leaves/2001/approve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"comment\":\"ok pour moi\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Congé approuvé"))
                .andExpect(jsonPath("$.data.statusCode").value("APPROVED"));
    }

    @Test
    void approveWithoutBodyAllowed() throws Exception {
        when(currentUserService.requireCompany(user)).thenReturn(COMPANY);
        when(leaveService.approve(COMPANY, user, 2001L, null))
                .thenReturn(response("APPROVED"));

        mockMvc.perform(post("/leaves/2001/approve"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.statusCode").value("APPROVED"));
    }

    @Test
    void rejectReturnsRejectedLeave() throws Exception {
        when(currentUserService.requireCompany(user)).thenReturn(COMPANY);
        when(leaveService.reject(COMPANY, user, 2001L, "planning chargé"))
                .thenReturn(response("REJECTED"));

        mockMvc.perform(post("/leaves/2001/reject")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"comment\":\"planning chargé\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Congé rejeté"))
                .andExpect(jsonPath("$.data.statusCode").value("REJECTED"));
    }

    @Test
    void cancelReturnsCancelledLeave() throws Exception {
        when(currentUserService.requireCompany(user)).thenReturn(COMPANY);
        when(leaveService.cancel(COMPANY, user, 2001L, "saisie en double"))
                .thenReturn(response("CANCELLED"));

        mockMvc.perform(post("/leaves/2001/cancel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"saisie en double\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Congé annulé"))
                .andExpect(jsonPath("$.data.statusCode").value("CANCELLED"));
    }

    @Test
    void cancelWithoutReasonRejected() throws Exception {
        mockMvc.perform(post("/leaves/2001/cancel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void balanceReturnsBalances() throws Exception {
        when(currentUserService.requireCompany(user)).thenReturn(COMPANY);
        when(leaveService.balance(COMPANY, user, 12L, 2026))
                .thenReturn(List.of(balanceResponse()));

        mockMvc.perform(get("/leaves/balance/12")
                        .param("year", "2026"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].leaveTypeCode").value("ANNUAL"))
                .andExpect(jsonPath("$.data[0].availableDays").value(15));
    }
}
