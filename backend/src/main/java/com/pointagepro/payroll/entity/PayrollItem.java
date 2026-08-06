package com.pointagepro.payroll.entity;

import com.pointagepro.contract.entity.EmployeeContract;
import com.pointagepro.employee.entity.Employee;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "payroll_items",
        uniqueConstraints = @UniqueConstraint(name = "uk_pitem", columnNames = {"payroll_id", "employee_id"}))
@Getter
@Setter
@NoArgsConstructor
public class PayrollItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payroll_id", nullable = false)
    private Payroll payroll;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contract_id")
    private EmployeeContract contract;

    @Column(name = "base_salary", nullable = false, precision = 10, scale = 2)
    private BigDecimal baseSalary;

    @Column(name = "work_days", nullable = false)
    private Integer workDays = 0;

    @Column(name = "work_hours", nullable = false, precision = 5, scale = 2)
    private BigDecimal workHours = BigDecimal.ZERO;

    @Column(name = "overtime_minutes", nullable = false)
    private Integer overtimeMinutes = 0;

    @Column(name = "overtime_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal overtimeAmount = BigDecimal.ZERO;

    @Column(name = "absence_minutes", nullable = false)
    private Integer absenceMinutes = 0;

    @Column(name = "absence_deduction", nullable = false, precision = 10, scale = 2)
    private BigDecimal absenceDeduction = BigDecimal.ZERO;

    @Column(name = "late_minutes", nullable = false)
    private Integer lateMinutes = 0;

    @Column(name = "late_deduction", nullable = false, precision = 10, scale = 2)
    private BigDecimal lateDeduction = BigDecimal.ZERO;

    @Column(name = "gross_salary", nullable = false, precision = 10, scale = 2)
    private BigDecimal grossSalary = BigDecimal.ZERO;

    @Column(name = "cnss_salarial", nullable = false, precision = 10, scale = 2)
    private BigDecimal cnssSalarial = BigDecimal.ZERO;

    @Column(name = "cnss_patronal", nullable = false, precision = 10, scale = 2)
    private BigDecimal cnssPatronal = BigDecimal.ZERO;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal irpp = BigDecimal.ZERO;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal css = BigDecimal.ZERO;

    @Column(name = "net_salary", nullable = false, precision = 10, scale = 2)
    private BigDecimal netSalary = BigDecimal.ZERO;

    @Column(nullable = false)
    private Boolean cancelled = false;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    @Column(name = "bank_transfer_ref", length = 50)
    private String bankTransferRef;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
