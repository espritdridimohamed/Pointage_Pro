package com.pointagepro.attendance;

import com.pointagepro.attendance.dto.AttendanceMonthlySummary;
import com.pointagepro.attendance.dto.AttendanceRecord;
import com.pointagepro.shared.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/attendance")
public class AttendanceController {

    private final AttendanceService service;

    public AttendanceController(AttendanceService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<AttendanceRecord>>> getByMonth(
            @RequestParam(defaultValue = "0") int month,
            @RequestParam(defaultValue = "0") int year,
            @RequestParam(required = false) Long employeeId) {
        if (month == 0) month = java.time.LocalDate.now().getMonthValue();
        if (year == 0) year = java.time.LocalDate.now().getYear();

        List<AttendanceRecord> records;
        if (employeeId != null) {
            records = service.getByEmployeeAndMonth(employeeId, month, year);
        } else {
            records = service.getByMonth(month, year);
        }
        return ResponseEntity.ok(ApiResponse.success("Attendance retrieved", records));
    }

    @GetMapping("/summary")
    public ResponseEntity<ApiResponse<AttendanceMonthlySummary>> getSummary(
            @RequestParam Long employeeId,
            @RequestParam(defaultValue = "0") int month,
            @RequestParam(defaultValue = "0") int year) {
        if (month == 0) month = java.time.LocalDate.now().getMonthValue();
        if (year == 0) year = java.time.LocalDate.now().getYear();
        return ResponseEntity.ok(ApiResponse.success("Summary retrieved",
                service.getMonthlySummary(employeeId, month, year)));
    }

    @PostMapping("/check-in")
    public ResponseEntity<ApiResponse<AttendanceRecord>> checkIn(@RequestParam Long employeeId) {
        Attendance attendance = service.recordCheckIn(employeeId, java.time.LocalDate.now());
        return ResponseEntity.ok(ApiResponse.success("Check-in recorded", AttendanceRecord.fromEntity(attendance)));
    }

    @PostMapping("/check-out")
    public ResponseEntity<ApiResponse<AttendanceRecord>> checkOut(@RequestParam Long employeeId) {
        Attendance attendance = service.recordCheckOut(employeeId, java.time.LocalDate.now());
        return ResponseEntity.ok(ApiResponse.success("Check-out recorded", AttendanceRecord.fromEntity(attendance)));
    }
}
