package com.pointagepro.attendance.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * Body of {@code POST /attendance/recompute} — explicit per-employee recompute over a date
 * range. {@code reason} is stored in {@code attendance_summary.recompute_reason} for audit.
 */
public class RecomputeRequest {

    @NotNull(message = "employeeId is required")
    private Long employeeId;

    @NotNull(message = "from is required")
    private LocalDate from;

    @NotNull(message = "to is required")
    private LocalDate to;

    @Size(max = 255, message = "reason must be at most 255 characters")
    private String reason;

    public RecomputeRequest() {
    }

    public RecomputeRequest(Long employeeId, LocalDate from, LocalDate to, String reason) {
        this.employeeId = employeeId;
        this.from = from;
        this.to = to;
        this.reason = reason;
    }

    public Long getEmployeeId() { return employeeId; }
    public LocalDate getFrom() { return from; }
    public LocalDate getTo() { return to; }
    public String getReason() { return reason; }
}
