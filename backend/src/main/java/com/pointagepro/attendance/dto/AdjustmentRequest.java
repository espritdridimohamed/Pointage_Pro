package com.pointagepro.attendance.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * Body of {@code POST /attendance/adjustments} — a new correction request. The target
 * day, type, minutes and reason are validated in {@code AttendanceAdjustmentService}
 * (tenant, scope, frozen month, allowed type, SET_ABSENT zero minutes, reason length).
 */
public class AdjustmentRequest {

    @NotNull(message = "employeeId is required")
    private Long employeeId;

    @NotNull(message = "workDate is required")
    private LocalDate workDate;

    @NotBlank(message = "adjustmentType is required")
    private String adjustmentType;

    @NotNull(message = "minutes is required")
    @PositiveOrZero(message = "minutes must be zero or positive")
    private Integer minutes;

    @NotBlank(message = "reason is required")
    @Size(max = 255, message = "reason must be at most 255 characters")
    private String reason;

    public AdjustmentRequest() {
    }

    public AdjustmentRequest(Long employeeId, LocalDate workDate, String adjustmentType,
                             Integer minutes, String reason) {
        this.employeeId = employeeId;
        this.workDate = workDate;
        this.adjustmentType = adjustmentType;
        this.minutes = minutes;
        this.reason = reason;
    }

    public Long getEmployeeId() { return employeeId; }
    public LocalDate getWorkDate() { return workDate; }
    public String getAdjustmentType() { return adjustmentType; }
    public Integer getMinutes() { return minutes; }
    public String getReason() { return reason; }
}
