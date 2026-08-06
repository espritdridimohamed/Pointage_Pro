package com.pointagepro.leave.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Body of {@code POST /api/v1/leaves/{id}/cancel} — the required cancellation reason.
 */
public class LeaveCancelRequest {

    @NotBlank(message = "reason is required")
    @Size(max = 255, message = "reason must be at most 255 characters")
    private String reason;

    public LeaveCancelRequest() {
    }

    public LeaveCancelRequest(String reason) {
        this.reason = reason;
    }

    public String getReason() { return reason; }
}
