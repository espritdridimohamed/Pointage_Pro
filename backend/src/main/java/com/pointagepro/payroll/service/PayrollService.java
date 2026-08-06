package com.pointagepro.payroll.service;

import com.pointagepro.attendance.entity.AttendanceSummary;
import com.pointagepro.attendance.entity.WorkScheduleLine;
import com.pointagepro.attendance.repository.AttendanceSummaryRepository;
import com.pointagepro.attendance.repository.WorkScheduleLineRepository;
import com.pointagepro.attendance.service.AttendanceEngineService;
import com.pointagepro.audit.service.AuditService;
import com.pointagepro.auth.entity.User;
import com.pointagepro.auth.repository.UserRepository;
import com.pointagepro.company.entity.Company;
import com.pointagepro.company.entity.CompanySettings;
import com.pointagepro.company.repository.CompanySettingsRepository;
import com.pointagepro.contract.entity.EmployeeContract;
import com.pointagepro.contract.entity.SalaryComponent;
import com.pointagepro.contract.entity.SalaryComponentType;
import com.pointagepro.contract.repository.EmployeeContractRepository;
import com.pointagepro.contract.repository.SalaryComponentRepository;
import com.pointagepro.contract.repository.SalaryComponentTypeRepository;
import com.pointagepro.employee.entity.Employee;
import com.pointagepro.employee.entity.EmployeeTaxProfile;
import com.pointagepro.employee.repository.EmployeeRepository;
import com.pointagepro.employee.repository.EmployeeTaxProfileRepository;
import com.pointagepro.leave.entity.LeaveRequest;
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
import com.pointagepro.payroll.dto.PayrollComponentResponse;
import com.pointagepro.payroll.dto.PayrollItemResponse;
import com.pointagepro.payroll.dto.PayrollRunResponse;
import com.pointagepro.payroll.dto.PayslipResponse;
import com.pointagepro.payroll.engine.PayrollCalculation;
import com.pointagepro.payroll.engine.PayrollCalculator;
import com.pointagepro.payroll.engine.PayrollEngineInput;
import com.pointagepro.payroll.entity.Payroll;
import com.pointagepro.payroll.entity.PayrollAttendanceSnapshot;
import com.pointagepro.payroll.entity.PayrollItem;
import com.pointagepro.payroll.entity.PayrollItemComponent;
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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Payroll workflow (PAYROLL_BUSINESS_RULES.md, PAYROLL_API_CONTRACT.md).
 *
 * <p>Lifecycle {@code DRAFT -> COMPUTED -> VALIDATED -> APPROVED -> PAID}
 * ({@code CANCELLED} only from {@code DRAFT}/{@code COMPUTED}); frozen from
 * {@code VALIDATED} (409 on any backward transition). The compute step freezes
 * each eligible employee's attendance facts into
 * {@code payroll_attendance_snapshots} (compute-on-miss via the existing
 * attendance engine, {@code is_paid_leave} resolved from APPROVED paid leave
 * types), then runs the pure {@link PayrollCalculator} and stores the item and
 * its payslip component lines. Single-step approve/pay with
 * {@code approved_by}/{@code approved_at}/{@code paid_at} on the run; payslips
 * {@code PP-yyyyMM-NNN} are created at approve. Every transition is audited.
 *
 * <p>Public methods return DTOs and the mapping happens inside the transaction
 * (entity graphs are used for the reads that back a response).
 */
@Service
@RequiredArgsConstructor
public class PayrollService {

    private static final String STATUS_DRAFT = "DRAFT";
    private static final String STATUS_COMPUTED = "COMPUTED";
    private static final String STATUS_VALIDATED = "VALIDATED";
    private static final String STATUS_APPROVED = "APPROVED";
    private static final String STATUS_PAID = "PAID";
    private static final String STATUS_CANCELLED = "CANCELLED";

    private static final String ENTITY_TYPE = "PAYROLL";
    private static final String BASE_TYPE_CODE = "BASE_SALARY";
    private static final int NOTES_MAX = 500;
    private static final Set<String> PRESENT_STATUSES = Set.of("PRESENT", "LATE", "HALF_DAY", "ADJUSTED");

    private final PayrollRepository payrollRepository;
    private final PayrollStatusRepository statusRepository;
    private final PayrollItemRepository itemRepository;
    private final PayrollItemComponentRepository componentRepository;
    private final PayslipRepository payslipRepository;
    private final PayrollAttendanceSnapshotRepository snapshotRepository;
    private final EmployeeRepository employeeRepository;
    private final EmployeeContractRepository contractRepository;
    private final SalaryComponentRepository salaryComponentRepository;
    private final SalaryComponentTypeRepository componentTypeRepository;
    private final EmployeeTaxProfileRepository taxProfileRepository;
    private final CompanySettingsRepository settingsRepository;
    private final AttendanceSummaryRepository summaryRepository;
    private final WorkScheduleLineRepository workScheduleLineRepository;
    private final LeaveRequestRepository leaveRequestRepository;
    private final TaxBracketRepository taxBracketRepository;
    private final CnssRateRepository cnssRateRepository;
    private final CssRateRepository cssRateRepository;
    private final SmigValueRepository smigValueRepository;
    private final FamilyAllowanceRepository familyAllowanceRepository;
    private final UserRepository userRepository;
    private final AttendanceEngineService engineService;
    private final AuditService auditService;

