package com.pointagepro.payroll.dto;

import com.pointagepro.payroll.entity.PayrollStatus;

public class PayrollStatusDto {

    private final String code;
    private final String label;

    public PayrollStatusDto(String code, String label) {
        this.code = code;
        this.label = label;
    }

    public static PayrollStatusDto from(PayrollStatus status) {
        return new PayrollStatusDto(status.getCode(), status.getLabel());
    }

    public String getCode() { return code; }
    public String getLabel() { return label; }
}
