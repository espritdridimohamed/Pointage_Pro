package com.pointagepro.payroll;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "payroll_items")
public class PayrollItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "payroll_id", nullable = false)
    private Long payrollId;

    @Column(name = "employee_id", nullable = false)
    private Long employeeId;

    @Column(name = "base_salary", nullable = false, precision = 10, scale = 2)
    private BigDecimal baseSalary;

    @Column(name = "prime_transport", precision = 10, scale = 2)
    private BigDecimal primeTransport = BigDecimal.ZERO;

    @Column(name = "prime_performance", precision = 10, scale = 2)
    private BigDecimal primePerformance = BigDecimal.ZERO;

    @Column(name = "prime_other", precision = 10, scale = 2)
    private BigDecimal primeOther = BigDecimal.ZERO;

    @Column(name = "overtime_hours", precision = 5, scale = 2)
    private BigDecimal overtimeHours = BigDecimal.ZERO;

    @Column(name = "overtime_amount", precision = 10, scale = 2)
    private BigDecimal overtimeAmount = BigDecimal.ZERO;

    @Column(name = "total_gross", precision = 10, scale = 2)
    private BigDecimal totalGross = BigDecimal.ZERO;

    @Column(name = "cnss_deduction", precision = 10, scale = 2)
    private BigDecimal cnssDeduction = BigDecimal.ZERO;

    @Column(name = "assurance_deduction", precision = 10, scale = 2)
    private BigDecimal assuranceDeduction = BigDecimal.ZERO;

    @Column(name = "ir_deduction", precision = 10, scale = 2)
    private BigDecimal irDeduction = BigDecimal.ZERO;

    @Column(name = "late_deduction", precision = 10, scale = 2)
    private BigDecimal lateDeduction = BigDecimal.ZERO;

    @Column(name = "absence_deduction", precision = 10, scale = 2)
    private BigDecimal absenceDeduction = BigDecimal.ZERO;

    @Column(name = "missing_hours", precision = 7, scale = 2)
    private BigDecimal missingHours = BigDecimal.ZERO;

    @Column(name = "missing_hours_deduction", precision = 10, scale = 2)
    private BigDecimal missingHoursDeduction = BigDecimal.ZERO;

    @Column(name = "absence_hours", precision = 7, scale = 2)
    private BigDecimal absenceHours = BigDecimal.ZERO;

    @Column(name = "total_deductions", precision = 10, scale = 2)
    private BigDecimal totalDeductions = BigDecimal.ZERO;

    @Column(name = "net_salary", precision = 10, scale = 2)
    private BigDecimal netSalary = BigDecimal.ZERO;

    @Column(name = "days_worked")
    private Integer daysWorked = 0;

    @Column(name = "days_absent")
    private Integer daysAbsent = 0;

    @Column(name = "late_minutes")
    private Integer lateMinutes = 0;

    @Column(name = "total_overtime_minutes")
    private Integer totalOvertimeMinutes = 0;

    @Column(name = "hourly_rate", precision = 10, scale = 4)
    private BigDecimal hourlyRate;

    @Column(nullable = false, length = 20)
    private String status = "PENDING";

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getPayrollId() { return payrollId; }
    public void setPayrollId(Long payrollId) { this.payrollId = payrollId; }
    public Long getEmployeeId() { return employeeId; }
    public void setEmployeeId(Long employeeId) { this.employeeId = employeeId; }
    public BigDecimal getBaseSalary() { return baseSalary; }
    public void setBaseSalary(BigDecimal baseSalary) { this.baseSalary = baseSalary; }
    public BigDecimal getPrimeTransport() { return primeTransport; }
    public void setPrimeTransport(BigDecimal primeTransport) { this.primeTransport = primeTransport; }
    public BigDecimal getPrimePerformance() { return primePerformance; }
    public void setPrimePerformance(BigDecimal primePerformance) { this.primePerformance = primePerformance; }
    public BigDecimal getPrimeOther() { return primeOther; }
    public void setPrimeOther(BigDecimal primeOther) { this.primeOther = primeOther; }
    public BigDecimal getOvertimeHours() { return overtimeHours; }
    public void setOvertimeHours(BigDecimal overtimeHours) { this.overtimeHours = overtimeHours; }
    public BigDecimal getOvertimeAmount() { return overtimeAmount; }
    public void setOvertimeAmount(BigDecimal overtimeAmount) { this.overtimeAmount = overtimeAmount; }
    public BigDecimal getTotalGross() { return totalGross; }
    public void setTotalGross(BigDecimal totalGross) { this.totalGross = totalGross; }
    public BigDecimal getCnssDeduction() { return cnssDeduction; }
    public void setCnssDeduction(BigDecimal cnssDeduction) { this.cnssDeduction = cnssDeduction; }
    public BigDecimal getAssuranceDeduction() { return assuranceDeduction; }
    public void setAssuranceDeduction(BigDecimal assuranceDeduction) { this.assuranceDeduction = assuranceDeduction; }
    public BigDecimal getIrDeduction() { return irDeduction; }
    public void setIrDeduction(BigDecimal irDeduction) { this.irDeduction = irDeduction; }
    public BigDecimal getLateDeduction() { return lateDeduction; }
    public void setLateDeduction(BigDecimal lateDeduction) { this.lateDeduction = lateDeduction; }
    public BigDecimal getAbsenceDeduction() { return absenceDeduction; }
    public void setAbsenceDeduction(BigDecimal absenceDeduction) { this.absenceDeduction = absenceDeduction; }
    public BigDecimal getMissingHours() { return missingHours; }
    public void setMissingHours(BigDecimal missingHours) { this.missingHours = missingHours; }
    public BigDecimal getMissingHoursDeduction() { return missingHoursDeduction; }
    public void setMissingHoursDeduction(BigDecimal missingHoursDeduction) { this.missingHoursDeduction = missingHoursDeduction; }
    public BigDecimal getAbsenceHours() { return absenceHours; }
    public void setAbsenceHours(BigDecimal absenceHours) { this.absenceHours = absenceHours; }
    public BigDecimal getTotalDeductions() { return totalDeductions; }
    public void setTotalDeductions(BigDecimal totalDeductions) { this.totalDeductions = totalDeductions; }
    public BigDecimal getNetSalary() { return netSalary; }
    public void setNetSalary(BigDecimal netSalary) { this.netSalary = netSalary; }
    public Integer getDaysWorked() { return daysWorked; }
    public void setDaysWorked(Integer daysWorked) { this.daysWorked = daysWorked; }
    public Integer getDaysAbsent() { return daysAbsent; }
    public void setDaysAbsent(Integer daysAbsent) { this.daysAbsent = daysAbsent; }
    public Integer getLateMinutes() { return lateMinutes; }
    public void setLateMinutes(Integer lateMinutes) { this.lateMinutes = lateMinutes; }
    public Integer getTotalOvertimeMinutes() { return totalOvertimeMinutes; }
    public void setTotalOvertimeMinutes(Integer totalOvertimeMinutes) { this.totalOvertimeMinutes = totalOvertimeMinutes; }
    public BigDecimal getHourlyRate() { return hourlyRate; }
    public void setHourlyRate(BigDecimal hourlyRate) { this.hourlyRate = hourlyRate; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getPaidAt() { return paidAt; }
    public void setPaidAt(LocalDateTime paidAt) { this.paidAt = paidAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
