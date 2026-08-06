package com.pointagepro.payroll.service;

import com.pointagepro.attendance.entity.AttendanceStatus;
import com.pointagepro.attendance.entity.AttendanceSummary;
import com.pointagepro.attendance.entity.DayType;
import com.pointagepro.attendance.repository.AttendanceSummaryRepository;
import com.pointagepro.attendance.repository.WorkScheduleLineRepository;
import com.pointagepro.attendance.service.AttendanceEngineService;
import com.pointagepro.audit.service.AuditService;
import com.pointagepro.auth.entity.User;
import com.pointagepro.auth.repository.UserRepository;
import com.pointagepro.company.entity.Company;
import com.pointagepro.company.entity.CompanySettings;
import com.pointagepro.company.repository.CompanySettingsRepository;
import com.pointagepro.contract.entity.ContractStatus;
import com.pointagepro.contract.entity.EmployeeContract;
import com.pointagepro.contract.entity.SalaryComponent;
import com.pointagepro.contract.entity.SalaryComponentType;
import com.pointagepro.contract.repository.EmployeeContractRepository;
import com.pointagepro.contract.repository.SalaryComponentRepository;
import com.pointagepro.contract.repository.SalaryComponentTypeRepository;
import com.pointagepro.employee.entity.Employee;
import com.pointagepro.employee.repository.EmployeeRepository;
import com.pointagepro.employee.repository.EmployeeTaxProfileRepository;
import com.pointagepro.leave.repository.LeaveRequestRepository;
import com.pointagepro.legal.entity.CnssRate;
import com.pointagepro.legal.entity.CssRate;
import com.pointagepro.legal.entity.FamilyAllowance;
import com.pointagepro.legal.entity.SmigValue;
import com.pointagepro.legal.entity.TaxBracket;
import com.pointagepro.legal.repository.CnssRateRepository;
import com.pointagepro.legal.repository.CssRateRepository;
import com.pointagepro.legal.repository.FamilyAllowanceRepository;
import com.pointagepro.legal.repository.SmigValueRepository;
import com.pointagepro.legal.repository.TaxBracketRepository;
import com.pointagepro.payroll.dto.PayrollRunResponse;
import com.pointagepro.payroll.entity.Payroll;
import com.pointagepro.payroll.entity.PayrollItem;
import com.pointagepro.payroll.entity.PayrollStatus;
import com.pointagepro.payroll.entity.Payslip;
import com.pointagepro.payroll.repository.PayrollAttendanceSnapshotRepository;
import com.pointagepro.payroll.repository.PayrollItemComponentRepository;
import com.pointagepro.payroll.repository.PayrollItemRepository;
import com.pointagepro.payroll.repository.PayrollRepository;
import com.pointagepro.payroll.repository.PayrollStatusRepository;
import com.pointagepro.payroll.repository.PayslipRepository;
import com.pointagepro.shared.exception.ConflictException;
import com.pointagepro.shared.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PayrollServiceTest {

    private static final long COMPANY_ID = 1L;
    private static final long PAYROLL_ID = 100L;
    private static final long EMPLOYEE_ID = 10L;
    private static final long CONTRACT_ID = 5L;
    private static final int YEAR = 2026;
    private static final int MONTH = 7;

    @Mock private PayrollRepository payrollRepository;
    @Mock private PayrollStatusRepository statusRepository;
    @Mock private PayrollItemRepository itemRepository;
    @Mock private PayrollItemComponentRepository componentRepository;
    @Mock private PayslipRepository payslipRepository;
    @Mock private PayrollAttendanceSnapshotRepository snapshotRepository;
    @Mock private EmployeeRepository employeeRepository;
    @Mock private EmployeeContractRepository contractRepository;
    @Mock private SalaryComponentRepository salaryComponentRepository;
    @Mock private SalaryComponentTypeRepository componentTypeRepository;
    @Mock private EmployeeTaxProfileRepository taxProfileRepository;
    @Mock private CompanySettingsRepository settingsRepository;
    @Mock private AttendanceSummaryRepository summaryRepository;
    @Mock private WorkScheduleLineRepository workScheduleLineRepository;
    @Mock private LeaveRequestRepository leaveRequestRepository;
    @Mock private TaxBracketRepository taxBracketRepository;
    @Mock private CnssRateRepository cnssRateRepository;
    @Mock private CssRateRepository cssRateRepository;
    @Mock private SmigValueRepository smigValueRepository;
    @Mock private FamilyAllowanceRepository familyAllowanceRepository;
    @Mock private UserRepository userRepository;
    @Mock private AttendanceEngineService engineService;
    @Mock private AuditService auditService;

    @InjectMocks
    private PayrollService service;

    private Company company;
    private User actor;

    @BeforeEach
    void setUp() {
        company = new Company();
        company.setId(COMPANY_ID);
        actor = new User();
        actor.setId(3L);
        seedStatus("DRAFT", "DRAFT");
        seedStatus("COMPUTED", "COMPUTED");
        seedStatus("VALIDATED", "VALIDATED");
        seedStatus("APPROVED", "APPROVED");
        seedStatus("PAID", "PAID");
        seedStatus("CANCELLED", "CANCELLED");
    }

    private void seedStatus(String code, String label) {
        PayrollStatus s = new PayrollStatus();
        s.setCode(code);
        s.setLabel(label);
        lenient().when(statusRepository.findByCode(code)).thenReturn(Optional.of(s));
    }

    // ------------------------------------------------------------------ create

    @Test
    void create_newRun_returnsDraft() {
        when(payrollRepository.findByCompanyIdAndPeriodYearAndPeriodMonth(COMPANY_ID, YEAR, MONTH))
                .thenReturn(Optional.empty());
        when(payrollRepository.save(any(Payroll.class))).thenAnswer(inv -> inv.getArgument(0));
        when(userRepository.getReferenceById(actor.getId())).thenReturn(actor);

        PayrollRunResponse response = service.create(company, actor, YEAR, MONTH, "juillet");

        assertThat(response.getStatus().getCode()).isEqualTo("DRAFT");
        assertThat(response.getPeriodYear()).isEqualTo(YEAR);
        assertThat(response.getPeriodMonth()).isEqualTo(MONTH);
        assertThat(response.getNotes()).isEqualTo("juillet");
        assertThat(response.getCreatedBy()).isEqualTo(actor.getId());
        verify(auditService).log(eq("CREATE"), eq(COMPANY_ID), eq(actor.getId()),
                eq("PAYROLL"), any(), eq(null), anyString());
    }

    @Test
    void create_existingDraft_isIdempotent() {
        Payroll existing = payroll("DRAFT", 0);
        when(payrollRepository.findByCompanyIdAndPeriodYearAndPeriodMonth(COMPANY_ID, YEAR, MONTH))
                .thenReturn(Optional.of(existing));

        PayrollRunResponse response = service.create(company, actor, YEAR, MONTH, "juillet");

        assertThat(response.getStatus().getCode()).isEqualTo("DRAFT");
        verify(payrollRepository, never()).save(any(Payroll.class));
    }

    @Test
    void create_existingComputed_conflicts() {
        Payroll existing = payroll("COMPUTED", 1);
        when(payrollRepository.findByCompanyIdAndPeriodYearAndPeriodMonth(COMPANY_ID, YEAR, MONTH))
                .thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.create(company, actor, YEAR, MONTH, null))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void create_existingCancelled_reopensDraft() {
        Payroll cancelled = payroll("CANCELLED", 2);
        when(payrollRepository.findByCompanyIdAndPeriodYearAndPeriodMonth(COMPANY_ID, YEAR, MONTH))
                .thenReturn(Optional.of(cancelled));
        when(payrollRepository.save(any(Payroll.class))).thenAnswer(inv -> inv.getArgument(0));

        PayrollRunResponse response = service.create(company, actor, YEAR, MONTH, "reouverture");

        assertThat(response.getStatus().getCode()).isEqualTo("DRAFT");
        assertThat(cancelled.getTotalNet()).isEqualByComparingTo("0.00");
        assertThat(cancelled.getEmployeeCount()).isZero();
        verify(snapshotRepository).deleteByPayrollId(PAYROLL_ID);
        verify(itemRepository).deleteByPayrollId(PAYROLL_ID);
        verify(payslipRepository).deleteByPayrollItemPayrollId(PAYROLL_ID);
    }

    @Test
    void create_invalidPeriod_throws() {
        assertThatThrownBy(() -> service.create(company, actor, YEAR, 13, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ------------------------------------------------------------------ compute

    @Test
    void compute_draftWithEligibleEmployee_freezesAndCalculates() {
        Payroll payroll = payroll("DRAFT", 0);
        payroll.setCreatedBy(actor);
        when(payrollRepository.findWithDetailsById(PAYROLL_ID)).thenReturn(Optional.of(payroll));

        Employee employee = employee();
        when(employeeRepository.findByCompanyIdOrderByLastNameAsc(COMPANY_ID))
                .thenReturn(List.of(employee));
        when(contractRepository.findByEmployeeIdOrderByStartDateDesc(EMPLOYEE_ID))
                .thenReturn(List.of(activeContract()));
        when(salaryComponentRepository.findByContractIdAndIsActiveTrueOrderByStartDateDesc(CONTRACT_ID))
                .thenReturn(List.of(baseComponent()));
        when(taxProfileRepository.findByEmployeeIdOrderByValidFromDesc(EMPLOYEE_ID))
                .thenReturn(List.of());
        when(settingsRepository.findByCompanyId(COMPANY_ID)).thenReturn(Optional.of(settings()));
        when(cnssRateRepository.findByYear(YEAR)).thenReturn(Optional.of(cnss()));
        when(cssRateRepository.findByYear(YEAR)).thenReturn(Optional.of(css()));
        when(taxBracketRepository.findByYearOrderByBracketOrderAsc(YEAR)).thenReturn(brackets());
        when(smigValueRepository.findByYear(YEAR)).thenReturn(Optional.of(smig()));
        when(familyAllowanceRepository.findByYear(YEAR)).thenReturn(Optional.of(familyAllowance()));

        LocalDate from = LocalDate.of(YEAR, MONTH, 1);
        LocalDate to = LocalDate.of(YEAR, MONTH, 31);
        List<AttendanceSummary> summaries = summaries(from, to);
        when(summaryRepository.findByEmployeeIdAndWorkDateBetweenOrderByWorkDateAsc(EMPLOYEE_ID, from, to))
                .thenReturn(summaries);
        when(leaveRequestRepository
                .findByEmployeeIdAndStatus_CodeAndStartDateLessThanEqualAndEndDateGreaterThanEqualOrderByStartDateAsc(
                        EMPLOYEE_ID, "APPROVED", to, from)).thenReturn(List.of());
        when(itemRepository.save(any(PayrollItem.class))).thenAnswer(inv -> inv.getArgument(0));
        when(componentTypeRepository.findByCode("BASE_SALARY"))
                .thenReturn(Optional.of(componentType("BASE_SALARY")));
        when(componentRepository.saveAll(anyList())).thenReturn(List.of());
        when(payrollRepository.save(any(Payroll.class))).thenAnswer(inv -> inv.getArgument(0));

        PayrollRunResponse response = service.compute(COMPANY_ID, PAYROLL_ID, actor);

        assertThat(response.getStatus().getCode()).isEqualTo("COMPUTED");
        assertThat(response.getEmployeeCount()).isEqualTo(1);
        assertThat(response.getTotals().getGross()).isEqualByComparingTo("1500.00");
        assertThat(response.getTotals().getCnss()).isEqualByComparingTo("145.20");
        assertThat(response.getTotals().getIrpp()).isEqualByComparingTo("139.60");
        assertThat(response.getTotals().getCss()).isEqualByComparingTo("7.50");
        assertThat(response.getTotals().getNet()).isEqualByComparingTo("1207.71");
        assertThat(response.getWarnings()).anyMatch(w -> w.contains("Allocations familiales"));
        verify(snapshotRepository).saveAll(anyList());
        verify(auditService).log(eq("PAYROLL_RUN"), eq(COMPANY_ID), eq(actor.getId()),
                eq("PAYROLL"), eq(PAYROLL_ID), anyString(), anyString());
    }

    @Test
    void compute_futurePeriod_conflicts() {
        Payroll payroll = payroll("DRAFT", 0);
        payroll.setPeriodYear(2026);
        payroll.setPeriodMonth(12);
        when(payrollRepository.findWithDetailsById(PAYROLL_ID)).thenReturn(Optional.of(payroll));

        assertThatThrownBy(() -> service.compute(COMPANY_ID, PAYROLL_ID, actor))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void compute_frozenStatus_conflicts() {
        Payroll payroll = payroll("VALIDATED", 1);
        when(payrollRepository.findWithDetailsById(PAYROLL_ID)).thenReturn(Optional.of(payroll));

        assertThatThrownBy(() -> service.compute(COMPANY_ID, PAYROLL_ID, actor))
                .isInstanceOf(ConflictException.class);
    }

    // ------------------------------------------------------ validate/approve/pay/cancel

    @Test
    void validate_computed_goesValidated() {
        Payroll payroll = payroll("COMPUTED", 1);
        when(payrollRepository.findWithDetailsById(PAYROLL_ID)).thenReturn(Optional.of(payroll));

        PayrollRunResponse response = service.validate(COMPANY_ID, PAYROLL_ID, actor, "ok");

        assertThat(response.getStatus().getCode()).isEqualTo("VALIDATED");
        assertThat(response.getNotes()).contains("ok");
        verify(auditService).log(eq("STATUS_CHANGE"), eq(COMPANY_ID), eq(actor.getId()),
                eq("PAYROLL"), eq(PAYROLL_ID), eq("{\"status\":\"COMPUTED\"}"),
                eq("{\"status\":\"VALIDATED\"}"));
    }

    @Test
    void validate_wrongStatus_conflicts() {
        Payroll payroll = payroll("DRAFT", 0);
        when(payrollRepository.findWithDetailsById(PAYROLL_ID)).thenReturn(Optional.of(payroll));

        assertThatThrownBy(() -> service.validate(COMPANY_ID, PAYROLL_ID, actor, null))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void approve_validated_createsPayslips() {
        Payroll payroll = payroll("VALIDATED", 1);
        payroll.setPeriodYear(YEAR);
        payroll.setPeriodMonth(MONTH);
        when(payrollRepository.findWithDetailsById(PAYROLL_ID)).thenReturn(Optional.of(payroll));
        when(itemRepository.findWithDetailsByPayrollId(PAYROLL_ID)).thenReturn(List.of(item(), item()));
        when(userRepository.getReferenceById(actor.getId())).thenReturn(actor);
        when(payslipRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        PayrollRunResponse response = service.approve(COMPANY_ID, PAYROLL_ID, actor);

        assertThat(response.getStatus().getCode()).isEqualTo("APPROVED");
        assertThat(response.getApprovedBy()).isEqualTo(actor.getId());
        assertThat(response.getApprovedAt()).isNotNull();
        var captor = org.mockito.ArgumentCaptor.forClass(List.class);
        verify(payslipRepository).saveAll(captor.capture());
        List<Payslip> payslips = captor.getValue();
        assertThat(payslips).hasSize(2);
        assertThat(payslips.get(0).getPayslipNumber()).isEqualTo("PP-202607-001");
        assertThat(payslips.get(1).getPayslipNumber()).isEqualTo("PP-202607-002");
        verify(auditService).log(eq("PAYROLL_APPROVE"), eq(COMPANY_ID), eq(actor.getId()),
                eq("PAYROLL"), eq(PAYROLL_ID), eq(null), anyString());
    }

    @Test
    void approve_wrongStatus_conflicts() {
        Payroll payroll = payroll("COMPUTED", 1);
        when(payrollRepository.findWithDetailsById(PAYROLL_ID)).thenReturn(Optional.of(payroll));

        assertThatThrownBy(() -> service.approve(COMPANY_ID, PAYROLL_ID, actor))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void pay_approved_marksItemsPaid() {
        Payroll payroll = payroll("APPROVED", 1);
        when(payrollRepository.findWithDetailsById(PAYROLL_ID)).thenReturn(Optional.of(payroll));
        PayrollItem i1 = item();
        PayrollItem i2 = item();
        when(itemRepository.findWithDetailsByPayrollId(PAYROLL_ID)).thenReturn(List.of(i1, i2));

        PayrollRunResponse response = service.pay(COMPANY_ID, PAYROLL_ID, actor, "  VIR-123  ");

        assertThat(response.getStatus().getCode()).isEqualTo("PAID");
        assertThat(response.getPaidAt()).isNotNull();
        assertThat(i1.getPaidAt()).isNotNull();
        assertThat(i1.getBankTransferRef()).isEqualTo("VIR-123");
        assertThat(i2.getBankTransferRef()).isEqualTo("VIR-123");
        verify(auditService).log(eq("PAYROLL_PAY"), eq(COMPANY_ID), eq(actor.getId()),
                eq("PAYROLL"), eq(PAYROLL_ID), eq(null), anyString());
    }

    @Test
    void pay_wrongStatus_conflicts() {
        Payroll payroll = payroll("VALIDATED", 1);
        when(payrollRepository.findWithDetailsById(PAYROLL_ID)).thenReturn(Optional.of(payroll));

        assertThatThrownBy(() -> service.pay(COMPANY_ID, PAYROLL_ID, actor, null))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void cancel_computed_clearsArtifacts() {
        Payroll payroll = payroll("COMPUTED", 1);
        when(payrollRepository.findWithDetailsById(PAYROLL_ID)).thenReturn(Optional.of(payroll));

        PayrollRunResponse response = service.cancel(COMPANY_ID, PAYROLL_ID, actor, "erreur");

        assertThat(response.getStatus().getCode()).isEqualTo("CANCELLED");
        assertThat(payroll.getTotalNet()).isEqualByComparingTo("0.00");
        assertThat(payroll.getEmployeeCount()).isZero();
        verify(snapshotRepository).deleteByPayrollId(PAYROLL_ID);
        verify(itemRepository).deleteByPayrollId(PAYROLL_ID);
    }

    @Test
    void cancel_approved_conflicts() {
        Payroll payroll = payroll("APPROVED", 1);
        when(payrollRepository.findWithDetailsById(PAYROLL_ID)).thenReturn(Optional.of(payroll));

        assertThatThrownBy(() -> service.cancel(COMPANY_ID, PAYROLL_ID, actor, null))
                .isInstanceOf(ConflictException.class);
    }

    // ------------------------------------------------------------------ reads

    @Test
    void get_wrongTenant_returns404() {
        Payroll payroll = payroll("DRAFT", 0);
        when(payrollRepository.findWithDetailsById(PAYROLL_ID)).thenReturn(Optional.of(payroll));

        assertThatThrownBy(() -> service.get(999L, PAYROLL_ID))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void items_onDraft_conflicts() {
        Payroll payroll = payroll("DRAFT", 0);
        when(payrollRepository.findWithDetailsById(PAYROLL_ID)).thenReturn(Optional.of(payroll));

        assertThatThrownBy(() -> service.items(COMPANY_ID, PAYROLL_ID))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void list_mapsScopedResults() {
        when(payrollRepository.findScoped(COMPANY_ID, YEAR, MONTH, "COMPUTED"))
                .thenReturn(List.of(payroll("COMPUTED", 1)));

        var result = service.list(COMPANY_ID, YEAR, MONTH, "COMPUTED");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getStatus().getCode()).isEqualTo("COMPUTED");
    }

    // ------------------------------------------------------------------ fixtures

    private Payroll payroll(String statusCode, int employeeCount) {
        Payroll p = new Payroll();
        p.setId(PAYROLL_ID);
        p.setCompany(company);
        p.setPeriodYear(YEAR);
        p.setPeriodMonth(MONTH);
        p.setRunDate(LocalDate.of(YEAR, MONTH, 1));
        PayrollStatus s = new PayrollStatus();
        s.setCode(statusCode);
        s.setLabel(statusCode);
        p.setStatus(s);
        p.setEmployeeCount(employeeCount);
        return p;
    }

    private Employee employee() {
        Employee e = new Employee();
        e.setId(EMPLOYEE_ID);
        e.setCompany(company);
        e.setFirstName("Ahmed");
        e.setLastName("Ben Salah");
        return e;
    }

    private EmployeeContract activeContract() {
        EmployeeContract c = new EmployeeContract();
        c.setId(CONTRACT_ID);
        c.setEmployee(employee());
        c.setCompany(company);
        ContractStatus s = new ContractStatus();
        s.setCode("ACTIVE");
        c.setStatus(s);
        c.setStartDate(LocalDate.of(2026, 1, 1));
        return c;
    }

    private SalaryComponent baseComponent() {
        SalaryComponent sc = new SalaryComponent();
        sc.setComponentType(componentType("BASE_SALARY"));
        sc.setLabel("Salaire de base");
        sc.setAmount(new BigDecimal("1500.00"));
        sc.setIsPercentage(false);
        sc.setStartDate(LocalDate.of(2026, 1, 1));
        sc.setIsActive(true);
        return sc;
    }

    private SalaryComponentType componentType(String code) {
        SalaryComponentType t = new SalaryComponentType();
        t.setCode(code);
        t.setLabel(code);
        t.setCategory("BASE");
        t.setIsSubjectToCnss(true);
        t.setIsSubjectToIrpp(true);
        t.setIsSubjectToCss(false);
        return t;
    }

    private CompanySettings settings() {
        CompanySettings cs = new CompanySettings();
        cs.setCompany(company);
        cs.setMonthlyWorkingHours(new BigDecimal("151.67"));
        cs.setOvertimeEnabled(true);
        cs.setOvertimeRateMultiplier(new BigDecimal("1.25"));
        return cs;
    }

    private CnssRate cnss() {
        CnssRate r = new CnssRate();
        r.setYear(YEAR);
        r.setEmployeeRate(new BigDecimal("9.68"));
        r.setEmployerRate(new BigDecimal("16.57"));
        return r;
    }

    private CssRate css() {
        CssRate r = new CssRate();
        r.setYear(YEAR);
        r.setEmployeeRate(new BigDecimal("0.50"));
        r.setEmployerRate(new BigDecimal("1.00"));
        return r;
    }

    private List<TaxBracket> brackets() {
        Object[][] rows = {
                {0, 5000, 0}, {5000, 20000, 15}, {20000, 30000, 25}, {30000, 50000, 30},
                {50000, 60000, 33}, {60000, 80000, 36}, {80000, 150000, 38}, {150000, 999999999, 40}};
        List<TaxBracket> brackets = new ArrayList<>();
        int order = 1;
        for (Object[] row : rows) {
            TaxBracket b = new TaxBracket();
            b.setYear(YEAR);
            b.setBracketOrder(order++);
            b.setLowerBound(new BigDecimal((Integer) row[0]));
            b.setUpperBound(new BigDecimal((Integer) row[1]));
            b.setRatePercent(new BigDecimal((Integer) row[2]));
            brackets.add(b);
        }
        return brackets;
    }

    private SmigValue smig() {
        SmigValue s = new SmigValue();
        s.setYear(YEAR);
        s.setMonthlyRate(new BigDecimal("524.954"));
        return s;
    }

    private FamilyAllowance familyAllowance() {
        FamilyAllowance f = new FamilyAllowance();
        f.setYear(YEAR);
        f.setAmountPerChild(BigDecimal.ZERO);
        return f;
    }

    private List<AttendanceSummary> summaries(LocalDate from, LocalDate to) {
        List<AttendanceSummary> list = new ArrayList<>();
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            AttendanceSummary s = new AttendanceSummary();
            s.setCompany(company);
            s.setEmployee(employee());
            s.setWorkDate(d);
            DayType dayType = new DayType();
            dayType.setCode("WORKDAY");
            s.setDayType(dayType);
            AttendanceStatus status = new AttendanceStatus();
            status.setCode("PRESENT");
            s.setStatus(status);
            s.setWorkedMinutes(480);
            s.setOvertimeMinutes(0);
            s.setLateMinutes(0);
            s.setMissingMinutes(0);
            list.add(s);
        }
        return list;
    }

    private PayrollItem item() {
        PayrollItem i = new PayrollItem();
        i.setId(1001L);
        i.setEmployee(employee());
        i.setContract(activeContract());
        i.setBaseSalary(new BigDecimal("1500.00"));
        i.setWorkDays(31);
        i.setWorkHours(new BigDecimal("248.00"));
        i.setGrossSalary(new BigDecimal("1500.00"));
        i.setNetSalary(new BigDecimal("1207.71"));
        return i;
    }
}
