package com.pointagepro.dashboard;

import com.pointagepro.attendance.Attendance;
import com.pointagepro.attendance.AttendanceRepository;
import com.pointagepro.dashboard.dto.DashboardChart;
import com.pointagepro.dashboard.dto.DashboardStats;
import com.pointagepro.dashboard.dto.RecentAttendance;
import com.pointagepro.employee.Employee;
import com.pointagepro.employee.EmployeeRepository;
import com.pointagepro.leave.LeaveRequestRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class DashboardService {

    private final EmployeeRepository employeeRepository;
    private final AttendanceRepository attendanceRepository;
    private final LeaveRequestRepository leaveRequestRepository;

    public DashboardService(EmployeeRepository employeeRepository,
                            AttendanceRepository attendanceRepository,
                            LeaveRequestRepository leaveRequestRepository) {
        this.employeeRepository = employeeRepository;
        this.attendanceRepository = attendanceRepository;
        this.leaveRequestRepository = leaveRequestRepository;
    }

    public DashboardStats getStats() {
        LocalDate today = LocalDate.now();
        long totalEmployees = employeeRepository.count();
        List<Attendance> todayRecords = attendanceRepository.findByDateBetweenOrderByDateAsc(today, today);

        long presentToday = todayRecords.stream()
                .filter(a -> "PRESENT".equals(a.getStatus()) || "PARTIAL".equals(a.getStatus()))
                .count();
        long lateToday = todayRecords.stream()
                .filter(a -> a.getLateMinutes() != null && a.getLateMinutes() > 0)
                .count();
        long absentToday = totalEmployees - presentToday;
        if (absentToday < 0) absentToday = 0;

        long pendingLeaves = leaveRequestRepository.countPending();

        return new DashboardStats(totalEmployees, presentToday, absentToday, lateToday, pendingLeaves);
    }

    public DashboardChart getWeeklyChart() {
        LocalDate today = LocalDate.now();
        long totalEmployees = employeeRepository.count();

        List<String> labels = new ArrayList<>();
        List<Long> present = new ArrayList<>();
        List<Long> absent = new ArrayList<>();
        List<Long> late = new ArrayList<>();

        String[] dayAbbr = {"Dim", "Lun", "Mar", "Mer", "Jeu", "Ven", "Sam"};
        String[] monthAbbr = {"Janv", "Févr", "Mars", "Avr", "Mai", "Juin", "Juil", "Août", "Sept", "Oct", "Nov", "Déc"};

        for (int i = 0; i < 7; i++) {
            LocalDate day = today.minusDays(6 - i);
            int dayOfWeek = day.getDayOfWeek().getValue() % 7;
            String label = dayAbbr[dayOfWeek] + "\n" + day.getDayOfMonth() + " " + monthAbbr[day.getMonthValue() - 1];
            labels.add(label);

            List<Attendance> dayRecords = attendanceRepository.findByDateBetweenOrderByDateAsc(day, day);
            long dayPresent = dayRecords.stream()
                    .filter(a -> "PRESENT".equals(a.getStatus()) || "PARTIAL".equals(a.getStatus()))
                    .count();
            long dayLate = dayRecords.stream()
                    .filter(a -> a.getLateMinutes() != null && a.getLateMinutes() > 0)
                    .count();
            present.add(dayPresent);
            absent.add(Math.max(0, totalEmployees - dayPresent));
            late.add(dayLate);
        }

        return new DashboardChart(labels, present, absent, late, "Derniers 7 jours", (int) totalEmployees);
    }

    public List<RecentAttendance> getRecentAttendance() {
        LocalDate today = LocalDate.now();
        List<Attendance> records = attendanceRepository.findByDateBetweenOrderByDateAsc(today, today);
        List<Long> employeeIds = records.stream().map(Attendance::getEmployeeId).distinct().toList();
        Map<Long, Employee> employeeMap = employeeRepository.findAllById(employeeIds).stream()
                .collect(Collectors.toMap(Employee::getId, e -> e));

        String[] colors = {"#2563EB", "#10B981", "#F59E0B", "#EF4444", "#8B5CF6", "#EC4899", "#06B6D4", "#F97316"};

        List<RecentAttendance> result = new ArrayList<>();
        for (Attendance record : records) {
            Employee emp = employeeMap.get(record.getEmployeeId());
            if (emp == null) continue;

            RecentAttendance ra = new RecentAttendance();
            ra.setEmployeeId(emp.getId());
            ra.setFirstName(emp.getFirstName());
            ra.setLastName(emp.getLastName());
            ra.setPosition(emp.getPosition());
            ra.setPhoto(emp.getPhoto());
            ra.setInitials((emp.getFirstName().substring(0, 1) + emp.getLastName().substring(0, 1)).toUpperCase());
            ra.setAvatarColor(colors[(int) (emp.getId() % colors.length)]);
            ra.setCheckIn(record.getCheckIn() != null ? record.getCheckIn().toLocalTime() : null);
            ra.setCheckOut(record.getCheckOut() != null ? record.getCheckOut().toLocalTime() : null);
            ra.setWorkedHours(record.getWorkedHours() != null ? record.getWorkedHours().doubleValue() : 0);
            ra.setStatus(record.getStatus());
            result.add(ra);
        }

        result.sort((a, b) -> {
            if (a.getCheckIn() == null) return 1;
            if (b.getCheckIn() == null) return -1;
            return b.getCheckIn().compareTo(a.getCheckIn());
        });

        return result.stream().limit(10).toList();
    }
}
