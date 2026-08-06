package com.pointagepro.attendance.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * Body of {@code POST /attendance/recompute/all} — company-wide recompute over a date range.
 */
public class RecomputeAllRequest {

    @NotNull(message = "from is required")
    private LocalDate from;

    @NotNull(message = "to is required")
    private LocalDate to;

    @Size(max = 255, message = "reason must be at most 255 characters")
    private String reason;

    public RecomputeAllRequest() {
    }

    public RecomputeAllRequest(LocalDate from, LocalDate to, String reason) {
        this.from = from;
        this.to = to;
        this.reason = reason;
    }

    public LocalDate getFrom() { return from; }
    public LocalDate getTo() { return to; }
    public String getReason() { return reason; }
}
