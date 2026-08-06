package com.pointagepro.payroll.entity;

import com.pointagepro.contract.entity.SalaryComponentType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "payroll_item_components")
@Getter
@Setter
@NoArgsConstructor
public class PayrollItemComponent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payroll_item_id", nullable = false)
    private PayrollItem payrollItem;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "component_type_id")
    private SalaryComponentType componentType;

    @Column(nullable = false, length = 100)
    private String label;

    @Column(nullable = false, length = 20)
    private String category;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Column(name = "is_percentage", nullable = false)
    private Boolean isPercentage = false;

    @Column(name = "percentage_value", precision = 5, scale = 2)
    private BigDecimal percentageValue;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;
}
