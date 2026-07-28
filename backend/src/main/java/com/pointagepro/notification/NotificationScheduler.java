package com.pointagepro.notification;

import com.pointagepro.attendance.AttendanceRepository;
import com.pointagepro.auth.LoginHistoryRepository;
import com.pointagepro.auth.UserSessionRepository;
import com.pointagepro.employee.EmployeeRepository;
import com.pointagepro.esp32.TerminalStatus;
import com.pointagepro.esp32.TerminalStatusRepository;
import com.pointagepro.payroll.Payroll;
import com.pointagepro.payroll.PayrollRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class NotificationScheduler {

    private static final Logger log = LoggerFactory.getLogger(NotificationScheduler.class);
    private static final Map<String, Boolean> terminalWasOnline = new HashMap<>();

    private final NotificationService notificationService;
    private final TerminalStatusRepository terminalStatusRepository;
    private final EmployeeRepository employeeRepository;
    private final AttendanceRepository attendanceRepository;
    private final PayrollRepository payrollRepository;
    private final LoginHistoryRepository loginHistoryRepository;
    private final UserSessionRepository sessionRepository;

    public NotificationScheduler(NotificationService notificationService,
                                  TerminalStatusRepository terminalStatusRepository,
                                  EmployeeRepository employeeRepository,
                                  AttendanceRepository attendanceRepository,
                                  PayrollRepository payrollRepository,
                                  LoginHistoryRepository loginHistoryRepository,
                                  UserSessionRepository sessionRepository) {
        this.notificationService = notificationService;
        this.terminalStatusRepository = terminalStatusRepository;
        this.employeeRepository = employeeRepository;
        this.attendanceRepository = attendanceRepository;
        this.payrollRepository = payrollRepository;
        this.loginHistoryRepository = loginHistoryRepository;
        this.sessionRepository = sessionRepository;
    }

    @Scheduled(cron = "0 */2 * * * *")
    public void checkTerminalStatus() {
        List<TerminalStatus> terminals = terminalStatusRepository.findAll();
        for (TerminalStatus t : terminals) {
            if (t.getLastHeartbeat() == null) continue;
            boolean isOnline = t.getLastHeartbeat().isAfter(LocalDateTime.now().minusSeconds(90));
            Boolean wasOnline = terminalWasOnline.getOrDefault(t.getDeviceId(), true);

            if (!isOnline && wasOnline) {
                terminalWasOnline.put(t.getDeviceId(), false);
                notificationService.notify("TERMINAL_OFFLINE", "Terminal hors ligne",
                    t.getDeviceName() + " (" + t.getDeviceId() + ") déconnecté", "HIGH",
                    "TERMINAL", t.getId());
            } else if (isOnline && !wasOnline) {
                terminalWasOnline.put(t.getDeviceId(), true);
                notificationService.notify("TERMINAL_ONLINE", "Terminal reconnecté",
                    t.getDeviceName() + " (" + t.getDeviceId() + ") de nouveau en ligne", "MEDIUM",
                    "TERMINAL", t.getId());
            }
        }
    }

    @Scheduled(cron = "0 55 22 * * ?")
    public void dailySummary() {
        LocalDate today = LocalDate.now();
        Integer presentToday = attendanceRepository.countDistinctEmployeesPresent(today, today);
        long presentCount = presentToday != null ? presentToday : 0;

        long onLeaveCount = employeeRepository.findByStatus("CONGE").size();
        long totalEmployees = employeeRepository.count();

        long absentToday = totalEmployees - presentCount - onLeaveCount;
        if (absentToday < 0) absentToday = 0;

        Integer lateMinutes = attendanceRepository.sumAllLateMinutes(today, today);
        int lateCount = (lateMinutes != null && lateMinutes > 0) ? 1 : 0;

        notificationService.notify("DAILY_SUMMARY", "Résumé du jour",
            presentCount + " présent(s), " + absentToday + " absent(s), " +
            lateCount + " retard(s), " + onLeaveCount + " en congé",
            "MEDIUM");
    }

    @Scheduled(cron = "0 0 8 ? * MON")
    public void weeklySummary() {
        LocalDate now = LocalDate.now();
        LocalDate weekStart = now.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));

        Integer lateMinutes = attendanceRepository.sumAllLateMinutes(weekStart, now);
        int totalLate = lateMinutes != null ? lateMinutes : 0;

        Integer daysWorked = attendanceRepository.countDistinctEmployeesPresent(weekStart, now);
        int totalWorked = daysWorked != null ? daysWorked : 0;

        int weekNumber = now.get(java.time.temporal.IsoFields.WEEK_OF_WEEK_BASED_YEAR);

        notificationService.notify("WEEKLY_SUMMARY", "Résumé hebdomadaire",
            "Semaine " + weekNumber + " : " + totalWorked + " jour(s) pointé(s), " +
            totalLate + " min de retard total",
            "MEDIUM");
    }

    @Scheduled(cron = "0 0 9 1 * ?")
    public void monthlySummary() {
        LocalDate now = LocalDate.now();
        int month = now.getMonthValue();
        int year = now.getYear();
        LocalDate start = LocalDate.of(year, month, 1);
        LocalDate end = start.withDayOfMonth(start.lengthOfMonth());

        String[] monthNames = {"", "Janvier", "Février", "Mars", "Avril", "Mai", "Juin",
            "Juillet", "Août", "Septembre", "Octobre", "Novembre", "Décembre"};

        long totalEmployees = employeeRepository.count();
        Integer daysWorked = attendanceRepository.countDistinctEmployeesPresent(start, end);
        int totalWorked = daysWorked != null ? daysWorked : 0;

        String payrollInfo = "";
        var payrollOpt = payrollRepository.findByMonthAndYear(month, year);
        if (payrollOpt.isPresent()) {
            Payroll p = payrollOpt.get();
            payrollInfo = " — Masse: " + p.getTotalNet() + " DT";
        }

        notificationService.notify("MONTHLY_SUMMARY", "Résumé mensuel",
            monthNames[month] + " " + year + " : " + totalEmployees + " employés, " +
            totalWorked + " jour(s) pointé(s)" + payrollInfo,
            "MEDIUM");
    }

    @Scheduled(cron = "0 0 4 * * ?")
    public void cleanupOldSessionsAndHistory() {
        try {
            java.time.LocalDateTime cutoff = java.time.LocalDateTime.now().minusDays(30);
            sessionRepository.deleteByCreatedAtBefore(cutoff);
            loginHistoryRepository.deleteByAttemptedAtBefore(cutoff);
            log.info("Cleaned up old sessions and login history older than 30 days");
        } catch (Exception e) {
            log.error("Error during session/history cleanup: {}", e.getMessage());
        }
    }
}
