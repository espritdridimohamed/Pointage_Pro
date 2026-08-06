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
@Table(name = "smig_values")
@Getter
@Setter
@NoArgsConstructor
public class SmigValue {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private Integer year;

    @Column(name = "hourly_rate", precision = 6, scale = 3)
    private BigDecimal hourlyRate;

    @Column(name = "monthly_rate", precision = 10, scale = 3)
    private BigDecimal monthlyRate;

    @Column(name = "weekly_rate", precision = 10, scale = 3)
    private BigDecimal weeklyRate;

    @Column(name = "active_from", nullable = false)
    private LocalDate activeFrom;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
