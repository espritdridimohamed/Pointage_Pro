package com.pointagepro.esp32;

import com.pointagepro.attendance.Attendance;
import com.pointagepro.attendance.AttendanceRepository;
import com.pointagepro.attendance.AttendanceService;
import com.pointagepro.employee.Employee;
import com.pointagepro.employee.EmployeeRepository;
import com.pointagepro.esp32.dto.HeartbeatRequest;
import com.pointagepro.esp32.dto.ScanRequest;
import com.pointagepro.esp32.dto.ScanResponse;
import com.pointagepro.notification.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/esp32")
public class Esp32Controller {

    private static final Logger log = LoggerFactory.getLogger(Esp32Controller.class);
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");

    @Value("${esp32.api-key:pointagepro-esp32-device-key-2026}")
    private String expectedApiKey;

    private final EmployeeRepository employeeRepository;
    private final AttendanceService attendanceService;
    private final AttendanceRepository attendanceRepository;
    private final TerminalStatusRepository terminalStatusRepository;
    private final ScanEventRepository scanEventRepository;
    private final NotificationService notificationService;

    public Esp32Controller(EmployeeRepository employeeRepository,
                           AttendanceService attendanceService,
                           AttendanceRepository attendanceRepository,
                           TerminalStatusRepository terminalStatusRepository,
                           ScanEventRepository scanEventRepository,
                           NotificationService notificationService) {
        this.employeeRepository = employeeRepository;
        this.attendanceService = attendanceService;
        this.attendanceRepository = attendanceRepository;
        this.terminalStatusRepository = terminalStatusRepository;
        this.scanEventRepository = scanEventRepository;
        this.notificationService = notificationService;
    }

    @PostMapping("/scan")
    public ResponseEntity<ScanResponse> scan(@RequestBody ScanRequest request,
                                             @RequestHeader(value = "X-API-Key", required = false) String apiKey) {
        if (apiKey == null || !apiKey.equals(expectedApiKey)) {
            log.warn("Invalid API key from ESP32");
            return ResponseEntity.status(401).body(
                new ScanResponse(false, null, null, null, "Clé API invalide", null));
        }

        String uid = request.getRfidUid().trim().toUpperCase();
        log.info("RFID scan: {}", uid);

        Optional<Employee> employeeOpt = employeeRepository.findByRfidUid(uid);

        if (employeeOpt.isEmpty()) {
            log.warn("Unknown RFID: {}", uid);
            notificationService.notify("UNKNOWN_BADGE", "Badge inconnu",
                "Badge non reconnu : " + uid, "HIGH");
            return ResponseEntity.ok(
                new ScanResponse(false, null, null, null, "Badge non reconnu", null));
        }

        Employee employee = employeeOpt.get();

        if ("INACTIF".equals(employee.getStatus())) {
            notificationService.notify("INACTIVE_SCAN", "Badge désactivé",
                "Badge INACTIF scanné : " + uid + " (" + employee.getFirstName() + " " + employee.getLastName() + ")",
                "HIGH", "EMPLOYEE", employee.getId());
            return ResponseEntity.ok(
                new ScanResponse(false, null, null, null, "Compte désactivé", null));
        }

        LocalDate today = LocalDate.now();
        LocalTime now = LocalTime.now();
        LocalTime scanTime = null;

        if (request.getTimestamp() != null && !request.getTimestamp().isEmpty()) {
            try {
                LocalDateTime ldt = LocalDateTime.parse(request.getTimestamp());
                today = ldt.toLocalDate();
                scanTime = ldt.toLocalTime();
                log.info("Using provided timestamp: {} (offline sync)", request.getTimestamp());
            } catch (Exception e) {
                log.warn("Invalid timestamp format: {}, using current time", request.getTimestamp());
            }
        }

        final LocalDate scanDate = today;
        final LocalTime effectiveNow = scanTime != null ? scanTime : now;

        Optional<Attendance> existingOpt = attendanceRepository
            .findByEmployeeIdAndDateBetweenOrderByDateAsc(employee.getId(), scanDate, scanDate)
            .stream().findFirst();

        ScanResponse response;

        if (existingOpt.isEmpty() || existingOpt.get().getCheckIn() == null) {
            Attendance attendance = attendanceService.recordCheckIn(employee.getId(), scanDate, effectiveNow);
            response = new ScanResponse(
                true, "CHECK_IN",
                employee.getFirstName() + " " + employee.getLastName(),
                employee.getMatricule(),
                "Arrivée enregistrée",
                effectiveNow.format(TIME_FMT)
            );
            log.info("CHECK_IN {} at {}", employee.getMatricule(), effectiveNow.format(TIME_FMT));
            logScanEvent("ESP32-001", uid, employee, "CHECK_IN");

            String empName = employee.getFirstName() + " " + employee.getLastName();
            notificationService.notify("CHECK_IN", "Arrivée enregistrée",
                empName + " a pointé à " + effectiveNow.format(TIME_FMT), "LOW",
                "ATTENDANCE", attendance.getId());

            if (attendance.getLateMinutes() != null && attendance.getLateMinutes() > 0) {
                notificationService.notify("LATE_ARRIVAL", "Retard détecté",
                    empName + " — " + attendance.getLateMinutes() + " min de retard", "MEDIUM",
                    "ATTENDANCE", attendance.getId());
            }

        } else {
            Attendance attendance = existingOpt.get();
            if (attendance.getCheckOut() == null) {
                attendanceService.recordCheckOut(employee.getId(), scanDate, effectiveNow);
                response = new ScanResponse(
                    true, "CHECK_OUT",
                    employee.getFirstName() + " " + employee.getLastName(),
                    employee.getMatricule(),
                    "Départ enregistré",
                    effectiveNow.format(TIME_FMT)
                );
                log.info("CHECK_OUT {} at {}", employee.getMatricule(), effectiveNow.format(TIME_FMT));
                logScanEvent("ESP32-001", uid, employee, "CHECK_OUT");

                String empName = employee.getFirstName() + " " + employee.getLastName();
                notificationService.notify("CHECK_OUT", "Départ enregistré",
                    empName + " est parti à " + effectiveNow.format(TIME_FMT), "LOW",
                    "ATTENDANCE", attendance.getId());

                if ("PARTIAL".equals(attendance.getStatus())) {
                    notificationService.notify("EARLY_DEPARTURE", "Départ anticipé",
                        empName + " — parti à " + effectiveNow.format(TIME_FMT) + " (jour partiel)",
                        "MEDIUM", "ATTENDANCE", attendance.getId());
                }
            } else {
                response = new ScanResponse(
                    false, null,
                    employee.getFirstName() + " " + employee.getLastName(),
                    employee.getMatricule(),
                    "Deja pointe",
                    effectiveNow.format(TIME_FMT)
                );
            }
        }

        return ResponseEntity.ok(response);
    }

