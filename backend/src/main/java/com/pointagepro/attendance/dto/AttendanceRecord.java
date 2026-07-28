package com.pointagepro.attendance.dto;

import com.pointagepro.attendance.Attendance;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public class AttendanceRecord {

    private Long id;
    private Long employeeId;
    private String date;
    private LocalDateTime checkIn;
    private LocalDateTime checkOut;
    private BigDecimal workedHours;
    private Integer lateMinutes;
    private BigDecimal overtimeHours;
    private String status;

    public static AttendanceRecord fromEntity(Attendance a) {
        AttendanceRecord r = new AttendanceRecord();
        r.id = a.getId();
        r.employeeId = a.getEmployeeId();
        r.date = a.getDate() != null ? a.getDate().toString() : null;
        r.checkIn = a.getCheckIn();
        r.checkOut = a.getCheckOut();
        r.workedHours = a.getWorkedHours();
        r.lateMinutes = a.getLateMinutes();
        r.overtimeHours = a.getOvertimeHours();
        r.status = a.getStatus();
        return r;
    }

    public Long getId() { return id; }
    public Long getEmployeeId() { return employeeId; }
    public String getDate() { return date; }
    public LocalDateTime getCheckIn() { return checkIn; }
    public LocalDateTime getCheckOut() { return checkOut; }
    public BigDecimal getWorkedHours() { return workedHours; }
    public Integer getLateMinutes() { return lateMinutes; }
    public BigDecimal getOvertimeHours() { return overtimeHours; }
    public String getStatus() { return status; }
}
