package com.pointagepro.legal.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "css_rates")
@Getter
@Setter
@NoArgsConstructor
public class CssRate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private Integer year;

    @Column(name = "employee_rate", nullable = false, precision = 5, scale = 2)
    private BigDecimal employeeRate;

    @Column(name = "employer_rate", nullable = false, precision = 5, scale = 2)
    private BigDecimal employerRate;

    @Column(name = "active_from", nullable = false)
    private LocalDate activeFrom;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