    @PostMapping("/heartbeat")
    public ResponseEntity<Map<String, Object>> heartbeat(
            @RequestBody(required = false) HeartbeatRequest request,
            @RequestHeader(value = "X-API-Key", required = false) String apiKey) {
        if (apiKey == null || !apiKey.equals(expectedApiKey)) {
            return ResponseEntity.status(401).build();
        }

        String deviceId = (request != null && request.getDeviceId() != null)
            ? request.getDeviceId() : "ESP32-001";

        TerminalStatus terminal = terminalStatusRepository.findByDeviceId(deviceId)
            .orElse(new TerminalStatus(deviceId));

        terminal.setDeviceName("Terminal Pointage");
        terminal.setLastHeartbeat(LocalDateTime.now());

        if (request != null) {
            if (request.getIpAddress() != null) terminal.setIpAddress(request.getIpAddress());
            if (request.getRssi() != null) terminal.setRssi(request.getRssi());
            if (request.getFirmwareVersion() != null) terminal.setFirmwareVersion(request.getFirmwareVersion());
            if (request.getFreeMemory() != null) terminal.setFreeMemory(request.getFreeMemory());
            if (request.getUptimeSeconds() != null) terminal.setUptimeSeconds(request.getUptimeSeconds());
        }

        int todayScans = scanEventRepository.countTodayScans(LocalDate.now().atStartOfDay());
        terminal.setScansToday(todayScans);

        terminalStatusRepository.save(terminal);

        log.debug("Heartbeat from {}", deviceId);

        Map<String, Object> resp = new HashMap<>();
        resp.put("status", "ok");
        resp.put("serverTime", LocalDateTime.now().format(
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")));
        return ResponseEntity.ok(resp);
    }

    @GetMapping("/terminals")
    public ResponseEntity<Map<String, Object>> getTerminals(
            @RequestHeader(value = "X-API-Key", required = false) String apiKey) {
        List<TerminalStatus> terminals = terminalStatusRepository.findAll();

        List<Map<String, Object>> terminalList = terminals.stream().map(t -> {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", t.getDeviceId());
            map.put("name", t.getDeviceName());
            map.put("ipAddress", t.getIpAddress());
            map.put("rssi", t.getRssi());
            map.put("firmwareVersion", t.getFirmwareVersion());
            map.put("freeMemory", t.getFreeMemory());
            map.put("uptimeSeconds", t.getUptimeSeconds());
            map.put("scansToday", t.getScansToday());

            boolean online = t.getLastHeartbeat() != null &&
                t.getLastHeartbeat().isAfter(LocalDateTime.now().minusSeconds(90));
            map.put("status", online ? "online" : "offline");
            map.put("lastPing", t.getLastHeartbeat() != null ? t.getLastHeartbeat().toString() : null);

            return map;
        }).collect(Collectors.toList());

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("success", true);
        resp.put("data", terminalList);
        return ResponseEntity.ok(resp);
    }

    @GetMapping("/scans/recent")
    public ResponseEntity<Map<String, Object>> getRecentScans(
            @RequestParam(defaultValue = "20") int limit) {
        List<ScanEvent> events = scanEventRepository.findTop20ByScannedAtAfterOrderByScannedAtDesc(
            LocalDate.now().atStartOfDay());

        List<Map<String, Object>> scanList = events.stream().map(e -> {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", e.getId());
            map.put("employeeName", e.getEmployeeName());
            map.put("matricule", e.getMatricule());
            map.put("rfidUid", e.getRfidUid());
            map.put("action", e.getAction());
            map.put("time", e.getScannedAt() != null ? e.getScannedAt().format(TIME_FMT) : null);
            map.put("scannedAt", e.getScannedAt() != null ? e.getScannedAt().toString() : null);
            map.put("deviceId", e.getDeviceId());
            return map;
        }).collect(Collectors.toList());

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("success", true);
        resp.put("data", scanList);
        return ResponseEntity.ok(resp);
    }

    private void logScanEvent(String deviceId, String rfidUid, Employee employee, String action) {
        try {
            ScanEvent event = new ScanEvent(deviceId, rfidUid, employee.getId(),
                employee.getFirstName() + " " + employee.getLastName(),
                employee.getMatricule(), action);
            scanEventRepository.save(event);
        } catch (Exception e) {
            log.error("Failed to log scan event", e);
        }
    }
}
