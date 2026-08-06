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
public class ContractResponse {

    private Long id;
    private Long employeeId;
    private String employeeName;
    private String contractTypeCode;
    private String contractType;
    private String statusCode;
    private String status;
    private LocalDate startDate;
    private LocalDate endDate;
    private LocalDate probationEndDate;
    private Long locationId;
    private String locationName;
    private BigDecimal workingHoursPerDay;
    private Integer workingDaysPerWeek;
    private Integer noticePeriodDays;
    private String attachmentPath;
    private BigDecimal baseSalary;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
