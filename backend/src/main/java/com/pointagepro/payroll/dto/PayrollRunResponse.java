package com.pointagepro.payroll.dto;

import com.pointagepro.payroll.entity.Payroll;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Response DTO for one payroll run (summary + totals + warnings). Built from a
 * {@link Payroll} loaded with its entity graph so mapping may safely happen
 * after the transaction commits.
 */
public class PayrollRunResponse {

    private Long id;
    private Integer periodYear;
    private Integer periodMonth;
    private LocalDate runDate;
    private PayrollStatusDto status;
    private PayrollTotals totals;
    private Integer employeeCount;
    private Long createdBy;
    private Long approvedBy;
    private LocalDateTime approvedAt;
    private LocalDateTime paidAt;
    private String notes;
    private List<String> warnings;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public PayrollRunResponse() {
    }

    public static PayrollRunResponse from(Payroll p, List<String> warnings) {
        PayrollRunResponse dto = new PayrollRunResponse();
        dto.id = p.getId();
        dto.periodYear = p.getPeriodYear();
        dto.periodMonth = p.getPeriodMonth();
        dto.runDate = p.getRunDate();
        dto.status = PayrollStatusDto.from(p.getStatus());
        dto.totals = new PayrollTotals(p.getTotalGross(), p.getTotalCnss(), p.getTotalIrpp(),
                p.getTotalCss(), p.getTotalDeductions(), p.getTotalNet());
        dto.employeeCount = p.getEmployeeCount();
        dto.createdBy = p.getCreatedBy() != null ? p.getCreatedBy().getId() : null;
        dto.approvedBy = p.getApprovedBy() != null ? p.getApprovedBy().getId() : null;
        dto.approvedAt = p.getApprovedAt();
        dto.paidAt = p.getPaidAt();
        dto.notes = p.getNotes();
        dto.warnings = warnings;
        dto.createdAt = p.getCreatedAt();
        dto.updatedAt = p.getUpdatedAt();
        return dto;
    }

    public Long getId() { return id; }
    public Integer getPeriodYear() { return periodYear; }
    public Integer getPeriodMonth() { return periodMonth; }
    public LocalDate getRunDate() { return runDate; }
    public PayrollStatusDto getStatus() { return status; }
    public PayrollTotals getTotals() { return totals; }
    public Integer getEmployeeCount() { return employeeCount; }
    public Long getCreatedBy() { return createdBy; }
    public Long getApprovedBy() { return approvedBy; }
    public LocalDateTime getApprovedAt() { return approvedAt; }
    public LocalDateTime getPaidAt() { return paidAt; }
    public String getNotes() { return notes; }
    public List<String> getWarnings() { return warnings; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
