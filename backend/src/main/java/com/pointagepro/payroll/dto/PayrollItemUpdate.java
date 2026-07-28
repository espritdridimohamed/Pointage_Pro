package com.pointagepro.payroll.dto;

import java.math.BigDecimal;

public class PayrollItemUpdate {

    private BigDecimal primeTransport;
    private BigDecimal primePerformance;
    private BigDecimal primeOther;

    public BigDecimal getPrimeTransport() { return primeTransport; }
    public void setPrimeTransport(BigDecimal primeTransport) { this.primeTransport = primeTransport; }
    public BigDecimal getPrimePerformance() { return primePerformance; }
    public void setPrimePerformance(BigDecimal primePerformance) { this.primePerformance = primePerformance; }
    public BigDecimal getPrimeOther() { return primeOther; }
    public void setPrimeOther(BigDecimal primeOther) { this.primeOther = primeOther; }
}
