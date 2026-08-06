package com.pointagepro.payroll.dto;

import java.math.BigDecimal;

public class PayrollTotals {

    private final BigDecimal gross;
    private final BigDecimal cnss;
    private final BigDecimal irpp;
    private final BigDecimal css;
    private final BigDecimal deductions;
    private final BigDecimal net;

    public PayrollTotals(BigDecimal gross, BigDecimal cnss, BigDecimal irpp,
                         BigDecimal css, BigDecimal deductions, BigDecimal net) {
        this.gross = gross;
        this.cnss = cnss;
        this.irpp = irpp;
        this.css = css;
        this.deductions = deductions;
        this.net = net;
    }

    public BigDecimal getGross() { return gross; }
    public BigDecimal getCnss() { return cnss; }
    public BigDecimal getIrpp() { return irpp; }
    public BigDecimal getCss() { return css; }
    public BigDecimal getDeductions() { return deductions; }
    public BigDecimal getNet() { return net; }
}
