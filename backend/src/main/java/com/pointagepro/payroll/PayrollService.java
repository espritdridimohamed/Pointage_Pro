package com.pointagepro.payroll;

import com.pointagepro.attendance.AttendanceRepository;
import com.pointagepro.attendance.AttendanceUpdatedEvent;
import com.pointagepro.employee.Employee;
import com.pointagepro.employee.EmployeeRepository;
import com.pointagepro.leave.LeaveRequest;
import com.pointagepro.leave.LeaveRequestRepository;
import com.pointagepro.notification.NotificationService;
import com.pointagepro.payroll.dto.PayrollItemResponse;
import com.pointagepro.payroll.dto.PayrollItemUpdate;
import com.pointagepro.payroll.dto.PayrollResponse;
import com.pointagepro.settings.CompanySettings;
import com.pointagepro.settings.CompanySettingsService;
import com.pointagepro.shared.exception.ResourceNotFoundException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
@Transactional
public class PayrollService {

    private static final Logger log = LoggerFactory.getLogger(PayrollService.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final PayrollRepository payrollRepository;
    private final PayrollItemRepository itemRepository;
    private final EmployeeRepository employeeRepository;
    private final AttendanceRepository attendanceRepository;
    private final LeaveRequestRepository leaveRequestRepository;
    private final CompanySettingsService settingsService;
    private final NotificationService notificationService;

    public PayrollService(PayrollRepository payrollRepository, PayrollItemRepository itemRepository,
                          EmployeeRepository employeeRepository,
                          AttendanceRepository attendanceRepository, LeaveRequestRepository leaveRequestRepository,
                          CompanySettingsService settingsService, NotificationService notificationService) {
        this.payrollRepository = payrollRepository;
        this.itemRepository = itemRepository;
        this.employeeRepository = employeeRepository;
        this.attendanceRepository = attendanceRepository;
        this.leaveRequestRepository = leaveRequestRepository;
        this.settingsService = settingsService;
        this.notificationService = notificationService;
    }

    public PayrollResponse generate(int month, int year) {
        Optional<Payroll> existing = payrollRepository.findByMonthAndYear(month, year);
        if (existing.isPresent()) {
            Payroll payroll = existing.get();
            List<PayrollItem> items = itemRepository.findByPayrollIdOrderByEmployeeIdAsc(payroll.getId());
            return toResponse(payroll, items);
        }

        CompanySettings settings = settingsService.get();

        List<Employee> activeEmployees = employeeRepository.findAll().stream()
                .filter(e -> "ACTIF".equals(e.getStatus()) || "CONGE".equals(e.getStatus()))
                .toList();

        Payroll payroll = new Payroll();
        payroll.setMonth(month);
        payroll.setYear(year);
        payroll.setStatus("DRAFT");
        payroll.setEmployeeCount(activeEmployees.size());
        Payroll savedPayroll = payrollRepository.save(payroll);

        List<PayrollItem> items = new ArrayList<>();
        BigDecimal totalGross = BigDecimal.ZERO;
        BigDecimal totalDeductions = BigDecimal.ZERO;
        BigDecimal totalNet = BigDecimal.ZERO;

        for (Employee emp : activeEmployees) {
            PayrollItem item = calculateItem(savedPayroll.getId(), emp, month, year, settings);
            items.add(itemRepository.save(item));
            totalGross = totalGross.add(item.getTotalGross());
            totalDeductions = totalDeductions.add(item.getTotalDeductions());
            totalNet = totalNet.add(item.getNetSalary());
        }

        savedPayroll.setTotalGross(totalGross);
        savedPayroll.setTotalDeductions(totalDeductions);
        savedPayroll.setTotalNet(totalNet);
        payrollRepository.save(savedPayroll);

        log.info("Payroll generated for {}/{}: {} employees, net total: {}",
                month, year, activeEmployees.size(), totalNet);

        String[] monthNames = {"", "Janvier", "Février", "Mars", "Avril", "Mai", "Juin",
            "Juillet", "Août", "Septembre", "Octobre", "Novembre", "Décembre"};
        notificationService.notify("PAYROLL_GENERATED", "Paie mensuelle générée",
            "Bulletin " + monthNames[month] + " " + year + " créé — " + activeEmployees.size() + " employés, " + totalNet + " DT",
            "HIGH", "PAYROLL", savedPayroll.getId());

        return toResponse(savedPayroll, items);
    }

    @Transactional(readOnly = true)
    public PayrollResponse getByMonth(int month, int year) {
        Payroll payroll = payrollRepository.findByMonthAndYear(month, year)
                .orElseThrow(() -> new ResourceNotFoundException("Payroll", "period", month + "/" + year));
        List<PayrollItem> items = itemRepository.findByPayrollIdOrderByEmployeeIdAsc(payroll.getId());
        return toResponse(payroll, items);
    }

    @Transactional(readOnly = true)
    public PayrollResponse getById(Long id) {
        Payroll payroll = payrollRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Payroll", "id", id));
        List<PayrollItem> items = itemRepository.findByPayrollIdOrderByEmployeeIdAsc(payroll.getId());
        return toResponse(payroll, items);
    }

    public PayrollItemResponse updateItem(Long itemId, PayrollItemUpdate update) {
        PayrollItem item = itemRepository.findById(itemId)
                .orElseThrow(() -> new ResourceNotFoundException("PayrollItem", "id", itemId));

        if (update.getPrimeTransport() != null) item.setPrimeTransport(update.getPrimeTransport());
        if (update.getPrimePerformance() != null) item.setPrimePerformance(update.getPrimePerformance());
        if (update.getPrimeOther() != null) item.setPrimeOther(update.getPrimeOther());

        CompanySettings settings = settingsService.get();
        recalculateItem(item, settings);
        PayrollItem saved = itemRepository.save(item);

        recalculatePayrollTotals(item.getPayrollId());
        return PayrollItemResponse.fromEntity(saved, null);
    }

    public void recalculateEmployeeInPayroll(Long employeeId, int month, int year) {
        Optional<Payroll> payrollOpt = payrollRepository.findByMonthAndYear(month, year);
        if (payrollOpt.isEmpty()) return;

        Payroll payroll = payrollOpt.get();
        if ("PAID".equals(payroll.getStatus())) return;

        CompanySettings settings = settingsService.get();
        List<PayrollItem> items = itemRepository.findByPayrollIdOrderByEmployeeIdAsc(payroll.getId());
        for (PayrollItem item : items) {
            if (item.getEmployeeId().equals(employeeId)) {
                recalculateItem(item, settings);
                itemRepository.save(item);
                break;
            }
        }
        recalculatePayrollTotals(payroll.getId());
        log.info("Payroll recalculated for employee {} month {}/{} due to leave change", employeeId, month, year);
    }

    public PayrollItemResponse payItem(Long itemId) {
        PayrollItem item = itemRepository.findById(itemId)
                .orElseThrow(() -> new ResourceNotFoundException("PayrollItem", "id", itemId));
        item.setStatus("PAYED");
        item.setPaidAt(LocalDateTime.now());
        PayrollItem saved = itemRepository.save(item);

        Employee emp = employeeRepository.findById(item.getEmployeeId()).orElse(null);
        String empName = emp != null ? emp.getFirstName() + " " + emp.getLastName() : "Employé #" + item.getEmployeeId();
        notificationService.notify("PAYROLL_ITEM_PAID", "Salaire versé",
            empName + " — " + item.getNetSalary() + " DT marqué versé", "MEDIUM", "PAYROLL", saved.getId());

        recalculatePayrollTotals(item.getPayrollId());

        List<PayrollItem> allItems = itemRepository.findByPayrollIdOrderByEmployeeIdAsc(item.getPayrollId());
        boolean allPaid = allItems.stream().allMatch(i -> "PAYED".equals(i.getStatus()));
        if (allPaid) {
            Payroll payroll = payrollRepository.findById(item.getPayrollId()).orElse(null);
            if (payroll != null) {
                String[] monthNames = {"", "Janvier", "Février", "Mars", "Avril", "Mai", "Juin",
                    "Juillet", "Août", "Septembre", "Octobre", "Novembre", "Décembre"};
                notificationService.notify("PAYROLL_ALL_PAID", "Paie finalisée",
                    "Tous les salaires de " + monthNames[payroll.getMonth()] + " " + payroll.getYear() + " versés",
                    "HIGH", "PAYROLL", payroll.getId());
            }
        }

        return PayrollItemResponse.fromEntity(saved, null);
    }

    public PayrollResponse payAll(Long payrollId) {
        Payroll payroll = payrollRepository.findById(payrollId)
                .orElseThrow(() -> new ResourceNotFoundException("Payroll", "id", payrollId));
        List<PayrollItem> items = itemRepository.findByPayrollIdOrderByEmployeeIdAsc(payrollId);
        for (PayrollItem item : items) {
            if (!"PAYED".equals(item.getStatus())) {
                item.setStatus("PAYED");
                item.setPaidAt(LocalDateTime.now());
                itemRepository.save(item);
            }
        }
        payroll.setStatus("PAID");
        payrollRepository.save(payroll);
        return toResponse(payroll, items);
    }

    private PayrollItem calculateItem(Long payrollId, Employee employee, int month, int year, CompanySettings settings) {
        PayrollItem item = new PayrollItem();
        item.setPayrollId(payrollId);
        item.setEmployeeId(employee.getId());
        item.setBaseSalary(employee.getBaseSalary() != null ? employee.getBaseSalary() : BigDecimal.ZERO);
        item.setPrimeTransport(employee.getPrimeTransport() != null ? employee.getPrimeTransport() : BigDecimal.ZERO);
        item.setPrimePerformance(employee.getPrimePerformance() != null ? employee.getPrimePerformance() : BigDecimal.ZERO);
        item.setPrimeOther(employee.getPrimeOther() != null ? employee.getPrimeOther() : BigDecimal.ZERO);
        item.setStatus("PENDING");
        computeAndApply(item, employee, month, year, settings);
        return item;
    }

    private void recalculateItem(PayrollItem item, CompanySettings settings) {
        Employee employee = employeeRepository.findById(item.getEmployeeId()).orElse(null);
        if (employee == null) return;
        Payroll payroll = payrollRepository.findById(item.getPayrollId()).orElse(null);
        if (payroll == null) return;
        computeAndApply(item, employee, payroll.getMonth(), payroll.getYear(), settings);
    }

    private void computeAndApply(PayrollItem item, Employee employee, int month, int year, CompanySettings settings) {
        BigDecimal baseSalary = item.getBaseSalary();
        LocalDate start = LocalDate.of(year, month, 1);
        LocalDate end = start.withDayOfMonth(start.lengthOfMonth());

        BigDecimal monthlyHours = calculateMonthlyHoursFromSchedule(employee, settings);
        BigDecimal hourlyRate = monthlyHours.compareTo(BigDecimal.ZERO) > 0
                ? baseSalary.divide(monthlyHours, 4, RoundingMode.HALF_UP) : BigDecimal.ZERO;

        List<com.pointagepro.attendance.Attendance> records =
                attendanceRepository.findByMonth(employee.getId(), start, end);

        Set<LocalDate> approvedLeaveDates = getApprovedLeaveDates(employee.getId(), start, end);

        Set<LocalDate> attendanceDates = new java.util.HashSet<>();
        for (com.pointagepro.attendance.Attendance a : records) {
            attendanceDates.add(a.getDate());
        }

        BigDecimal totalWorkedHours = BigDecimal.ZERO;
        int totalLateMinutes = 0;
        BigDecimal totalMissingHours = BigDecimal.ZERO;
        BigDecimal totalOvertimeHours = BigDecimal.ZERO;

        for (com.pointagepro.attendance.Attendance a : records) {
            BigDecimal worked = a.getWorkedHours() != null ? a.getWorkedHours() : BigDecimal.ZERO;
            totalWorkedHours = totalWorkedHours.add(worked);

            int lateMin = a.getLateMinutes() != null ? a.getLateMinutes() : 0;
            int effectiveLate = Math.max(0, lateMin - settings.getLateGraceMinutes());
            totalLateMinutes += effectiveLate;

            String[] schedule = getScheduleForDay(employee, a.getDate().getDayOfWeek(), settings);
            BigDecimal expectedDay = scheduleHours(schedule[0], schedule[1]);

            if (worked.compareTo(BigDecimal.ZERO) > 0 && worked.compareTo(expectedDay) < 0) {
                totalMissingHours = totalMissingHours.add(expectedDay.subtract(worked));
            }
            if (worked.compareTo(expectedDay) > 0) {
                totalOvertimeHours = totalOvertimeHours.add(worked.subtract(expectedDay));
            }
        }

        int daysWorked = records.size();

        int totalWorkDays = countWorkDaysInRange(start, end, settings.getWorkDays());
        int absentDays = 0;
        BigDecimal totalAbsenceHours = BigDecimal.ZERO;

        LocalDate d = start;
        while (!d.isAfter(end)) {
            if (!attendanceDates.contains(d) && !approvedLeaveDates.contains(d)) {
                String dayKey = dayOfWeekToKey(d.getDayOfWeek());
                if (settings.getWorkDays().toUpperCase().contains(dayKey)) {
                    String[] schedule = getScheduleForDay(employee, d.getDayOfWeek(), settings);
                    BigDecimal expectedDay = scheduleHours(schedule[0], schedule[1]);
                    totalAbsenceHours = totalAbsenceHours.add(expectedDay);
                    absentDays++;
                }
            }
            d = d.plusDays(1);
        }

        BigDecimal lateHours = BigDecimal.valueOf(totalLateMinutes)
                .divide(BigDecimal.valueOf(60), 4, RoundingMode.HALF_UP);

        BigDecimal overtime = totalOvertimeHours;
        BigDecimal chainMissing = totalMissingHours.min(overtime);
        overtime = overtime.subtract(chainMissing);
        BigDecimal finalMissingHours = totalMissingHours.subtract(chainMissing);

        BigDecimal chainLate = lateHours.min(overtime);
        overtime = overtime.subtract(chainLate);
        BigDecimal finalLateHours = lateHours.subtract(chainLate);

        BigDecimal chainAbsent = totalAbsenceHours.min(overtime);
        overtime = overtime.subtract(chainAbsent);
        BigDecimal finalAbsenceHours = totalAbsenceHours.subtract(chainAbsent);

        int finalLateMinutes = finalLateHours.multiply(BigDecimal.valueOf(60))
                .setScale(0, RoundingMode.HALF_UP).intValue();

        BigDecimal lateDeduction = finalLateHours.multiply(hourlyRate).setScale(2, RoundingMode.HALF_UP);
        BigDecimal missingHoursDeduction = finalMissingHours.multiply(hourlyRate).setScale(2, RoundingMode.HALF_UP);
        BigDecimal absenceDeduction = finalAbsenceHours.multiply(hourlyRate).setScale(2, RoundingMode.HALF_UP);
        BigDecimal overtimeAmount = overtime.multiply(hourlyRate)
                .multiply(settings.getOvertimeRate()).setScale(2, RoundingMode.HALF_UP);

        BigDecimal primeTransport = item.getPrimeTransport() != null ? item.getPrimeTransport() : BigDecimal.ZERO;
        BigDecimal primePerformance = item.getPrimePerformance() != null ? item.getPrimePerformance() : BigDecimal.ZERO;
        BigDecimal primeOther = item.getPrimeOther() != null ? item.getPrimeOther() : BigDecimal.ZERO;
        BigDecimal totalPrimes = primeTransport.add(primePerformance).add(primeOther);

        BigDecimal totalGross = baseSalary.add(totalPrimes).add(overtimeAmount);

        BigDecimal cnssBase = baseSalary.min(settings.getCnssCeiling());
        BigDecimal cnssDeduction = cnssBase.multiply(settings.getCnssRate())
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        BigDecimal assuranceDeduction = baseSalary.multiply(settings.getAssuranceRate())
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        BigDecimal irDeduction = calculateIR(totalGross, cnssDeduction, settings);

        BigDecimal totalDeductions = cnssDeduction.add(assuranceDeduction).add(irDeduction)
                .add(lateDeduction).add(missingHoursDeduction).add(absenceDeduction);
        BigDecimal netSalary = totalGross.subtract(totalDeductions).max(BigDecimal.ZERO);

        item.setDaysWorked(daysWorked);
        item.setDaysAbsent(absentDays);
        item.setLateMinutes(totalLateMinutes);
        item.setTotalOvertimeMinutes(totalOvertimeHours.multiply(BigDecimal.valueOf(60)).intValue());
        item.setMissingHours(totalMissingHours);
        item.setAbsenceHours(totalAbsenceHours);
        item.setHourlyRate(hourlyRate);
        item.setOvertimeHours(overtime);
        item.setOvertimeAmount(overtimeAmount);
        item.setLateDeduction(lateDeduction);
        item.setMissingHoursDeduction(missingHoursDeduction);
        item.setAbsenceDeduction(absenceDeduction);
        item.setTotalGross(totalGross);
        item.setCnssDeduction(cnssDeduction);
        item.setAssuranceDeduction(assuranceDeduction);
        item.setIrDeduction(irDeduction);
        item.setTotalDeductions(totalDeductions);
        item.setNetSalary(netSalary);
    }

    private Set<LocalDate> getApprovedLeaveDates(Long employeeId, LocalDate start, LocalDate end) {
        List<LeaveRequest> approvedLeaves = leaveRequestRepository.findApprovedLeavesInRange(employeeId, start, end);
        Set<LocalDate> dates = new java.util.HashSet<>();
        for (LeaveRequest lr : approvedLeaves) {
            LocalDate ls = lr.getStartDate().isBefore(start) ? start : lr.getStartDate();
            LocalDate le = lr.getEndDate().isAfter(end) ? end : lr.getEndDate();
            LocalDate d = ls;
            while (!d.isAfter(le)) {
                dates.add(d);
                d = d.plusDays(1);
            }
        }
        return dates;
    }

    private BigDecimal scheduleHours(String startStr, String endStr) {
        LocalTime s = LocalTime.parse(startStr);
        LocalTime e = LocalTime.parse(endStr);
        long minutes = Duration.between(s, e).toMinutes();
        return BigDecimal.valueOf(minutes).divide(BigDecimal.valueOf(60), 4, RoundingMode.HALF_UP);
    }

    private String dayOfWeekToKey(java.time.DayOfWeek dow) {
        return switch (dow) {
            case MONDAY -> "LUN";
            case TUESDAY -> "MAR";
            case WEDNESDAY -> "MER";
            case THURSDAY -> "JEU";
            case FRIDAY -> "VEN";
            case SATURDAY -> "SAM";
            case SUNDAY -> "DIM";
        };
    }

    private int countWorkDaysInRange(LocalDate start, LocalDate end, String workDaysSetting) {
        Set<String> workDays = java.util.Arrays.stream(workDaysSetting.split(","))
                .map(String::trim).map(String::toUpperCase).collect(java.util.stream.Collectors.toSet());
        int count = 0;
        LocalDate current = start;
        while (!current.isAfter(end)) {
            if (workDays.contains(dayOfWeekToKey(current.getDayOfWeek()))) count++;
            current = current.plusDays(1);
        }
        return count;
    }

    private void recalculatePayrollTotals(Long payrollId) {
        Payroll payroll = payrollRepository.findById(payrollId).orElseThrow();
        List<PayrollItem> items = itemRepository.findByPayrollIdOrderByEmployeeIdAsc(payrollId);

        BigDecimal totalGross = BigDecimal.ZERO;
        BigDecimal totalDeductions = BigDecimal.ZERO;
        BigDecimal totalNet = BigDecimal.ZERO;

        for (PayrollItem item : items) {
            totalGross = totalGross.add(item.getTotalGross() != null ? item.getTotalGross() : BigDecimal.ZERO);
            totalDeductions = totalDeductions.add(item.getTotalDeductions() != null ? item.getTotalDeductions() : BigDecimal.ZERO);
            totalNet = totalNet.add(item.getNetSalary() != null ? item.getNetSalary() : BigDecimal.ZERO);
        }

        payroll.setTotalGross(totalGross);
        payroll.setTotalDeductions(totalDeductions);
        payroll.setTotalNet(totalNet);
        payroll.setEmployeeCount(items.size());
        payrollRepository.save(payroll);
    }

    private String[] getScheduleForDay(Employee employee, java.time.DayOfWeek dayOfWeek, CompanySettings settings) {
        String dayKey = switch (dayOfWeek) {
            case MONDAY -> "LUN";
            case TUESDAY -> "MAR";
            case WEDNESDAY -> "MER";
            case THURSDAY -> "JEU";
            case FRIDAY -> "VEN";
            case SATURDAY -> "SAM";
            case SUNDAY -> "DIM";
        };
        if (employee != null && employee.getWeeklySchedule() != null && !employee.getWeeklySchedule().isBlank()) {
            try {
                JsonNode root = objectMapper.readTree(employee.getWeeklySchedule());
                JsonNode dayNode = root.get(dayKey);
                if (dayNode != null && dayNode.has("start") && dayNode.has("end")) {
                    return new String[]{ dayNode.get("start").asText(), dayNode.get("end").asText() };
                }
            } catch (Exception e) {
                log.warn("Failed to parse weekly schedule for employee {}: {}", employee.getId(), e.getMessage());
            }
        }
        return new String[]{ settings.getWorkStartTime(), settings.getWorkEndTime() };
    }

    private BigDecimal calculateMonthlyHoursFromSchedule(Employee employee, CompanySettings settings) {
        if (employee != null && employee.getWeeklySchedule() != null && !employee.getWeeklySchedule().isBlank()) {
            try {
                JsonNode root = objectMapper.readTree(employee.getWeeklySchedule());
                BigDecimal weeklyHours = BigDecimal.ZERO;
                String[] dayKeys = {"LUN", "MAR", "MER", "JEU", "VEN", "SAM", "DIM"};
                for (String dayKey : dayKeys) {
                    JsonNode dayNode = root.get(dayKey);
                    if (dayNode != null && dayNode.has("start") && dayNode.has("end")) {
                        LocalTime start = LocalTime.parse(dayNode.get("start").asText());
                        LocalTime end = LocalTime.parse(dayNode.get("end").asText());
                        long dayMinutes = Duration.between(start, end).toMinutes();
                        weeklyHours = weeklyHours.add(BigDecimal.valueOf(dayMinutes)
                                .divide(BigDecimal.valueOf(60), 4, RoundingMode.HALF_UP));
                    }
                }
                if (weeklyHours.compareTo(BigDecimal.ZERO) > 0) {
                    return weeklyHours.multiply(BigDecimal.valueOf(52.0 / 12.0))
                            .setScale(2, RoundingMode.HALF_UP);
                }
            } catch (Exception e) {
                log.warn("Failed to parse weekly schedule for employee {}: {}", employee.getId(), e.getMessage());
            }
        }
        return settings.getMonthlyWorkHours();
    }

    private BigDecimal calculateIR(BigDecimal gross, BigDecimal cnss, CompanySettings settings) {
        BigDecimal annualGross = gross.multiply(BigDecimal.valueOf(12));
        BigDecimal annualCnss = cnss.multiply(BigDecimal.valueOf(12));
        BigDecimal annualTaxable = annualGross.subtract(annualCnss)
                .subtract(settings.getIrAbatement())
                .max(BigDecimal.ZERO);

        BigDecimal t1 = settings.getIrTranche1();
        BigDecimal t2 = settings.getIrTranche2();
        BigDecimal t3 = settings.getIrTranche3();
        BigDecimal t4 = settings.getIrTranche4();
        BigDecimal r1 = settings.getIrRate1();
        BigDecimal r2 = settings.getIrRate2();
        BigDecimal r3 = settings.getIrRate3();
        BigDecimal r4 = settings.getIrRate4();
        BigDecimal r5 = settings.getIrRate5();

        BigDecimal annualTax = BigDecimal.ZERO;
        BigDecimal remaining = annualTaxable;

        if (remaining.compareTo(t1) > 0) {
            BigDecimal taxable = t1.min(remaining);
            annualTax = annualTax.add(taxable.multiply(r1).divide(BigDecimal.valueOf(100)));
            remaining = remaining.subtract(taxable);
        }
        if (remaining.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal taxable = remaining.min(t2.subtract(t1));
            annualTax = annualTax.add(taxable.multiply(r2).divide(BigDecimal.valueOf(100)));
            remaining = remaining.subtract(taxable);
        }
        if (remaining.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal taxable = remaining.min(t3.subtract(t2));
            annualTax = annualTax.add(taxable.multiply(r3).divide(BigDecimal.valueOf(100)));
            remaining = remaining.subtract(taxable);
        }
        if (remaining.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal taxable = remaining.min(t4.subtract(t3));
            annualTax = annualTax.add(taxable.multiply(r4).divide(BigDecimal.valueOf(100)));
            remaining = remaining.subtract(taxable);
        }
        if (remaining.compareTo(BigDecimal.ZERO) > 0) {
            annualTax = annualTax.add(remaining.multiply(r5).divide(BigDecimal.valueOf(100)));
        }

        return annualTax.divide(BigDecimal.valueOf(12), 2, RoundingMode.HALF_UP);
    }

    public void addEmployeeToCurrentDraftPayroll(Employee employee) {
        if (!"ACTIF".equals(employee.getStatus()) && !"CONGE".equals(employee.getStatus())) return;

        Optional<Payroll> draftPayroll = payrollRepository.findByMonthAndYear(
                        LocalDate.now().getMonthValue(), LocalDate.now().getYear())
                .filter(p -> "DRAFT".equals(p.getStatus()));

        if (draftPayroll.isEmpty()) return;

        Payroll payroll = draftPayroll.get();
        List<PayrollItem> existing = itemRepository.findByPayrollIdAndEmployeeId(payroll.getId(), employee.getId());
        if (!existing.isEmpty()) return;

        CompanySettings settings = settingsService.get();
        PayrollItem item = calculateItem(payroll.getId(), employee, payroll.getMonth(), payroll.getYear(), settings);
        itemRepository.save(item);

        recalculatePayrollTotals(payroll.getId());

        log.info("New employee {} added to draft payroll {}/{}", employee.getId(), payroll.getMonth(), payroll.getYear());
    }

    public void updateEmployeeInDraftPayroll(Employee employee) {
        Optional<Payroll> draftPayroll = payrollRepository.findByMonthAndYear(
                        LocalDate.now().getMonthValue(), LocalDate.now().getYear())
                .filter(p -> "DRAFT".equals(p.getStatus()));

        if (draftPayroll.isEmpty()) return;

        Payroll payroll = draftPayroll.get();
        List<PayrollItem> existing = itemRepository.findByPayrollIdAndEmployeeId(payroll.getId(), employee.getId());

        if (existing.isEmpty()) {
            addEmployeeToCurrentDraftPayroll(employee);
            return;
        }

        PayrollItem item = existing.get(0);
        item.setBaseSalary(employee.getBaseSalary() != null ? employee.getBaseSalary() : BigDecimal.ZERO);
        item.setPrimeTransport(employee.getPrimeTransport() != null ? employee.getPrimeTransport() : BigDecimal.ZERO);
        item.setPrimePerformance(employee.getPrimePerformance() != null ? employee.getPrimePerformance() : BigDecimal.ZERO);
        item.setPrimeOther(employee.getPrimeOther() != null ? employee.getPrimeOther() : BigDecimal.ZERO);

        CompanySettings settings = settingsService.get();
        recalculateItem(item, settings);
        itemRepository.save(item);
        recalculatePayrollTotals(payroll.getId());

        log.info("Employee {} updated in draft payroll {}/{}", employee.getId(), payroll.getMonth(), payroll.getYear());
    }

    public void removeEmployeeFromDraftPayrolls(Long employeeId) {
        Optional<Payroll> draftPayroll = payrollRepository.findByMonthAndYear(
                        LocalDate.now().getMonthValue(), LocalDate.now().getYear())
                .filter(p -> "DRAFT".equals(p.getStatus()));

        if (draftPayroll.isEmpty()) return;

        Payroll payroll = draftPayroll.get();
        List<PayrollItem> existing = itemRepository.findByPayrollIdAndEmployeeId(payroll.getId(), employeeId);

        if (!existing.isEmpty()) {
            itemRepository.deleteAll(existing);
            recalculatePayrollTotals(payroll.getId());
            log.info("Employee {} removed from draft payroll {}/{}", employeeId, payroll.getMonth(), payroll.getYear());
        }
    }

    @Scheduled(cron = "0 0 0 1 * ?")
    public void autoGenerateMonthlyPayroll() {
        LocalDate now = LocalDate.now();
        int month = now.getMonthValue();
        int year = now.getYear();
        log.info("Auto-generating payroll for {}/{}", month, year);
        generate(month, year);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onStartupCheckPayroll() {
        LocalDate now = LocalDate.now();
        int month = now.getMonthValue();
        int year = now.getYear();
        if (!payrollRepository.existsByMonthAndYear(month, year)) {
            log.info("No payroll found for current month {}/{} — generating now", month, year);
            generate(month, year);
        }
    }

    @EventListener(AttendanceUpdatedEvent.class)
    public void onAttendanceUpdated(AttendanceUpdatedEvent event) {
        recalculateEmployeeInPayroll(event.getEmployeeId(), event.getMonth(), event.getYear());
    }

    private PayrollResponse toResponse(Payroll payroll, List<PayrollItem> items) {
        PayrollResponse response = new PayrollResponse();
        response.setId(payroll.getId());
        response.setMonth(payroll.getMonth());
        response.setYear(payroll.getYear());
        response.setStatus(payroll.getStatus());
        response.setTotalGross(payroll.getTotalGross());
        response.setTotalDeductions(payroll.getTotalDeductions());
        response.setTotalNet(payroll.getTotalNet());
        response.setEmployeeCount(payroll.getEmployeeCount());
        response.setCreatedAt(payroll.getCreatedAt());

        List<PayrollItemResponse> itemResponses = items.stream()
                .map(item -> {
                    Employee emp = employeeRepository.findById(item.getEmployeeId()).orElse(null);
                    return PayrollItemResponse.fromEntity(item, emp);
                })
                .toList();
        response.setItems(itemResponses);
        return response;
    }
}