    // ------------------------------------------------------------------ create

    @Transactional
    public PayrollRunResponse create(Company company, User actor, Integer periodYear, Integer periodMonth,
                                     String notes) {
        validatePeriod(periodYear, periodMonth);
        String trimmedNotes = trimNotes(notes);

        Optional<Payroll> existing = payrollRepository
                .findByCompanyIdAndPeriodYearAndPeriodMonth(company.getId(), periodYear, periodMonth);
        if (existing.isPresent()) {
            Payroll payroll = existing.get();
            String status = payroll.getStatus().getCode();
            if (STATUS_CANCELLED.equals(status)) {
                clearArtifacts(payroll.getId());
                resetTotals(payroll);
                payroll.setStatus(status(STATUS_DRAFT));
                payroll.setRunDate(LocalDate.now());
                payroll.setNotes(trimmedNotes);
                payrollRepository.save(payroll);
                auditService.log("STATUS_CHANGE", company.getId(), actor.getId(), ENTITY_TYPE, payroll.getId(),
                        json(STATUS_CANCELLED), json(STATUS_DRAFT));
                return PayrollRunResponse.from(payroll, List.of());
            }
            if (STATUS_DRAFT.equals(status)) {
                return PayrollRunResponse.from(payroll, List.of());
            }
            throw new ConflictException("Une paie existe déjà pour cette période (statut " + status + ")");
        }

        Payroll payroll = new Payroll();
        payroll.setCompany(company);
        payroll.setPeriodYear(periodYear);
        payroll.setPeriodMonth(periodMonth);
        payroll.setRunDate(LocalDate.now());
        payroll.setStatus(status(STATUS_DRAFT));
        payroll.setCreatedBy(userRepository.getReferenceById(actor.getId()));
        payroll.setNotes(trimmedNotes);
        Payroll saved = payrollRepository.save(payroll);
        auditService.log("CREATE", company.getId(), actor.getId(), ENTITY_TYPE, saved.getId(),
                null, json(STATUS_DRAFT));
        return PayrollRunResponse.from(saved, List.of());
    }

    // ----------------------------------------------------------------- compute

