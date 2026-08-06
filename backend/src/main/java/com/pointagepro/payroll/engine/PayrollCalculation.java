package com.pointagepro.payroll.engine;

import java.math.BigDecimal;
import java.util.List;

/**
 * Result of one employee's payroll computation: the stored item fields plus the
 * payslip component lines. Money fields are rounded to 2 dp (HALF_UP).
 */
public record PayrollCalculation(
        BigDecimal baseSalary,
        int workDays,
        BigDecimal workHours,
        int overtimeMinutes,
        BigDecimal overtimeAmount,
        int absenceMinutes,
        BigDecimal absenceDeduction,
        int lateMinutes,
        BigDecimal lateDeduction,
        BigDecimal grossSalary,
        BigDecimal cnssSalarial,
        BigDecimal cnssPatronal,
        BigDecimal irpp,
        BigDecimal css,
        BigDecimal netSalary,
        List<ComponentResult> components,
        List<String> warnings) {

    /** One payslip line (category BASE / BONUS / DEDUCTION). */
    public record ComponentResult(
            String code,
            String label,
            String category,
            BigDecimal amount,
            boolean isPercentage,
            BigDecimal percentageValue,
            int sortOrder) {
    }
}
