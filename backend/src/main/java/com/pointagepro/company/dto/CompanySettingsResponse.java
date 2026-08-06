package com.pointagepro.company.dto;

import com.pointagepro.company.entity.CompanySettings;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
public class CompanySettingsResponse {

    private Integer fiscalYearStartMonth;
    private BigDecimal weeklyWorkingHours;
    private BigDecimal monthlyWorkingHours;
    private Boolean overtimeEnabled;
    private BigDecimal overtimeRateMultiplier;
    private Boolean hoursNettingEnabled;

    public static CompanySettingsResponse from(CompanySettings s) {
        CompanySettingsResponse dto = new CompanySettingsResponse();
        dto.setFiscalYearStartMonth(s.getFiscalYearStartMonth());
        dto.setWeeklyWorkingHours(s.getWeeklyWorkingHours());
        dto.setMonthlyWorkingHours(s.getMonthlyWorkingHours());
        dto.setOvertimeEnabled(s.getOvertimeEnabled());
        dto.setOvertimeRateMultiplier(s.getOvertimeRateMultiplier());
        dto.setHoursNettingEnabled(s.getHoursNettingEnabled());
        return dto;
    }
}
