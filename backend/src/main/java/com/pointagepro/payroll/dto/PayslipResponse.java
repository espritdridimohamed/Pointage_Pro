package com.pointagepro.payroll.dto;

import com.pointagepro.payroll.entity.Payslip;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Response DTO for a payslip (PAYROLL_API_CONTRACT.md §13). Built from a
 * {@link Payslip} loaded with its entity graph; the component list is attached
 * by the service.
 */
public class PayslipResponse {

    private Long id;
    private String payslipNumber;
    private PayrollItemRef payrollItem;
    private BigDecimal grossSalary;
    private BigDecimal cnssSalarial;
    private BigDecimal irpp;
    private BigDecimal css;
    private BigDecimal netSalary;
    private LocalDateTime issuedAt;
    private String pdfPath;
    private LocalDateTime sentAt;
    private List<PayrollComponentResponse> components;

    public PayslipResponse() {
    }

    public static PayslipResponse from(Payslip p, List<PayrollComponentResponse> components) {
        PayslipResponse dto = new PayslipResponse();
        dto.id = p.getId();
        dto.payslipNumber = p.getPayslipNumber();
        dto.payrollItem = new PayrollItemRef(
                p.getPayrollItem().getId(),
                p.getPayrollItem().getPayroll().getPeriodYear(),
                p.getPayrollItem().getPayroll().getPeriodMonth(),
                new EmployeeRef(p.getPayrollItem().getEmployee()));
        dto.grossSalary = p.getPayrollItem().getGrossSalary();
        dto.cnssSalarial = p.getPayrollItem().getCnssSalarial();
        dto.irpp = p.getPayrollItem().getIrpp();
        dto.css = p.getPayrollItem().getCss();
        dto.netSalary = p.getPayrollItem().getNetSalary();
        dto.issuedAt = p.getIssuedAt();
        dto.pdfPath = p.getPdfPath();
        dto.sentAt = p.getSentAt();
        dto.components = components;
        return dto;
    }

    public Long getId() { return id; }
    public String getPayslipNumber() { return payslipNumber; }
    public PayrollItemRef getPayrollItem() { return payrollItem; }
    public BigDecimal getGrossSalary() { return grossSalary; }
    public BigDecimal getCnssSalarial() { return cnssSalarial; }
    public BigDecimal getIrpp() { return irpp; }
    public BigDecimal getCss() { return css; }
    public BigDecimal getNetSalary() { return netSalary; }
    public LocalDateTime getIssuedAt() { return issuedAt; }
    public String getPdfPath() { return pdfPath; }
    public LocalDateTime getSentAt() { return sentAt; }
    public List<PayrollComponentResponse> getComponents() { return components; }

    /** Compact payroll-item reference nested in the payslip response. */
    public static class PayrollItemRef {
        private final Long id;
        private final Integer periodYear;
        private final Integer periodMonth;
        private final EmployeeRef employee;

        public PayrollItemRef(Long id, Integer periodYear, Integer periodMonth, EmployeeRef employee) {
            this.id = id;
            this.periodYear = periodYear;
            this.periodMonth = periodMonth;
            this.employee = employee;
        }

        public Long getId() { return id; }
        public Integer getPeriodYear() { return periodYear; }
        public Integer getPeriodMonth() { return periodMonth; }
        public EmployeeRef getEmployee() { return employee; }
    }
}
