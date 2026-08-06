package com.pointagepro.payroll.entity;

import com.pointagepro.auth.entity.User;
import com.pointagepro.company.entity.Company;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "payrolls",
        uniqueConstraints = @UniqueConstraint(name = "uk_payrolls_period",
                columnNames = {"company_id", "period_year", "period_month"}))
@Getter
@Setter
@NoArgsConstructor
public class Payroll {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @Column(name = "period_year", nullable = false)
    private Integer periodYear;

    @Column(name = "period_month", nullable = false)
    private Integer periodMonth;

    @Column(name = "run_date")
    private LocalDate runDate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "status_id", nullable = false)
    private PayrollStatus status;

    @Column(name = "total_gross", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalGross = BigDecimal.ZERO;

    @Column(name = "total_cnss", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalCnss = BigDecimal.ZERO;

    @Column(name = "total_irpp", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalIrpp = BigDecimal.ZERO;

    @Column(name = "total_css", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalCss = BigDecimal.ZERO;

    @Column(name = "total_deductions", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalDeductions = BigDecimal.ZERO;

    @Column(name = "total_net", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalNet = BigDecimal.ZERO;

    @Column(name = "employee_count", nullable = false)
    private Integer employeeCount = 0;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approved_by")
    private User approvedBy;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    @Column(length = 500)
    private String notes;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Version
    @Column(nullable = false)
    private Long version;
}