    @Transactional
    public PayrollRunResponse compute(Long companyId, Long payrollId, User actor) {
        Payroll payroll = loadScoped(companyId, payrollId);
        String status = payroll.getStatus().getCode();
        if (!STATUS_DRAFT.equals(status) && !STATUS_COMPUTED.equals(status)) {
            throw new ConflictException("La paie est gelée (statut " + status + "); recalcule interdit");
        }

        int year = payroll.getPeriodYear();
        int month = payroll.getPeriodMonth();
        LocalDate pFirst = LocalDate.of(year, month, 1);
        LocalDate pLast = pFirst.withDayOfMonth(pFirst.lengthOfMonth());
        LocalDate today = LocalDate.now();
        if (pFirst.isAfter(today)) {
            throw new ConflictException("La période est entièrement dans le futur; calcul impossible");
        }

        String oldTotals = totalsJson(payroll);
        clearArtifacts(payroll.getId());

        LegalRates legal = loadLegalRates(companyId, year);
        List<String> warnings = new ArrayList<>();
        if (pLast.isAfter(today)) {
            warnings.add("Période en cours : les jours après " + today + " ne sont pas inclus");
        }
        if (legal.familyAllowance() != null
                && legal.familyAllowance().getAmountPerChild().signum() <= 0) {
            warnings.add("Allocations familiales " + year + " non configurées (montant nul)");
        }

        BigDecimal totalGross = BigDecimal.ZERO;
        BigDecimal totalCnss = BigDecimal.ZERO;
        BigDecimal totalIrpp = BigDecimal.ZERO;
        BigDecimal totalCss = BigDecimal.ZERO;
        BigDecimal totalNet = BigDecimal.ZERO;
        BigDecimal totalDeductions = BigDecimal.ZERO;
        int count = 0;

        for (Employee employee : employeeRepository.findByCompanyIdOrderByLastNameAsc(companyId)) {
            ContextOutcome outcome = resolveContext(companyId, employee, pFirst, pLast, today);
            if (outcome.excluded()) {
                warnings.add("Employé " + employee.getId() + " exclu : " + outcome.reason());
                continue;
            }
            EmployeeContext ctx = outcome.context();

            List<PayrollAttendanceSnapshot> snapshots = freeze(companyId, actor, payroll.getId(), ctx, pFirst, pLast);
            AttendanceAggregate agg = aggregate(snapshots);
            if (agg.scheduledWorkdays() == 0) {
                snapshotRepository.deleteAll(snapshots);
                warnings.add("Employé " + employee.getId() + " exclu : aucun jour ouvré dans la fenêtre");
                continue;
            }

            PayrollEngineInput input = new PayrollEngineInput(
                    ctx.baseSalary(),
                    legal.settings().getMonthlyWorkingHours(),
                    legal.settings().getOvertimeEnabled(),
                    legal.settings().getOvertimeRateMultiplier(),
                    agg.scheduledWorkdays(),
                    agg.presentDays(),
                    agg.workedMinutes(),
                    agg.overtimeMinutes(),
                    agg.lateMinutes(),
                    agg.absenceMinutes(),
                    ctx.components(),
                    cnssInput(legal.cnss()),
                    cssInput(legal.css()),
                    taxBrackets(legal.brackets()),
                    ctx.taxProfile(),
                    legal.smig() == null ? null : legal.smig().getMonthlyRate(),
                    new PayrollEngineInput.DerivedLabels(
                            "Base salary", "Overtime", "Absence deduction", "Late deduction"));

            PayrollCalculation calc = PayrollCalculator.calculate(input);
            warnings.addAll(calc.warnings());

            PayrollItem item = persistItem(payroll, ctx, calc);
            totalGross = totalGross.add(calc.grossSalary());
            totalCnss = totalCnss.add(calc.cnssSalarial());
            totalIrpp = totalIrpp.add(calc.irpp());
            totalCss = totalCss.add(calc.css());
            totalNet = totalNet.add(calc.netSalary());
            totalDeductions = totalDeductions.add(itemDeductions(calc));
            count++;
        }

        payroll.setStatus(status(STATUS_COMPUTED));
        payroll.setRunDate(today);
        payroll.setTotalGross(totalGross);
        payroll.setTotalCnss(totalCnss);
        payroll.setTotalIrpp(totalIrpp);
        payroll.setTotalCss(totalCss);
        payroll.setTotalNet(totalNet);
        payroll.setTotalDeductions(totalDeductions);
        payroll.setEmployeeCount(count);
        payroll.setNotes(withWarningCount(payroll.getNotes(), warnings.size()));
        payrollRepository.save(payroll);

        auditService.log("PAYROLL_RUN", companyId, actor.getId(), ENTITY_TYPE, payroll.getId(),
                oldTotals, totalsJson(payroll));
        return PayrollRunResponse.from(payroll, warnings);
    }

    // ------------------------------------------------------- validate/approve/pay/cancel

    @Transactional
    public PayrollRunResponse validate(Long companyId, Long payrollId, User actor, String notes) {
        Payroll payroll = loadScoped(companyId, payrollId);
        requireStatus(payroll, STATUS_COMPUTED);
        String old = payroll.getStatus().getCode();
        payroll.setStatus(status(STATUS_VALIDATED));
        payroll.setNotes(appendNotes(payroll.getNotes(), notes));
        auditService.log("STATUS_CHANGE", companyId, actor.getId(), ENTITY_TYPE, payroll.getId(),
                json(old), json(STATUS_VALIDATED));
        return PayrollRunResponse.from(payroll, List.of());
    }

    @Transactional
    public PayrollRunResponse approve(Long companyId, Long payrollId, User actor) {
        Payroll payroll = loadScoped(companyId, payrollId);
        requireStatus(payroll, STATUS_VALIDATED);
        String old = payroll.getStatus().getCode();
        payroll.setStatus(status(STATUS_APPROVED));
        payroll.setApprovedBy(userRepository.getReferenceById(actor.getId()));
        payroll.setApprovedAt(LocalDateTime.now());

        List<PayrollItem> items = itemRepository.findWithDetailsByPayrollId(payrollId);
        String prefix = String.format("PP-%04d%02d-", payroll.getPeriodYear(), payroll.getPeriodMonth());
        List<Payslip> payslips = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            Payslip payslip = new Payslip();
            payslip.setPayrollItem(items.get(i));
            payslip.setPayslipNumber(prefix + String.format("%03d", i + 1));
            payslip.setIssuedAt(LocalDateTime.now());
            payslips.add(payslip);
        }
        payslipRepository.saveAll(payslips);

