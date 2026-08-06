package com.pointagepro.payroll.dto;

import com.pointagepro.payroll.entity.PayrollItem;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Response DTO for one payroll item with its component lines. Built from a
 * {@link PayrollItem} loaded with its entity graph; the component list is
 * attached by the service.
 */
public class PayrollItemResponse {

    private Long id;
    private EmployeeRef employee;
    private Long contractId;
    private BigDecimal baseSalary;
    private Integer workDays;
    private BigDecimal workHours;
    private Integer overtimeMinutes;
    private BigDecimal overtimeAmount;
    private Integer absenceMinutes;
    private BigDecimal absenceDeduction;
    private Integer lateMinutes;
    private BigDecimal lateDeduction;
    private BigDecimal grossSalary;
    private BigDecimal cnssSalarial;
    private BigDecimal cnssPatronal;
    private BigDecimal irpp;
    private BigDecimal css;
    private BigDecimal netSalary;
    private boolean cancelled;
    private String bankTransferRef;
    private List<PayrollComponentResponse> components;

    public PayrollItemResponse() {
    }

    public static PayrollItemResponse from(PayrollItem item, List<PayrollComponentResponse> components) {
        PayrollItemResponse dto = new PayrollItemResponse();
        dto.id = item.getId();
        dto.employee = new EmployeeRef(item.getEmployee());
        dto.contractId = item.getContract() != null ? item.getContract().getId() : null;
        dto.baseSalary = item.getBaseSalary();
        dto.workDays = item.getWorkDays();
        dto.workHours = item.getWorkHours();
        dto.overtimeMinutes = item.getOvertimeMinutes();
        dto.overtimeAmount = item.getOvertimeAmount();
        dto.absenceMinutes = item.getAbsenceMinutes();
        dto.absenceDeduction = item.getAbsenceDeduction();
        dto.lateMinutes = item.getLateMinutes();
        dto.lateDeduction = item.getLateDeduction();
        dto.grossSalary = item.getGrossSalary();
        dto.cnssSalarial = item.getCnssSalarial();
        dto.cnssPatronal = item.getCnssPatronal();
        dto.irpp = item.getIrpp();
        dto.css = item.getCss();
        dto.netSalary = item.getNetSalary();
        dto.cancelled = Boolean.TRUE.equals(item.getCancelled());
        dto.bankTransferRef = item.getBankTransferRef();
        dto.components = components;
        return dto;
    }

    public Long getId() { return id; }
    public EmployeeRef getEmployee() { return employee; }
    public Long getContractId() { return contractId; }
    public BigDecimal getBaseSalary() { return baseSalary; }
    public Integer getWorkDays() { return workDays; }
    public BigDecimal getWorkHours() { return workHours; }
    public Integer getOvertimeMinutes() { return overtimeMinutes; }
    public BigDecimal getOvertimeAmount() { return overtimeAmount; }
    public Integer getAbsenceMinutes() { return absenceMinutes; }
    public BigDecimal getAbsenceDeduction() { return absenceDeduction; }
    public Integer getLateMinutes() { return lateMinutes; }
    public BigDecimal getLateDeduction() { return lateDeduction; }
    public BigDecimal getGrossSalary() { return grossSalary; }
    public BigDecimal getCnssSalarial() { return cnssSalarial; }
    public BigDecimal getCnssPatronal() { return cnssPatronal; }
    public BigDecimal getIrpp() { return irpp; }
    public BigDecimal getCss() { return css; }
    public BigDecimal getNetSalary() { return netSalary; }
    public boolean isCancelled() { return cancelled; }
    public String getBankTransferRef() { return bankTransferRef; }
    public List<PayrollComponentResponse> getComponents() { return components; }
}
