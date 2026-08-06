package com.pointagepro.contract.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
public class ContractRequest {

    private String contractTypeCode;

    private String statusCode;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate startDate;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate endDate;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate probationEndDate;

    private Long locationId;

    private BigDecimal workingHoursPerDay;

    private Integer workingDaysPerWeek;

    private Integer noticePeriodDays;

    private String attachmentPath;
}
