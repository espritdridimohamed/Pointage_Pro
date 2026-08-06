package com.pointagepro.legal.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "tax_brackets")
@Getter
@Setter
@NoArgsConstructor
public class TaxBracket {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Integer year;

    @Column(name = "bracket_order", nullable = false)
    private Integer bracketOrder;

    @Column(name = "lower_bound", nullable = false, precision = 10, scale = 2)
    private BigDecimal lowerBound;

    @Column(name = "upper_bound", nullable = false, precision = 12, scale = 2)
    private BigDecimal upperBound;

    @Column(name = "rate_percent", nullable = false, precision = 5, scale = 2)
    private BigDecimal ratePercent;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
