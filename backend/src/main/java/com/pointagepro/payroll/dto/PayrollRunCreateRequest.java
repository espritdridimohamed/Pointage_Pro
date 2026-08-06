package com.pointagepro.payroll.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class PayrollRunCreateRequest {

    @NotNull(message = "periodYear is required")
    private Integer periodYear;

    @NotNull(message = "periodMonth is required")
    @Min(value = 1, message = "periodMonth must be between 1 and 12")
    @Max(value = 12, message = "periodMonth must be between 1 and 12")
    private Integer periodMonth;

    @Size(max = 500, message = "notes must be at most 500 characters")
    private String notes;

    public PayrollRunCreateRequest() {
    }

    public PayrollRunCreateRequest(Integer periodYear, Integer periodMonth, String notes) {
        this.periodYear = periodYear;
        this.periodMonth = periodMonth;
        this.notes = notes;
    }

    public Integer getPeriodYear() { return periodYear; }
    public Integer getPeriodMonth() { return periodMonth; }
    public String getNotes() { return notes; }
}
