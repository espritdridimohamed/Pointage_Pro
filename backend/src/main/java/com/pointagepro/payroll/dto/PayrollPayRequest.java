package com.pointagepro.payroll.dto;

import jakarta.validation.constraints.Size;

public class PayrollPayRequest {

    @Size(max = 50, message = "bankTransferRef must be at most 50 characters")
    private String bankTransferRef;

    public PayrollPayRequest() {
    }

    public PayrollPayRequest(String bankTransferRef) {
        this.bankTransferRef = bankTransferRef;
    }

    public String getBankTransferRef() { return bankTransferRef; }
}
