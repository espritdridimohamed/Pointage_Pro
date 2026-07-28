package com.pointagepro.reports;

import com.pointagepro.attendance.Attendance;
import com.pointagepro.attendance.AttendanceRepository;
import com.pointagepro.employee.Employee;
import com.pointagepro.employee.EmployeeRepository;
import com.pointagepro.leave.LeaveRequest;
import com.pointagepro.leave.LeaveRequestRepository;
import com.pointagepro.payroll.Payroll;
import com.pointagepro.payroll.PayrollRepository;
import com.pointagepro.reports.dto.EmployeeAttendanceStats;
import com.pointagepro.reports.dto.ReportResponse;
import com.pointagepro.settings.CompanySettings;
import com.pointagepro.settings.CompanySettingsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class ReportsService {

    private static final Logger log = LoggerFactory.getLogger(ReportsService.class);

    private final AttendanceRepository attendanceRepository;
    private final EmployeeRepository employeeRepository;
    private final LeaveRequestRepository leaveRequestRepository;
    private final PayrollRepository payrollRepository;
    private final CompanySettingsService settingsService;

    public ReportsService(AttendanceRepository attendanceRepository,
                          EmployeeRepository employeeRepository,
                          LeaveRequestRepository leaveRequestRepository,
                          PayrollRepository payrollRepository,
                          CompanySettingsService settingsService) {
        this.attendanceRepository = attendanceRepository;
        this.employeeRepository = employeeRepository;
        this.leaveRequestRepository = leaveRequestRepository;
        this.payrollRepository = payrollRepository;
        this.settingsService = settingsService;
    }

    public ReportResponse getMonthlyReport(int month, int year) {
        YearMonth ym = YearMonth.of(year, month);
        LocalDate monthStart = ym.atDay(1);
        LocalDate monthEnd = ym.atEndOfMonth();
        CompanySettings settings = settingsService.get();
        long activeEmployeeCount = employeeRepository.findByStatusIn(List.of("ACTIF", "CONGE")).size();

        List<String> weekLabels = new ArrayList<>();
        List<Integer> presence = new ArrayList<>();
        List<Integer> retards = new ArrayList<>();
        List<Double> overtimeHours = new ArrayList<>();

        LocalDate weekStart = monthStart;
        int weekNum = 1;
        while (!weekStart.isAfter(monthEnd)) {
            LocalDate weekEnd = weekStart.plusDays(6);
            if (weekEnd.isAfter(monthEnd)) weekEnd = monthEnd;

            weekLabels.add("Sem " + weekNum);

            List<Attendance> weekRecords = attendanceRepository.findByDateBetweenOrderByDateAsc(weekStart, weekEnd);
            long workDaysThisWeek = countWorkDays(weekStart, weekEnd, settings.getWorkDays());
            int totalSlots = (int) (activeEmployeeCount * workDaysThisWeek);
            int presentCount = (int) weekRecords.stream()
                    .filter(a -> "PRESENT".equals(a.getStatus()) || "PARTIAL".equals(a.getStatus()))
                    .count();
            int presenceRate = totalSlots > 0 ? Math.round((float) presentCount / totalSlots * 100) : 0;
            presence.add(presenceRate);

            int lateCount = (int) weekRecords.stream()
                    .filter(a -> a.getLateMinutes() != null && a.getLateMinutes() > 0)
                    .count();
            retards.add(lateCount);

            double weekOvertime = weekRecords.stream()
                    .filter(a -> a.getOvertimeHours() != null)
                    .mapToDouble(a -> a.getOvertimeHours().doubleValue())
                    .sum();
            overtimeHours.add(Math.round(weekOvertime * 10.0) / 10.0);

            weekStart = weekEnd.plusDays(1);
            weekNum++;
        }

        ReportResponse report = new ReportResponse();
        report.setLabels(weekLabels);
        report.setPresence(presence);
        report.setRetards(retards);
        report.setMasse(List.of());
        report.setOvertimeHours(overtimeHours);
        report.setTotalEmployees((int) activeEmployeeCount);

        report.setAbsences(buildAbsenceBreakdown(monthStart, monthEnd));
        report.setEmployeeStats(buildEmployeeStats(monthStart, monthEnd));

        return report;
    }

    public ReportResponse getAnnualReport(int year) {
        CompanySettings settings = settingsService.get();
        long activeEmployeeCount = employeeRepository.findByStatusIn(List.of("ACTIF", "CONGE")).size();
        String[] monthLabels = {"Jan", "Fév", "Mar", "Avr", "Mai", "Jun", "Jul", "Aoû", "Sep", "Oct", "Nov", "Déc"};

        List<String> labels = new ArrayList<>(List.of(monthLabels));
        List<Integer> presence = new ArrayList<>();
        List<Integer> retards = new ArrayList<>();
        List<Long> masse = new ArrayList<>();
        List<Double> overtimeHours = new ArrayList<>();

        for (int m = 1; m <= 12; m++) {
            YearMonth ym = YearMonth.of(year, m);
            LocalDate monthStart = ym.atDay(1);
            LocalDate monthEnd = ym.atEndOfMonth();

            List<Attendance> monthRecords = attendanceRepository.findByDateBetweenOrderByDateAsc(monthStart, monthEnd);
            long workDaysThisMonth = countWorkDays(monthStart, monthEnd, settings.getWorkDays());
            int totalSlots = (int) (activeEmployeeCount * workDaysThisMonth);
            int presentCount = (int) monthRecords.stream()
                    .filter(a -> "PRESENT".equals(a.getStatus()) || "PARTIAL".equals(a.getStatus()))
                    .count();
            int presenceRate = totalSlots > 0 ? Math.round((float) presentCount / totalSlots * 100) : 0;
            presence.add(presenceRate);

            int lateCount = (int) monthRecords.stream()
                    .filter(a -> a.getLateMinutes() != null && a.getLateMinutes() > 0)
                    .count();
            retards.add(lateCount);

            double monthOvertime = monthRecords.stream()
                    .filter(a -> a.getOvertimeHours() != null)
                    .mapToDouble(a -> a.getOvertimeHours().doubleValue())
                    .sum();
            overtimeHours.add(Math.round(monthOvertime * 10.0) / 10.0);

            Optional<Payroll> payrollOpt = payrollRepository.findByMonthAndYear(m, year);
            if (payrollOpt.isPresent() && payrollOpt.get().getTotalGross() != null) {
                masse.add(payrollOpt.get().getTotalGross().longValue());
            } else {
                masse.add(0L);
            }
        }

        ReportResponse report = new ReportResponse();
        report.setLabels(labels);
        report.setPresence(presence);
        report.setRetards(retards);
        report.setMasse(masse);
        report.setOvertimeHours(overtimeHours);
        report.setTotalEmployees((int) activeEmployeeCount);

        LocalDate yearStart = YearMonth.of(year, 1).atDay(1);
        LocalDate yearEnd = YearMonth.of(year, 12).atEndOfMonth();
        report.setAbsences(buildAbsenceBreakdown(yearStart, yearEnd));
        report.setEmployeeStats(buildEmployeeStats(yearStart, yearEnd));

        return report;
    }

    private List<ReportResponse.AbsenceBreakdown> buildAbsenceBreakdown(LocalDate start, LocalDate end) {
        List<ReportResponse.AbsenceBreakdown> absences = new ArrayList<>();
        absences.add(new ReportResponse.AbsenceBreakdown("Congé Annuel",
                leaveRequestRepository.sumApprovedDaysByRangeAndType(start, end, "Congé Annuel"), "#2563EB"));
        absences.add(new ReportResponse.AbsenceBreakdown("Congé Maladie",
                leaveRequestRepository.sumApprovedDaysByRangeAndType(start, end, "Congé Maladie"), "#10B981"));
        absences.add(new ReportResponse.AbsenceBreakdown("Congé Maternité",
                leaveRequestRepository.sumApprovedDaysByRangeAndType(start, end, "Congé Maternité"), "#8B5CF6"));
        absences.add(new ReportResponse.AbsenceBreakdown("Congé Paternité",
                leaveRequestRepository.sumApprovedDaysByRangeAndType(start, end, "Congé Paternité"), "#06B6D4"));
        absences.add(new ReportResponse.AbsenceBreakdown("Formation",
                leaveRequestRepository.sumApprovedDaysByRangeAndType(start, end, "Formation"), "#EC4899"));
        absences.add(new ReportResponse.AbsenceBreakdown("Congé Sans Solde",
                leaveRequestRepository.sumApprovedDaysByRangeAndType(start, end, "Congé Sans Solde"), "#F59E0B"));
        absences.add(new ReportResponse.AbsenceBreakdown("Absence injustifiée",
                countUnjustifiedAbsences(start, end), "#EF4444"));
        return absences;
    }

    private List<EmployeeAttendanceStats> buildEmployeeStats(LocalDate start, LocalDate end) {
        List<Employee> employees = employeeRepository.findByStatusIn(List.of("ACTIF", "CONGE"));
        CompanySettings settings = settingsService.get();
        List<LeaveRequest> allApprovedLeaves = leaveRequestRepository.findAllApprovedLeavesInRange(start, end);
        Set<LocalDate> approvedLeaveDates = buildApprovedLeaveDateSet(allApprovedLeaves, start, end);

        List<EmployeeAttendanceStats> stats = new ArrayList<>();

        for (Employee emp : employees) {
            List<Attendance> empRecords = attendanceRepository.findByEmployeeIdAndDateBetweenOrderByDateAsc(
                    emp.getId(), start, end);

            Set<LocalDate> attendanceDates = empRecords.stream()
                    .map(Attendance::getDate)
                    .collect(Collectors.toSet());

            int present = (int) empRecords.stream()
                    .filter(a -> "PRESENT".equals(a.getStatus()) || "PARTIAL".equals(a.getStatus()))
                    .count();
            int late = (int) empRecords.stream()
                    .filter(a -> a.getLateMinutes() != null && a.getLateMinutes() > 0)
                    .count();
            double overtime = empRecords.stream()
                    .filter(a -> a.getOvertimeHours() != null)
                    .mapToDouble(a -> a.getOvertimeHours().doubleValue())
                    .sum();

            long workDays = countWorkDaysInRangeForEmployee(emp, start, end, settings);
            long empApprovedLeaveDays = countEmployeeApprovedLeaveDays(emp.getId(), start, end);
            int absent = (int) Math.max(0, workDays - present - empApprovedLeaveDays);

            EmployeeAttendanceStats s = new EmployeeAttendanceStats();
            s.setEmployeeId(emp.getId());
            s.setFirstName(emp.getFirstName());
            s.setLastName(emp.getLastName());
            s.setDepartment(emp.getDepartment());
            s.setDaysPresent(present);
            s.setDaysLate(late);
            s.setDaysAbsent(absent);
            s.setOvertimeHours(Math.round(overtime * 10.0) / 10.0);
            stats.add(s);
        }

        stats.sort(Comparator.comparingInt(EmployeeAttendanceStats::getDaysLate).reversed());
        return stats;
    }

    private Set<LocalDate> buildApprovedLeaveDateSet(List<LeaveRequest> approvedLeaves,
                                                      LocalDate rangeStart, LocalDate rangeEnd) {
        Set<LocalDate> dates = new HashSet<>();
        for (LeaveRequest lr : approvedLeaves) {
            LocalDate lrStart = lr.getStartDate().isBefore(rangeStart) ? rangeStart : lr.getStartDate();
            LocalDate lrEnd = lr.getEndDate().isAfter(rangeEnd) ? rangeEnd : lr.getEndDate();
            LocalDate d = lrStart;
            while (!d.isAfter(lrEnd)) {
                dates.add(d);
                d = d.plusDays(1);
            }
        }
        return dates;
    }

    private long countUnjustifiedAbsences(LocalDate start, LocalDate end) {
        List<Employee> activeEmployees = employeeRepository.findByStatusIn(List.of("ACTIF", "CONGE"));
        List<LeaveRequest> allApprovedLeaves = leaveRequestRepository.findAllApprovedLeavesInRange(start, end);
        Set<LocalDate> approvedLeaveDates = buildApprovedLeaveDateSet(allApprovedLeaves, start, end);

        List<Attendance> allRecords = attendanceRepository.findByDateBetweenOrderByDateAsc(start, end);
        Map<LocalDate, Set<Long>> presentByDate = new HashMap<>();
        for (Attendance a : allRecords) {
            presentByDate.computeIfAbsent(a.getDate(), k -> new HashSet<>()).add(a.getEmployeeId());
        }

        long unjustifiedCount = 0;
        LocalDate d = start;
        while (!d.isAfter(end)) {
            String dayKey = dayOfWeekToKey(d.getDayOfWeek());
            if (settingsService.get().getWorkDays().toUpperCase().contains(dayKey)) {
                for (Employee emp : activeEmployees) {
                    boolean hasAttendance = presentByDate.containsKey(d) && presentByDate.get(d).contains(emp.getId());
                    boolean hasApprovedLeave = approvedLeaveDates.contains(d);
                    if (!hasAttendance && !hasApprovedLeave) {
                        unjustifiedCount++;
                    }
                }
            }
            d = d.plusDays(1);
        }
        return unjustifiedCount;
    }

    private long countEmployeeApprovedLeaveDays(Long employeeId, LocalDate start, LocalDate end) {
        List<LeaveRequest> leaves = leaveRequestRepository.findApprovedLeavesInRange(employeeId, start, end);
        long total = 0;
        for (LeaveRequest lr : leaves) {
            LocalDate lrStart = lr.getStartDate().isBefore(start) ? start : lr.getStartDate();
            LocalDate lrEnd = lr.getEndDate().isAfter(end) ? end : lr.getEndDate();
            total += java.time.temporal.ChronoUnit.DAYS.between(lrStart, lrEnd) + 1;
        }
        return total;
    }

    private long countWorkDaysInRangeForEmployee(Employee emp, LocalDate start, LocalDate end, CompanySettings settings) {
        long count = 0;
        LocalDate current = start;
        while (!current.isAfter(end)) {
            String dayKey = dayOfWeekToKey(current.getDayOfWeek());
            if (settings.getWorkDays().toUpperCase().contains(dayKey)) {
                count++;
            }
            current = current.plusDays(1);
        }
        return count;
    }

    private long countWorkDays(LocalDate start, LocalDate end, String workDaysSetting) {
        Set<String> workDays = Arrays.stream(workDaysSetting.split(","))
                .map(String::trim).map(String::toUpperCase).collect(Collectors.toSet());
        long count = 0;
        LocalDate current = start;
        while (!current.isAfter(end)) {
            String dayName = dayOfWeekToKey(current.getDayOfWeek());
            if (workDays.contains(dayName)) count++;
            current = current.plusDays(1);
        }
        return count;
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
}
