package com.pointagepro.attendance.dto;

import jakarta.validation.constraints.Size;

/**
 * Body of {@code POST /attendance/adjustments/{id}/approve} and {@code /reject} — the
 * optional free-text comment attached to the decided approval step.
 */
public class AdjustmentDecisionRequest {

    @Size(max = 500, message = "comment must be at most 500 characters")
    private String comment;

    public AdjustmentDecisionRequest() {
    }

    public AdjustmentDecisionRequest(String comment) {
        this.comment = comment;
    }

    public String getComment() { return comment; }
}
