package com.pointagepro.payroll.engine;

import java.math.BigDecimal;
import java.util.List;

/**
 * Immutable input of one employee's monthly payroll computation
 * (PAYROLL_BUSINESS_RULES.md §4). All figures come from the frozen attendance
 * snapshots, the contract's salary components and the versioned legal rates.
 */
public record PayrollEngineInput(
        BigDecimal baseSalary,
        BigDecimal monthlyHours,
        boolean overtimeEnabled,
        BigDecimal overtimeMultiplier,
        int scheduledWorkdays,
        int presentDays,
        int workedMinutes,
        int overtimeMinutes,
        int lateMinutes,
        int absenceMinutes,
        List<ComponentInput> components,
        CnssInput cnss,
        CssInput css,
        List<TaxBracketInput> taxBrackets,
        TaxProfileInput taxProfile,
        BigDecimal smigMonthlyRate,
        DerivedLabels labels) {

    /**
     * One salary component snapshot (earned base / bonus / deduction) with its
     * taxation flags. {@code amount} is the contractual monthly figure; a
     * percentage component uses {@code percentageValue} against the earned base.
     */
    public record ComponentInput(
            String code,
            String label,
            String category,
            BigDecimal amount,
            boolean isPercentage,
            BigDecimal percentageValue,
            boolean isSubjectToCnss,
            boolean isSubjectToIrpp,
            boolean isSubjectToCss) {
    }

    /** CNSS rates for the period year; {@code ceiling} may be null (uncapped). */
    public record CnssInput(BigDecimal employeeRate, BigDecimal employerRate, BigDecimal ceiling) {
    }

    /** CSS rates for the period year (employee side is deducted from net). */
    public record CssInput(BigDecimal employeeRate) {
    }

    /** One IRPP annual bracket (lower, upper, rate %). */
    public record TaxBracketInput(BigDecimal lower, BigDecimal upper, BigDecimal ratePercent) {
    }

    /** IRPP family situation from employee_tax_profiles (standard rule §4.7). */
    public record TaxProfileInput(
            String situationCode,
            boolean spouseIsWorking,
            int numberOfChildren,
            int numberOfDisabledChildren) {
    }

    /** Labels for the derived payslip lines. */
    public record DerivedLabels(String base, String overtime, String absence, String late) {
    }
}
