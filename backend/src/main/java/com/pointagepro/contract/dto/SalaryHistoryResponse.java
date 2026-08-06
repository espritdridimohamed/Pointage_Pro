package com.pointagepro.contract.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
public class SalaryHistoryResponse {

    private Long id;
    private Long employeeId;
    private Long contractId;
    private BigDecimal oldAmount;
    private BigDecimal newAmount;
    private LocalDate changeDate;
    private String reason;
    private Long changedBy;
    private LocalDateTime createdAt;
}
