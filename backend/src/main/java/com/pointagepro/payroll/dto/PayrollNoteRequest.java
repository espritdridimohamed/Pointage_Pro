package com.pointagepro.payroll.dto;

import jakarta.validation.constraints.Size;

public class PayrollNoteRequest {

    @Size(max = 500, message = "notes must be at most 500 characters")
    private String notes;

    public PayrollNoteRequest() {
    }

    public PayrollNoteRequest(String notes) {
        this.notes = notes;
    }

    public String getNotes() { return notes; }
}