        auditService.log("STATUS_CHANGE", companyId, actor.getId(), ENTITY_TYPE, payroll.getId(),
                json(old), json(STATUS_APPROVED));
        auditService.log("PAYROLL_APPROVE", companyId, actor.getId(), ENTITY_TYPE, payroll.getId(),
                null, json(STATUS_APPROVED));
        return PayrollRunResponse.from(payroll, List.of());
    }

    @Transactional
    public PayrollRunResponse pay(Long companyId, Long payrollId, User actor, String bankTransferRef) {
        Payroll payroll = loadScoped(companyId, payrollId);
        requireStatus(payroll, STATUS_APPROVED);
        String old = payroll.getStatus().getCode();
        payroll.setStatus(status(STATUS_PAID));
        payroll.setPaidAt(LocalDateTime.now());

        String ref = bankTransferRef == null ? null : bankTransferRef.trim();
        if (ref != null && ref.length() > 50) {
            ref = ref.substring(0, 50);
        }
        for (PayrollItem item : itemRepository.findWithDetailsByPayrollId(payrollId)) {
            item.setPaidAt(LocalDateTime.now());
            item.setBankTransferRef(ref);
        }

        auditService.log("STATUS_CHANGE", companyId, actor.getId(), ENTITY_TYPE, payroll.getId(),
                json(old), json(STATUS_PAID));
        auditService.log("PAYROLL_PAY", companyId, actor.getId(), ENTITY_TYPE, payroll.getId(),
                null, json(STATUS_PAID));
        return PayrollRunResponse.from(payroll, List.of());
    }

    @Transactional
    public PayrollRunResponse cancel(Long companyId, Long payrollId, User actor, String notes) {
        Payroll payroll = loadScoped(companyId, payrollId);
        String status = payroll.getStatus().getCode();
        if (!STATUS_DRAFT.equals(status) && !STATUS_COMPUTED.equals(status)) {
            throw new ConflictException("La paie est gelée (statut " + status + "); annulation interdite");
        }
        String old = payroll.getStatus().getCode();
        clearArtifacts(payroll.getId());
        resetTotals(payroll);
        payroll.setStatus(status(STATUS_CANCELLED));
        payroll.setNotes(appendNotes(payroll.getNotes(), notes));
        auditService.log("STATUS_CHANGE", companyId, actor.getId(), ENTITY_TYPE, payroll.getId(),
                json(old), json(STATUS_CANCELLED));
        return PayrollRunResponse.from(payroll, List.of());
    }

    // ------------------------------------------------------------------- reads

    @Transactional(readOnly = true)
    public List<PayrollRunResponse> list(Long companyId, Integer year, Integer month, String statusCode) {
        return payrollRepository.findScoped(companyId, year, month, statusCode).stream()
                .map(p -> PayrollRunResponse.from(p, List.of()))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public PayrollRunResponse get(Long companyId, Long payrollId) {
        Payroll payroll = loadScoped(companyId, payrollId);
        List<String> warnings = reconstructWarnings(companyId, payroll);
        return PayrollRunResponse.from(payroll, warnings);
    }

    @Transactional(readOnly = true)
    public List<PayrollItemResponse> items(Long companyId, Long payrollId) {
        Payroll payroll = loadScoped(companyId, payrollId);
        if (STATUS_DRAFT.equals(payroll.getStatus().getCode())) {
            throw new ConflictException("La paie n'a pas encore été calculée");
        }
        List<PayrollItem> items = itemRepository.findWithDetailsByPayrollId(payrollId);
        Map<Long, List<PayrollComponentResponse>> componentsByItem = componentsByItem(
                items.stream().map(PayrollItem::getId).collect(Collectors.toList()));
        List<PayrollItemResponse> responses = new ArrayList<>();
        for (PayrollItem item : items) {
            responses.add(PayrollItemResponse.from(item,
                    componentsByItem.getOrDefault(item.getId(), List.of())));
        }
        return responses;
    }

    @Transactional(readOnly = true)
    public List<PayslipResponse> payslips(Long companyId, Long payrollId) {
        loadScoped(companyId, payrollId);
        return payslips(payslipRepository.findWithDetailsByPayrollId(payrollId));
    }

    @Transactional(readOnly = true)
    public PayslipResponse getPayslip(Long companyId, Long payslipId) {
        Payslip payslip = payslipRepository.findWithDetailsById(payslipId)
                .filter(p -> p.getPayrollItem().getPayroll().getCompany().getId().equals(companyId))
                .orElseThrow(() -> new ResourceNotFoundException("Payslip", "id", payslipId));
        return payslips(List.of(payslip)).get(0);
    }

    // ------------------------------------------------------------------ helpers

    private List<PayslipResponse> payslips(List<Payslip> payslips) {
        List<Long> itemIds = payslips.stream()
                .map(p -> p.getPayrollItem().getId()).collect(Collectors.toList());
        Map<Long, List<PayrollComponentResponse>> componentsByItem =
                itemIds.isEmpty() ? Map.of() : componentsByItem(itemIds);
        List<PayslipResponse> responses = new ArrayList<>();
        for (Payslip payslip : payslips) {
            responses.add(PayslipResponse.from(payslip,
                    componentsByItem.getOrDefault(payslip.getPayrollItem().getId(), List.of())));
        }
        return responses;
    }

    private Map<Long, List<PayrollComponentResponse>> componentsByItem(List<Long> itemIds) {
        if (itemIds.isEmpty()) {
            return Map.of();
        }
        return componentRepository.findByPayrollItemIdInOrderByPayrollItemIdAscSortOrderAsc(itemIds)
                .stream()
                .collect(Collectors.groupingBy(
                        c -> c.getPayrollItem().getId(),
                        LinkedHashMap::new,
                        Collectors.mapping(PayrollComponentResponse::from, Collectors.toList())));
    }

    private List<String> reconstructWarnings(Long companyId, Payroll payroll) {
        if (payroll.getEmployeeCount() == 0) {
            return List.of();
        }
        LocalDate pFirst = LocalDate.of(payroll.getPeriodYear(), payroll.getPeriodMonth(), 1);
        LocalDate pLast = pFirst.withDayOfMonth(pFirst.lengthOfMonth());
        Set<Long> itemEmployees = itemRepository.findWithDetailsByPayrollId(payroll.getId()).stream()
                .map(i -> i.getEmployee().getId()).collect(Collectors.toSet());
        List<String> warnings = new ArrayList<>();
        for (Employee employee : employeeRepository.findByCompanyIdOrderByLastNameAsc(companyId)) {
            if (itemEmployees.contains(employee.getId())) {
                continue;
            }
            ContextOutcome outcome = resolveContext(companyId, employee, pFirst, pLast, LocalDate.now());
            if (outcome.excluded()) {
                warnings.add("Employé " + employee.getId() + " exclu : " + outcome.reason());
            }
        }
        return warnings;
    }

    /** Freeze step: compute-on-miss then copy each day verbatim into the snapshot table. */
    private List<PayrollAttendanceSnapshot> freeze(Long companyId, User actor, Long payrollId,
                                                   EmployeeContext ctx, LocalDate pFirst, LocalDate pLast) {
        Employee employee = ctx.employee();
        List<AttendanceSummary> summaries = summaryRepository
                .findByEmployeeIdAndWorkDateBetweenOrderByWorkDateAsc(employee.getId(), ctx.windowFrom(), ctx.windowTo());
        Set<LocalDate> present = summaries.stream()
                .map(AttendanceSummary::getWorkDate).collect(Collectors.toSet());
        for (LocalDate date = ctx.windowFrom(); !date.isAfter(ctx.windowTo()); date = date.plusDays(1)) {
            if (!present.contains(date)) {
                engineService.recomputeDay(companyId, employee.getId(), date, actor,
                        "payroll:" + payrollId);
            }
        }
        summaries = summaryRepository
                .findByEmployeeIdAndWorkDateBetweenOrderByWorkDateAsc(employee.getId(), ctx.windowFrom(), ctx.windowTo());

        List<LeaveRequest> leaves = leaveRequestRepository
                .findByEmployeeIdAndStatus_CodeAndStartDateLessThanEqualAndEndDateGreaterThanEqualOrderByStartDateAsc(
                        employee.getId(), "APPROVED", ctx.windowTo(), ctx.windowFrom());

        List<PayrollAttendanceSnapshot> snapshots = new ArrayList<>();
        for (AttendanceSummary s : summaries) {
            PayrollAttendanceSnapshot snap = new PayrollAttendanceSnapshot();
            snap.setPayrollId(payrollId);
            snap.setCompany(s.getCompany());
            snap.setEmployee(s.getEmployee());
            snap.setWorkDate(s.getWorkDate());
            snap.setDayType(s.getDayType());
            snap.setSchedule(s.getSchedule());
            if (s.getSchedule() != null) {
                WorkScheduleLine line = workScheduleLineRepository
                        .findByScheduleIdAndWeekday(s.getSchedule().getId(),
                                s.getWorkDate().getDayOfWeek().getValue()).orElse(null);
                if (line != null) {
                    snap.setScheduledStartTime(line.getStartTime());
                    snap.setScheduledEndTime(line.getEndTime());
                    snap.setScheduledBreakMinutes(line.getBreakMinutes());
                } else {
                    snap.setScheduledBreakMinutes(0);
                }
            }
            snap.setStatus(s.getStatus());
            snap.setFirstIn(s.getFirstIn());
            snap.setLastOut(s.getLastOut());
            snap.setWorkedMinutes(s.getWorkedMinutes());
            snap.setLateMinutes(s.getLateMinutes());
            snap.setEarlyExitMinutes(s.getEarlyExitMinutes());
            snap.setMissingMinutes(s.getMissingMinutes());
            snap.setOvertimeMinutes(s.getOvertimeMinutes());
            snap.setNettedWorkMinutes(s.getNettedWorkMinutes());
            snap.setAdjustmentMinutes(s.getAdjustmentMinutes());
            snap.setIsWeekend(s.getIsWeekend());
            snap.setIsHoliday(s.getIsHoliday());
            snap.setSourceSummary(s);
            snap.setIsPaidLeave(isPaidLeave(s, leaves));
            snapshots.add(snap);
        }
        snapshotRepository.saveAll(snapshots);
        return snapshots;
    }

    private boolean isPaidLeave(AttendanceSummary s, List<LeaveRequest> leaves) {
        if (s.getStatus() == null || !"LEAVE".equals(s.getStatus().getCode())) {
            return false;
        }
        LocalDate date = s.getWorkDate();
        return leaves.stream().anyMatch(l ->
                !l.getStartDate().isAfter(date) && !l.getEndDate().isBefore(date)
                        && Boolean.TRUE.equals(l.getLeaveType().getIsPaid()));
    }

    private AttendanceAggregate aggregate(List<PayrollAttendanceSnapshot> snapshots) {
        int scheduled = 0;
        int present = 0;
        long worked = 0;
        long overtime = 0;
        long late = 0;
        long missing = 0;
        for (PayrollAttendanceSnapshot snap : snapshots) {
            worked += nz(snap.getWorkedMinutes());
            overtime += nz(snap.getOvertimeMinutes());
            late += nz(snap.getLateMinutes());
            missing += nz(snap.getMissingMinutes());
            String dayType = snap.getDayType() == null ? null : snap.getDayType().getCode();
            if ("WORKDAY".equals(dayType)) {
                scheduled++;
                if (isPaidDay(snap)) {
                    present++;
                }
            }
        }
        return new AttendanceAggregate(scheduled, present, (int) worked, (int) overtime, (int) late, (int) missing);
    }

    private boolean isPaidDay(PayrollAttendanceSnapshot snap) {
        String code = snap.getStatus() == null ? null : snap.getStatus().getCode();
        if (PRESENT_STATUSES.contains(code)) {
            return true;
        }
        return "LEAVE".equals(code) && Boolean.TRUE.equals(snap.getIsPaidLeave());
    }

    private PayrollItem persistItem(Payroll payroll, EmployeeContext ctx, PayrollCalculation calc) {
        PayrollItem item = new PayrollItem();
        item.setPayroll(payroll);
        item.setEmployee(ctx.employee());
        item.setContract(ctx.contract());
        item.setBaseSalary(calc.baseSalary());
        item.setWorkDays(calc.workDays());
        item.setWorkHours(calc.workHours());
        item.setOvertimeMinutes(calc.overtimeMinutes());
        item.setOvertimeAmount(calc.overtimeAmount());
        item.setAbsenceMinutes(calc.absenceMinutes());
        item.setAbsenceDeduction(calc.absenceDeduction());
        item.setLateMinutes(calc.lateMinutes());
        item.setLateDeduction(calc.lateDeduction());
        item.setGrossSalary(calc.grossSalary());
        item.setCnssSalarial(calc.cnssSalarial());
        item.setCnssPatronal(BigDecimal.ZERO); // employer cost: informational, not stored (§4.7)
        item.setIrpp(calc.irpp());
        item.setCss(calc.css());
        item.setNetSalary(calc.netSalary());
        item.setCancelled(false);
        itemRepository.save(item);

        List<PayrollItemComponent> components = new ArrayList<>();
        for (PayrollCalculation.ComponentResult cr : calc.components()) {
            PayrollItemComponent pic = new PayrollItemComponent();
            pic.setPayrollItem(item);
            pic.setComponentType(componentTypeRepository.findByCode(cr.code()).orElse(null));
            pic.setLabel(cr.label());
            pic.setCategory(cr.category());
            pic.setAmount(cr.amount());
            pic.setIsPercentage(cr.isPercentage());
            pic.setPercentageValue(cr.percentageValue());
            pic.setSortOrder(cr.sortOrder());
            components.add(pic);
        }
        componentRepository.saveAll(components);
        return item;
    }

    private BigDecimal itemDeductions(PayrollCalculation calc) {
        BigDecimal deductions = calc.absenceDeduction().add(calc.lateDeduction());
        for (PayrollCalculation.ComponentResult cr : calc.components()) {
            if ("DEDUCTION".equals(cr.category())) {
                deductions = deductions.add(cr.amount());
            }
        }
        return deductions;
    }

    /** Eligibility: active contract overlapping P + BASE_SALARY effective at P.last. */
    private ContextOutcome resolveContext(Long companyId, Employee employee,
                                          LocalDate pFirst, LocalDate pLast, LocalDate today) {
        if (employee.getExitDate() != null && employee.getExitDate().isBefore(pFirst)) {
            return new ContextOutcome(null, "employé quitté le " + employee.getExitDate()
                    + " (avant la période)");
        }
        EmployeeContract contract = null;
        for (EmployeeContract c : contractRepository.findByEmployeeIdOrderByStartDateDesc(employee.getId())) {
            if (c.getStatus() != null && "ACTIVE".equals(c.getStatus().getCode())
                    && !c.getStartDate().isAfter(pLast)
                    && (c.getEndDate() == null || !c.getEndDate().isBefore(pFirst))) {
                contract = c;
                break;
            }
        }
        if (contract == null) {
            return new ContextOutcome(null, "pas de contrat actif sur la période");
        }
        LocalDate windowFrom = max(contract.getStartDate(), pFirst);
        LocalDate windowTo = min(min(contract.getEndDate() == null ? pLast : contract.getEndDate(), pLast), today);
        if (windowFrom.isAfter(windowTo)) {
            return new ContextOutcome(null, "fenêtre hors période");
        }

        BigDecimal baseSalary = null;
        List<PayrollEngineInput.ComponentInput> components = new ArrayList<>();
        for (SalaryComponent sc : salaryComponentRepository
                .findByContractIdAndIsActiveTrueOrderByStartDateDesc(contract.getId())) {
            if (sc.getStartDate().isAfter(pLast)
                    || (sc.getEndDate() != null && sc.getEndDate().isBefore(pFirst))) {
                continue;
            }
            SalaryComponentType type = sc.getComponentType();
            if (type == null) {
                continue;
            }
            if (BASE_TYPE_CODE.equals(type.getCode()) || "BASE".equalsIgnoreCase(type.getCategory())) {
                baseSalary = sc.getAmount();
            } else {
                components.add(new PayrollEngineInput.ComponentInput(
                        type.getCode(), sc.getLabel(), type.getCategory(),
                        sc.getAmount(), Boolean.TRUE.equals(sc.getIsPercentage()),
                        sc.getPercentageValue(), Boolean.TRUE.equals(type.getIsSubjectToCnss()),
                        Boolean.TRUE.equals(type.getIsSubjectToIrpp()),
                        Boolean.TRUE.equals(type.getIsSubjectToCss())));
            }
        }
        if (baseSalary == null) {
            return new ContextOutcome(null, "composante BASE_SALARY manquante");
        }

        PayrollEngineInput.TaxProfileInput taxProfile = resolveTaxProfile(employee.getId(), pFirst, pLast);
        return new ContextOutcome(
                new EmployeeContext(employee, contract, windowFrom, windowTo, baseSalary, components, taxProfile),
                null);
    }

    private PayrollEngineInput.TaxProfileInput resolveTaxProfile(Long employeeId,
                                                                 LocalDate pFirst, LocalDate pLast) {
        EmployeeTaxProfile best = null;
        for (EmployeeTaxProfile p : taxProfileRepository.findByEmployeeIdOrderByValidFromDesc(employeeId)) {
            if (p.getValidFrom().isAfter(pLast)) {
                continue;
            }
            if (p.getValidTo() != null && p.getValidTo().isBefore(pFirst)) {
                continue;
            }
            best = p;
            break;
        }
        if (best == null) {
            return new PayrollEngineInput.TaxProfileInput("CELIBATAIRE", false, 0, 0);
        }
        return new PayrollEngineInput.TaxProfileInput(
                best.getTaxSituation() == null ? "CELIBATAIRE" : best.getTaxSituation().getCode(),
                Boolean.TRUE.equals(best.getSpouseIsWorking()),
                nz(best.getNumberOfChildren()),
                nz(best.getNumberOfDisabledChildren()));
    }

    private LegalRates loadLegalRates(Long companyId, int year) {
        CompanySettings settings = settingsRepository.findByCompanyId(companyId)
                .orElseThrow(() -> new IllegalStateException("Company settings missing for " + companyId));
        return new LegalRates(settings,
                cnssRateRepository.findByYear(year).orElse(null),
                cssRateRepository.findByYear(year).orElse(null),
                taxBracketRepository.findByYearOrderByBracketOrderAsc(year),
                smigValueRepository.findByYear(year).orElse(null),
                familyAllowanceRepository.findByYear(year).orElse(null));
    }

    private PayrollEngineInput.CnssInput cnssInput(CnssRate rate) {
        if (rate == null) {
            return null;
        }
        return new PayrollEngineInput.CnssInput(rate.getEmployeeRate(), rate.getEmployerRate(),
                rate.getCeilingAmount());
    }

    private PayrollEngineInput.CssInput cssInput(CssRate rate) {
        if (rate == null) {
            return null;
        }
        return new PayrollEngineInput.CssInput(rate.getEmployeeRate());
    }

    private List<PayrollEngineInput.TaxBracketInput> taxBrackets(List<TaxBracket> brackets) {
        return brackets.stream()
                .map(b -> new PayrollEngineInput.TaxBracketInput(
                        b.getLowerBound(), b.getUpperBound(), b.getRatePercent()))
                .collect(Collectors.toList());
    }

    // ------------------------------------------------------------------ guard

    private Payroll loadScoped(Long companyId, Long payrollId) {
        return payrollRepository.findWithDetailsById(payrollId)
                .filter(p -> p.getCompany().getId().equals(companyId))
                .orElseThrow(() -> new ResourceNotFoundException("Payroll", "id", payrollId));
    }

    private void requireStatus(Payroll payroll, String expected) {
        String actual = payroll.getStatus().getCode();
        if (!expected.equals(actual)) {
            throw new ConflictException("Transition invalide depuis le statut " + actual);
        }
    }

    private PayrollStatus status(String code) {
        return statusRepository.findByCode(code)
                .orElseThrow(() -> new IllegalStateException("Missing payroll status: " + code));
    }

    private void clearArtifacts(Long payrollId) {
        payslipRepository.deleteByPayrollItemPayrollId(payrollId);
        snapshotRepository.deleteByPayrollId(payrollId);
        itemRepository.deleteByPayrollId(payrollId);
    }

    private void resetTotals(Payroll payroll) {
        payroll.setTotalGross(BigDecimal.ZERO);
        payroll.setTotalCnss(BigDecimal.ZERO);
        payroll.setTotalIrpp(BigDecimal.ZERO);
        payroll.setTotalCss(BigDecimal.ZERO);
        payroll.setTotalNet(BigDecimal.ZERO);
        payroll.setTotalDeductions(BigDecimal.ZERO);
        payroll.setEmployeeCount(0);
        payroll.setApprovedBy(null);
        payroll.setApprovedAt(null);
        payroll.setPaidAt(null);
    }

    private void validatePeriod(Integer year, Integer month) {
        if (year == null || year < 1 || year > 9999) {
            throw new IllegalArgumentException("periodYear must be a valid year");
        }
        if (month == null || month < 1 || month > 12) {
            throw new IllegalArgumentException("periodMonth must be between 1 and 12");
        }
    }

    private String trimNotes(String notes) {
        if (notes == null) {
            return null;
        }
        String trimmed = notes.trim();
        return trimmed.length() > NOTES_MAX ? trimmed.substring(0, NOTES_MAX) : trimmed;
    }

    private String appendNotes(String current, String addition) {
        if (addition == null || addition.isBlank()) {
            return current;
        }
        String base = current == null ? "" : current;
        String joined = base.isBlank() ? addition.trim() : base + " | " + addition.trim();
        return joined.length() > NOTES_MAX ? joined.substring(0, NOTES_MAX) : joined;
    }

    private String withWarningCount(String notes, int count) {
        String suffix = count > 0 ? " | " + count + " avertissement(s)" : "";
        String base = notes == null ? "" : notes;
        int idx = base.lastIndexOf(" | ");
        String stripped = base;
        if (idx >= 0 && base.substring(idx + 4).matches("\\d+ avertissement\\(s\\)")) {
            stripped = base.substring(0, idx);
        }
        String joined = stripped + suffix;
        return joined.length() > NOTES_MAX ? joined.substring(0, NOTES_MAX) : joined;
    }

    private String json(String status) {
        return "{\"status\":\"" + status + "\"}";
    }

    private String totalsJson(Payroll p) {
        return "{\"gross\":" + p.getTotalGross() + ",\"net\":" + p.getTotalNet() + "}";
    }

    private static int nz(Integer value) {
        return value == null ? 0 : value;
    }

    private static LocalDate max(LocalDate a, LocalDate b) {
        return a.isAfter(b) ? a : b;
    }

    private static LocalDate min(LocalDate a, LocalDate b) {
        return a.isBefore(b) ? a : b;
    }

    private record EmployeeContext(Employee employee, EmployeeContract contract,
                                   LocalDate windowFrom, LocalDate windowTo,
                                   BigDecimal baseSalary,
                                   List<PayrollEngineInput.ComponentInput> components,
                                   PayrollEngineInput.TaxProfileInput taxProfile) {
    }

    private record ContextOutcome(EmployeeContext context, String reason) {
        boolean excluded() {
            return context == null;
        }
    }

    private record AttendanceAggregate(int scheduledWorkdays, int presentDays,
                                       int workedMinutes, int overtimeMinutes,
                                       int lateMinutes, int absenceMinutes) {
    }

    private record LegalRates(CompanySettings settings, CnssRate cnss, CssRate css,
                              List<TaxBracket> brackets, SmigValue smig,
                              FamilyAllowance familyAllowance) {
    }
}
