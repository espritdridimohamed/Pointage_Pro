package com.pointagepro.company.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "company_settings")
@Getter
@Setter
@NoArgsConstructor
public class CompanySettings {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false, unique = true)
    private Company company;

    @Column(name = "fiscal_year_start_month", nullable = false)
    private Integer fiscalYearStartMonth = 1;

    @Column(name = "weekly_working_hours", nullable = false, precision = 5, scale = 2)
    private BigDecimal weeklyWorkingHours = new BigDecimal("40.00");

    @Column(name = "monthly_working_hours", nullable = false, precision = 5, scale = 2)
    private BigDecimal monthlyWorkingHours = new BigDecimal("151.67");

    @Column(name = "overtime_enabled", nullable = false)
    private Boolean overtimeEnabled = true;

    @Column(name = "overtime_rate_multiplier", nullable = false, precision = 5, scale = 2)
    private BigDecimal overtimeRateMultiplier = new BigDecimal("1.25");

    @Column(name = "hours_netting_enabled", nullable = false)
    private Boolean hoursNettingEnabled = false;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
