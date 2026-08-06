package com.pointagepro.attendance.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Body of {@code POST /attendance/adjustments/{id}/cancel} — cancellation requires a
 * reason, which is recorded on the cancelled approval steps and in the audit log.
 */
public class AdjustmentCancelRequest {

    @NotBlank(message = "reason is required")
    @Size(max = 255, message = "reason must be at most 255 characters")
    private String reason;

    public AdjustmentCancelRequest() {
    }

    public AdjustmentCancelRequest(String reason) {
        this.reason = reason;
    }

    public String getReason() { return reason; }
}
