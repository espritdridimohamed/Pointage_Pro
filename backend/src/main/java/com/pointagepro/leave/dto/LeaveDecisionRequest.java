package com.pointagepro.leave.dto;

import jakarta.validation.constraints.Size;

/**
 * Body of {@code POST /api/v1/leaves/{id}/approve} and {@code /reject} — the optional
 * free-text comment attached to the decided approval step. For {@code /reject} the
 * comment is also stored as {@code leave_requests.rejected_reason} (max 255).
 */
public class LeaveDecisionRequest {

    @Size(max = 500, message = "comment must be at most 500 characters")
    private String comment;

    public LeaveDecisionRequest() {
    }

    public LeaveDecisionRequest(String comment) {
        this.comment = comment;
    }

    public String getComment() { return comment; }
}
