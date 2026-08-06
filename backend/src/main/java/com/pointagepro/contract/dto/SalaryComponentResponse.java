package com.pointagepro.contract.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
public class SalaryComponentResponse {

    private Long id;
    private String componentTypeCode;
    private String componentType;
    private String category;
    private String label;
    private BigDecimal amount;
    private Boolean isPercentage;
    private BigDecimal percentageValue;
    private LocalDate startDate;
    private LocalDate endDate;
    private Boolean isActive;
}
