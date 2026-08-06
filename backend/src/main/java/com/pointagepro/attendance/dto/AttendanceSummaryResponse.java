package com.pointagepro.attendance.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.pointagepro.attendance.entity.AttendanceSummary;
import com.pointagepro.auth.entity.User;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * Response DTO for a materialized day summary (AttendanceSummary). Built from the entity
 * with the associations eagerly loaded via {@code AttendanceSummaryRepository} entity graphs,
 * so mapping may safely happen after the transaction commits.
 */
public class AttendanceSummaryResponse {

    private Long id;
    private Long employeeId;
    private String employeeName;
    private LocalDate workDate;
    private String dayTypeCode;
    private String dayTypeLabel;
    private Long scheduleId;
    private String scheduleCode;

    @JsonFormat(pattern = "HH:mm")
    private LocalTime firstIn;

    @JsonFormat(pattern = "HH:mm")
    private LocalTime lastOut;
    private int workedMinutes;
    private int lateMinutes;
    private int earlyExitMinutes;
    private int missingMinutes;
    private int overtimeMinutes;
    private int nettedWorkMinutes;
    private int adjustmentMinutes;
    private boolean isWeekend;
    private boolean isHoliday;
    private String statusCode;
    private String statusLabel;
    private LocalDateTime computedAt;
    private String recomputeReason;
    private ComputedBy computedBy;

    public AttendanceSummaryResponse() {
    }

    public static AttendanceSummaryResponse from(AttendanceSummary s) {
        AttendanceSummaryResponse r = new AttendanceSummaryResponse();
        r.id = s.getId();
        r.employeeId = s.getEmployee().getId();
        r.employeeName = s.getEmployee().getFirstName() + " " + s.getEmployee().getLastName();
        r.workDate = s.getWorkDate();
        if (s.getDayType() != null) {
            r.dayTypeCode = s.getDayType().getCode();
            r.dayTypeLabel = s.getDayType().getLabel();
        }
        if (s.getSchedule() != null) {
            r.scheduleId = s.getSchedule().getId();
            r.scheduleCode = s.getSchedule().getCode();
        }
        r.firstIn = s.getFirstIn();
        r.lastOut = s.getLastOut();
        r.workedMinutes = s.getWorkedMinutes();
        r.lateMinutes = s.getLateMinutes();
        r.earlyExitMinutes = s.getEarlyExitMinutes();
        r.missingMinutes = s.getMissingMinutes();
        r.overtimeMinutes = s.getOvertimeMinutes();
        r.nettedWorkMinutes = s.getNettedWorkMinutes();
        r.adjustmentMinutes = s.getAdjustmentMinutes();
        r.isWeekend = Boolean.TRUE.equals(s.getIsWeekend());
        r.isHoliday = Boolean.TRUE.equals(s.getIsHoliday());
        if (s.getStatus() != null) {
            r.statusCode = s.getStatus().getCode();
            r.statusLabel = s.getStatus().getLabel();
        }
        r.computedAt = s.getComputedAt();
        r.recomputeReason = s.getRecomputeReason();
        if (s.getComputedBy() != null) {
            r.computedBy = new ComputedBy(s.getComputedBy().getId(), s.getComputedBy().getFullName());
        }
        return r;
    }

    public Long getId() { return id; }
    public Long getEmployeeId() { return employeeId; }
    public String getEmployeeName() { return employeeName; }
    public LocalDate getWorkDate() { return workDate; }
    public String getDayTypeCode() { return dayTypeCode; }
    public String getDayTypeLabel() { return dayTypeLabel; }
    public Long getScheduleId() { return scheduleId; }
    public String getScheduleCode() { return scheduleCode; }
    public LocalTime getFirstIn() { return firstIn; }
    public LocalTime getLastOut() { return lastOut; }
    public int getWorkedMinutes() { return workedMinutes; }
    public int getLateMinutes() { return lateMinutes; }
    public int getEarlyExitMinutes() { return earlyExitMinutes; }
    public int getMissingMinutes() { return missingMinutes; }
    public int getOvertimeMinutes() { return overtimeMinutes; }
    public int getNettedWorkMinutes() { return nettedWorkMinutes; }
    public int getAdjustmentMinutes() { return adjustmentMinutes; }
    public boolean isWeekend() { return isWeekend; }
    public boolean isHoliday() { return isHoliday; }
    public String getStatusCode() { return statusCode; }
    public String getStatusLabel() { return statusLabel; }
    public LocalDateTime getComputedAt() { return computedAt; }
    public String getRecomputeReason() { return recomputeReason; }
    public ComputedBy getComputedBy() { return computedBy; }

    public static class ComputedBy {
        private final Long id;
        private final String fullName;

        public ComputedBy(Long id, String fullName) {
            this.id = id;
            this.fullName = fullName;
        }

        public Long getId() { return id; }
        public String getFullName() { return fullName; }
    }
}
