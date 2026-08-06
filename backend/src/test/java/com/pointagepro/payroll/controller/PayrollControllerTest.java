package com.pointagepro.payroll.controller;

import com.pointagepro.auth.entity.User;
import com.pointagepro.auth.service.CurrentUserService;
import com.pointagepro.company.entity.Company;
import com.pointagepro.employee.entity.Employee;
import com.pointagepro.payroll.dto.PayrollItemResponse;
import com.pointagepro.payroll.dto.PayrollRunResponse;
import com.pointagepro.payroll.dto.PayslipResponse;
import com.pointagepro.payroll.entity.Payroll;
import com.pointagepro.payroll.entity.PayrollItem;
import com.pointagepro.payroll.entity.PayrollStatus;
import com.pointagepro.payroll.entity.Payslip;
import com.pointagepro.payroll.service.PayrollService;
import com.pointagepro.shared.exception.GlobalExceptionHandler;
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
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class PayrollControllerTest {

    private static final Company COMPANY;

    static {
        COMPANY = new Company();
        COMPANY.setId(1L);
    }

    @Mock
    private PayrollService payrollService;
    @Mock
    private CurrentUserService currentUserService;

    private MockMvc mockMvc;
    private User user;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(3L);
        user.setUsername("rh");
        user.setEmployeeId(20L);

        mockMvc = MockMvcBuilders
                .standaloneSetup(new PayrollController(payrollService, currentUserService),
                        new PayslipController(payrollService, currentUserService))
                .setMessageConverters(jacksonConverter())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, "token", List.of()));
        lenient().when(currentUserService.requireCompany(any(User.class))).thenReturn(COMPANY);
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

    private static Payroll payroll(String statusCode) {
        Payroll p = new Payroll();
        p.setId(100L);
        p.setCompany(COMPANY);
        p.setPeriodYear(2026);
        p.setPeriodMonth(7);
        PayrollStatus s = new PayrollStatus();
        s.setCode(statusCode);
        s.setLabel(statusCode);
        p.setStatus(s);
        p.setTotalGross(new BigDecimal("1500.00"));
        p.setTotalCnss(new BigDecimal("145.20"));
        p.setTotalIrpp(new BigDecimal("139.60"));
        p.setTotalCss(new BigDecimal("7.50"));
        p.setTotalDeductions(new BigDecimal("0.00"));
        p.setTotalNet(new BigDecimal("1207.71"));
        p.setEmployeeCount(1);
        User creator = new User();
        creator.setId(3L);
        p.setCreatedBy(creator);
        return p;
    }

    private static Employee employee() {
        Employee e = new Employee();
        e.setId(10L);
        e.setMatricule("E-001");
        e.setFirstName("Ahmed");
        e.setLastName("Ben Salah");
        return e;
    }

    private static PayrollItem item() {
        PayrollItem i = new PayrollItem();
        i.setId(2001L);
        i.setPayroll(payroll("COMPUTED"));
        i.setEmployee(employee());
        i.setBaseSalary(new BigDecimal("1500.00"));
        i.setWorkDays(21);
        i.setWorkHours(new BigDecimal("168.00"));
        i.setGrossSalary(new BigDecimal("1500.00"));
        i.setNetSalary(new BigDecimal("1207.71"));
        return i;
    }

    @Test
    void create_returns201() throws Exception {
        when(payrollService.create(eq(COMPANY), eq(user), eq(2026), eq(7), any()))
                .thenReturn(PayrollRunResponse.from(payroll("DRAFT"), List.of()));

        mockMvc.perform(post("/payrolls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"periodYear\":2026,\"periodMonth\":7,\"notes\":\"juillet\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Paie créée"))
                .andExpect(jsonPath("$.data.status.code").value("DRAFT"))
                .andExpect(jsonPath("$.data.totals.net").value(1207.71));
    }

    @Test
    void create_missingPeriod_returns400() throws Exception {
        mockMvc.perform(post("/payrolls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"periodMonth\":7}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void compute_returns200() throws Exception {
        when(payrollService.compute(eq(COMPANY.getId()), eq(100L), eq(user)))
                .thenReturn(PayrollRunResponse.from(payroll("COMPUTED"), List.of()));

        mockMvc.perform(post("/payrolls/100/compute"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Paie calculée"))
                .andExpect(jsonPath("$.data.status.code").value("COMPUTED"));
    }

    @Test
    void validate_returns200() throws Exception {
        when(payrollService.validate(eq(COMPANY.getId()), eq(100L), eq(user), any()))
                .thenReturn(PayrollRunResponse.from(payroll("VALIDATED"), List.of()));

        mockMvc.perform(post("/payrolls/100/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"notes\":\"ok\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Paie validée"))
                .andExpect(jsonPath("$.data.status.code").value("VALIDATED"));
    }

    @Test
    void approve_returns200() throws Exception {
        when(payrollService.approve(eq(COMPANY.getId()), eq(100L), eq(user)))
                .thenReturn(PayrollRunResponse.from(payroll("APPROVED"), List.of()));

        mockMvc.perform(post("/payrolls/100/approve"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Paie approuvée"))
                .andExpect(jsonPath("$.data.status.code").value("APPROVED"));
    }

    @Test
    void pay_returns200() throws Exception {
        when(payrollService.pay(eq(COMPANY.getId()), eq(100L), eq(user), any()))
                .thenReturn(PayrollRunResponse.from(payroll("PAID"), List.of()));

        mockMvc.perform(post("/payrolls/100/pay")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bankTransferRef\":\"VIR-123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Paie marquée payée"))
                .andExpect(jsonPath("$.data.status.code").value("PAID"));
    }

    @Test
    void cancel_returns200() throws Exception {
        when(payrollService.cancel(eq(COMPANY.getId()), eq(100L), eq(user), any()))
                .thenReturn(PayrollRunResponse.from(payroll("CANCELLED"), List.of()));

        mockMvc.perform(post("/payrolls/100/cancel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Paie annulée"))
                .andExpect(jsonPath("$.data.status.code").value("CANCELLED"));
    }

    @Test
    void list_returns200() throws Exception {
        when(payrollService.list(eq(COMPANY.getId()), eq(2026), eq(7), eq("COMPUTED")))
                .thenReturn(List.of(PayrollRunResponse.from(payroll("COMPUTED"), List.of())));

        mockMvc.perform(get("/payrolls").param("year", "2026")
                        .param("month", "7").param("status", "COMPUTED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].status.code").value("COMPUTED"));
    }

    @Test
    void getById_returns200() throws Exception {
        when(payrollService.get(eq(COMPANY.getId()), eq(100L)))
                .thenReturn(PayrollRunResponse.from(payroll("VALIDATED"), List.of()));

        mockMvc.perform(get("/payrolls/100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status.code").value("VALIDATED"));
    }

    @Test
    void items_returns200() throws Exception {
        when(payrollService.items(eq(COMPANY.getId()), eq(100L)))
                .thenReturn(List.of(PayrollItemResponse.from(item(), List.of())));

        mockMvc.perform(get("/payrolls/100/items"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Lignes de paie"))
                .andExpect(jsonPath("$.data[0].workDays").value(21))
                .andExpect(jsonPath("$.data[0].employee.firstName").value("Ahmed"));
    }

    @Test
    void payslips_returns200() throws Exception {
        Payslip p = new Payslip();
        p.setId(3001L);
        p.setPayrollItem(item());
        p.setPayslipNumber("PP-202607-001");
        p.setIssuedAt(LocalDateTime.of(2026, 8, 1, 10, 0));
        when(payrollService.payslips(eq(COMPANY.getId()), eq(100L)))
                .thenReturn(List.of(PayslipResponse.from(p, List.of())));

        mockMvc.perform(get("/payrolls/100/payslips"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].payslipNumber").value("PP-202607-001"));
    }

    @Test
    void payslipDetail_returns200() throws Exception {
        Payslip p = new Payslip();
        p.setId(3001L);
        p.setPayrollItem(item());
        p.setPayslipNumber("PP-202607-001");
        when(payrollService.getPayslip(eq(COMPANY.getId()), eq(3001L)))
                .thenReturn(PayslipResponse.from(p, List.of()));

        mockMvc.perform(get("/payslips/3001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Bulletin de paie"))
                .andExpect(jsonPath("$.data.payslipNumber").value("PP-202607-001"));
    }
}
