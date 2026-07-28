package com.pointagepro.attendance.dto;

import java.math.BigDecimal;

public class AttendanceMonthlySummary {

    private Long employeeId;
    private int month;
    private int year;
    private BigDecimal overtimeHours;
    private int lateMinutes;
    private int daysWorked;
    private int daysAbsent;
    private int totalWorkDays;

    public AttendanceMonthlySummary(Long employeeId, int month, int year,
                                     BigDecimal overtimeHours, int lateMinutes,
                                     int daysWorked, int daysAbsent, int totalWorkDays) {
        this.employeeId = employeeId;
        this.month = month;
        this.year = year;
        this.overtimeHours = overtimeHours;
        this.lateMinutes = lateMinutes;
        this.daysWorked = daysWorked;
        this.daysAbsent = daysAbsent;
        this.totalWorkDays = totalWorkDays;
    }

    public Long getEmployeeId() { return employeeId; }
    public int getMonth() { return month; }
    public int getYear() { return year; }
    public BigDecimal getOvertimeHours() { return overtimeHours; }
    public int getLateMinutes() { return lateMinutes; }
    public int getDaysWorked() { return daysWorked; }
    public int getDaysAbsent() { return daysAbsent; }
    public int getTotalWorkDays() { return totalWorkDays; }
}
