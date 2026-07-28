package com.pointagepro.attendance;

import com.pointagepro.attendance.dto.AttendanceMonthlySummary;
import com.pointagepro.attendance.dto.AttendanceRecord;
import com.pointagepro.employee.Employee;
import com.pointagepro.employee.EmployeeRepository;
import com.pointagepro.settings.CompanySettings;
import com.pointagepro.settings.CompanySettingsService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Transactional
public class AttendanceService {

    private static final Logger log = LoggerFactory.getLogger(AttendanceService.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final AttendanceRepository repository;
    private final CompanySettingsService settingsService;
    private final EmployeeRepository employeeRepository;
    private final ApplicationEventPublisher eventPublisher;

    public AttendanceService(AttendanceRepository repository, CompanySettingsService settingsService,
                             EmployeeRepository employeeRepository, ApplicationEventPublisher eventPublisher) {
        this.repository = repository;
        this.settingsService = settingsService;
        this.employeeRepository = employeeRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional(readOnly = true)
    public List<AttendanceRecord> getByEmployeeAndMonth(Long employeeId, int month, int year) {
        YearMonth ym = YearMonth.of(year, month);
        LocalDate start = ym.atDay(1);
        LocalDate end = ym.atEndOfMonth();
        return repository.findByMonth(employeeId, start, end).stream()
                .map(AttendanceRecord::fromEntity)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AttendanceRecord> getByMonth(int month, int year) {
        YearMonth ym = YearMonth.of(year, month);
        LocalDate start = ym.atDay(1);
        LocalDate end = ym.atEndOfMonth();
        return repository.findByDateBetweenOrderByDateAsc(start, end).stream()
                .map(AttendanceRecord::fromEntity)
                .toList();
    }

    @Transactional(readOnly = true)
    public AttendanceMonthlySummary getMonthlySummary(Long employeeId, int month, int year) {
        YearMonth ym = YearMonth.of(year, month);
        LocalDate start = ym.atDay(1);
        LocalDate end = ym.atEndOfMonth();

        BigDecimal overtimeHours = repository.sumOvertimeHours(employeeId, start, end);
        Integer lateMinutes = repository.sumLateMinutes(employeeId, start, end);
        Integer daysWorked = repository.countDaysWorked(employeeId, start, end);
        Integer daysAbsent = repository.countDaysAbsent(employeeId, start, end);
        CompanySettings settings = settingsService.get();
        int totalWorkDays = countWorkDaysInRange(start, end, settings.getWorkDays());

        return new AttendanceMonthlySummary(
                employeeId, month, year,
                overtimeHours != null ? overtimeHours : BigDecimal.ZERO,
                lateMinutes != null ? lateMinutes : 0,
                daysWorked != null ? daysWorked : 0,
                daysAbsent != null ? daysAbsent : 0,
                totalWorkDays
        );
    }

    public Attendance recordCheckIn(Long employeeId, LocalDate date) {
        return recordCheckIn(employeeId, date, LocalTime.now());
    }

    public Attendance recordCheckIn(Long employeeId, LocalDate date, LocalTime scanTime) {
        CompanySettings settings = settingsService.get();
        Employee employee = employeeRepository.findById(employeeId).orElse(null);

        String[] schedule = getScheduleForDay(employee, date.getDayOfWeek(), settings);
        LocalTime workStart = LocalTime.parse(schedule[0]);
        LocalTime now = scanTime;

        Attendance attendance = repository.findByEmployeeIdAndDateBetweenOrderByDateAsc(
                employeeId, date, date).stream().findFirst().orElseGet(() -> {
            Attendance a = new Attendance();
            a.setEmployeeId(employeeId);
            a.setDate(date);
            a.setStatus("PRESENT");
            return a;
        });

        attendance.setCheckIn(date.atTime(now));

        int lateMinutes = 0;
        if (now.isAfter(workStart)) {
            lateMinutes = (int) java.time.Duration.between(workStart, now).toMinutes();
        }
        attendance.setLateMinutes(lateMinutes);

        Attendance saved = repository.save(attendance);
        eventPublisher.publishEvent(new AttendanceUpdatedEvent(this, employeeId, date.getMonthValue(), date.getYear()));
        return saved;
    }

    public Attendance recordCheckOut(Long employeeId, LocalDate date) {
        return recordCheckOut(employeeId, date, LocalTime.now());
    }

    public Attendance recordCheckOut(Long employeeId, LocalDate date, LocalTime scanTime) {
        Attendance attendance = repository.findByEmployeeIdAndDateBetweenOrderByDateAsc(
                employeeId, date, date).stream().findFirst().orElseThrow(
                () -> new RuntimeException("No check-in found for employee " + employeeId + " on " + date));

        CompanySettings settings = settingsService.get();
        Employee employee = employeeRepository.findById(employeeId).orElse(null);
        attendance.setCheckOut(date.atTime(scanTime));

        if (attendance.getCheckIn() != null) {
            String[] schedule = getScheduleForDay(employee, date.getDayOfWeek(), settings);
            LocalTime workEnd = LocalTime.parse(schedule[1]);
            LocalTime workStart = LocalTime.parse(schedule[0]);

            LocalTime actualCheckIn = attendance.getCheckIn().toLocalTime();
            long actualDelayMinutes = java.time.Duration.between(workStart, actualCheckIn).toMinutes();

            LocalTime effectiveStart = (actualDelayMinutes <= settings.getLateGraceMinutes())
                    ? workStart
                    : actualCheckIn;

            long totalMinutes = java.time.Duration.between(effectiveStart, attendance.getCheckOut().toLocalTime()).toMinutes();
            BigDecimal workedHours = BigDecimal.valueOf(totalMinutes).divide(BigDecimal.valueOf(60), 2, java.math.RoundingMode.HALF_UP);
            attendance.setWorkedHours(workedHours);

            BigDecimal dailyHours = BigDecimal.valueOf(java.time.Duration.between(workStart, workEnd).toMinutes())
                    .divide(BigDecimal.valueOf(60), 2, java.math.RoundingMode.HALF_UP);

            BigDecimal overtime = workedHours.subtract(dailyHours).max(BigDecimal.ZERO).setScale(2, java.math.RoundingMode.HALF_UP);
            attendance.setOvertimeHours(overtime);

            if (attendance.getWorkedHours().compareTo(BigDecimal.ZERO) > 0 && attendance.getWorkedHours().compareTo(dailyHours) < 0) {
                attendance.setStatus("PARTIAL");
            }
        }

        Attendance saved = repository.save(attendance);
        eventPublisher.publishEvent(new AttendanceUpdatedEvent(this, employeeId, date.getMonthValue(), date.getYear()));
        return saved;
    }

    private String[] getScheduleForDay(Employee employee, DayOfWeek dayOfWeek, CompanySettings settings) {
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

    private int countWorkDaysInRange(LocalDate start, LocalDate end, String workDaysSetting) {
        Set<String> workDays = Arrays.stream(workDaysSetting.split(","))
                .map(String::trim).map(String::toUpperCase).collect(Collectors.toSet());
        int count = 0;
        LocalDate current = start;
        while (!current.isAfter(end)) {
            String dayName = switch (current.getDayOfWeek()) {
                case MONDAY -> "LUN";
                case TUESDAY -> "MAR";
                case WEDNESDAY -> "MER";
                case THURSDAY -> "JEU";
                case FRIDAY -> "VEN";
                case SATURDAY -> "SAM";
                case SUNDAY -> "DIM";
            };
            if (workDays.contains(dayName)) count++;
            current = current.plusDays(1);
        }
        return count;
    }
}
