package com.pointagepro.payroll.engine;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Pure payroll math engine (PAYROLL_BUSINESS_RULES.md §4). No Spring context,
 * no database access - fully unit-testable.
 *
 * <p>Rules implemented:
 * <ul>
 *   <li>Day-based pro-rata: {@code earnedBase = base x present/scheduled}.</li>
 *   <li>Flat overtime: {@code overtimeMinutes / 60 x hourlyRate x multiplier},
 *       {@code hourlyRate = base / monthlyHours}.</li>
 *   <li>Late deduction: {@code lateMinutes / 60 x hourlyRate}.</li>
 *   <li>CNSS employee/employer rates (no ceiling unless provided).</li>
 *   <li>CSS employee rate.</li>
 *   <li>IRPP annualized on taxable base via brackets, /12, family deduction
 *       10%/unit (children + disabled capped at 3, CHEF_DE_FAMILLE with a
 *       non-working spouse +2).</li>
 *   <li>Net = gross - CNSS salarial - IRPP - CSS.</li>
 *   <li>SMIG check (informational warning only).</li>
 * </ul>
 *
 * <p>All money inputs are unrounded; stored money fields are rounded to 2 dp
 * (HALF_UP). Taxable bases are floored at zero so a fully-deducted employee
 * never produces negative contributions.
 */
public final class PayrollCalculator {

    private static final int MONEY_SCALE = 2;
    private static final RoundingMode ROUNDING = RoundingMode.HALF_UP;
    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);
    private static final BigDecimal SIXTY = BigDecimal.valueOf(60);
    private static final BigDecimal TWELVE = BigDecimal.valueOf(12);
    private static final BigDecimal FAMILY_DEDUCTION_RATE = BigDecimal.valueOf(0.10);
    private static final int MAX_FAMILY_UNITS = 3;
    private static final int FAMILY_UNITS_SPOUSE = 2;
    private static final String CHEF_DE_FAMILLE = "CHEF_DE_FAMILLE";

    private PayrollCalculator() {
    }

    public static PayrollCalculation calculate(PayrollEngineInput in) {
        BigDecimal base = in.baseSalary();
        BigDecimal presenceFactor = factor(in.scheduledWorkdays(), in.presentDays());

        BigDecimal earnedBase = base.multiply(presenceFactor);
        BigDecimal hourlyRate = base.divide(in.monthlyHours(), 10, ROUNDING);

        // --- bonus and deduction components -------------------------------
        List<PayrollEngineInput.ComponentInput> components = in.components() == null
                ? List.of() : in.components();
        BigDecimal earnedBonuses = BigDecimal.ZERO;
        BigDecimal fixedDeductions = BigDecimal.ZERO;
        for (PayrollEngineInput.ComponentInput c : components) {
            BigDecimal earned = "DEDUCTION".equalsIgnoreCase(c.category())
                    ? deductionAmount(c, base)
                    : earnedAmount(c, earnedBase, presenceFactor);
            if ("DEDUCTION".equalsIgnoreCase(c.category())) {
                fixedDeductions = fixedDeductions.add(earned);
            } else {
                earnedBonuses = earnedBonuses.add(earned);
            }
        }

        // --- overtime and late --------------------------------------------
        BigDecimal overtimeAmount = BigDecimal.ZERO;
        if (in.overtimeEnabled() && in.overtimeMinutes() > 0) {
            overtimeAmount = BigDecimal.valueOf(in.overtimeMinutes())
                    .divide(SIXTY, 10, ROUNDING)
                    .multiply(hourlyRate)
                    .multiply(in.overtimeMultiplier());
        }
        BigDecimal lateDeduction = BigDecimal.valueOf(in.lateMinutes())
                .divide(SIXTY, 10, ROUNDING)
                .multiply(hourlyRate);

        // --- gross ----------------------------------------------------------
        BigDecimal gross = earnedBase.add(earnedBonuses).add(overtimeAmount)
                .subtract(lateDeduction).subtract(fixedDeductions);
        if (gross.signum() < 0) {
            gross = BigDecimal.ZERO;
        }

        // --- contribution bases (floored at zero) --------------------------
        BigDecimal cnssBase = subjectBase(earnedBase, overtimeAmount, components, true, presenceFactor);
        if (in.cnss() != null && in.cnss().ceiling() != null
                && cnssBase.compareTo(in.cnss().ceiling()) > 0) {
            cnssBase = in.cnss().ceiling();
        }
        BigDecimal cssBase = subjectBase(earnedBase, overtimeAmount, components, false, presenceFactor);

        BigDecimal cnssSalarial = BigDecimal.ZERO;
        BigDecimal cnssPatronal = BigDecimal.ZERO;
        if (in.cnss() != null) {
            cnssSalarial = applyRate(cnssBase, in.cnss().employeeRate());
            cnssPatronal = applyRate(cnssBase, in.cnss().employerRate());
        }
        BigDecimal css = applyRate(cssBase, in.css() == null ? null : in.css().employeeRate());

        // --- IRPP ------------------------------------------------------------
        BigDecimal irppBase = earnedBase.add(earnedBonuses).add(overtimeAmount)
                .subtract(cnssSalarial).subtract(css);
        if (irppBase.signum() < 0) {
            irppBase = BigDecimal.ZERO;
        }
        BigDecimal irpp = computeIrpp(irppBase, in.taxBrackets(), in.taxProfile());

        // --- net ---------------------------------------------------------------
        BigDecimal net = gross.subtract(cnssSalarial).subtract(irpp).subtract(css);
        if (net.signum() < 0) {
            net = BigDecimal.ZERO;
        }

        // --- SMIG informational warning --------------------------------------
        List<String> warnings = new ArrayList<>();
        if (in.smigMonthlyRate() != null && base.compareTo(in.smigMonthlyRate()) < 0) {
            warnings.add("Salaire de base inférieur au SMIG mensuel ("
                    + money(in.smigMonthlyRate()) + ")");
        }

        // --- component lines ----------------------------------------------------
        List<PayrollCalculation.ComponentResult> lines = new ArrayList<>();
        lines.add(new PayrollCalculation.ComponentResult(
                "BASE_SALARY", label(in.labels(), "base"), "BASE",
                money(earnedBase), false, null, 1));
        int order = 10;
        for (PayrollEngineInput.ComponentInput c : components) {
            if ("DEDUCTION".equalsIgnoreCase(c.category())) {
                continue;
            }
            lines.add(new PayrollCalculation.ComponentResult(
                    c.code(), c.label(), c.category(),
                    money(earnedAmount(c, earnedBase, presenceFactor)), c.isPercentage(),
                    c.percentageValue(), order++));
        }
        if (in.overtimeMinutes() > 0) {
            lines.add(new PayrollCalculation.ComponentResult(
                    "HEURES_SUP", label(in.labels(), "overtime"), "BONUS",
                    money(overtimeAmount), false, null, 20));
        }
        order = 30;
        for (PayrollEngineInput.ComponentInput c : components) {
            if (!"DEDUCTION".equalsIgnoreCase(c.category())) {
                continue;
            }
            lines.add(new PayrollCalculation.ComponentResult(
                    c.code(), c.label(), c.category(),
                    money(deductionAmount(c, base)), c.isPercentage(),
                    c.percentageValue(), order++));
        }
        if (in.absenceMinutes() > 0) {
            lines.add(new PayrollCalculation.ComponentResult(
                    "DEDUCTION_ABSENCE", label(in.labels(), "absence"), "DEDUCTION",
                    money(base.subtract(earnedBase)), false, null, 40));
        }
        if (in.lateMinutes() > 0) {
            lines.add(new PayrollCalculation.ComponentResult(
                    "DEDUCTION_RETARD", label(in.labels(), "late"), "DEDUCTION",
                    money(lateDeduction), false, null, 41));
        }

        return new PayrollCalculation(
                money(base),
                in.presentDays(),
                money(BigDecimal.valueOf(in.workedMinutes()).divide(SIXTY, 10, ROUNDING)),
                in.overtimeMinutes(),
                money(overtimeAmount),
                in.absenceMinutes(),
                money(base.subtract(earnedBase)),
                in.lateMinutes(),
                money(lateDeduction),
                money(gross),
                money(cnssSalarial),
                money(cnssPatronal),
                money(irpp),
                money(css),
                money(net),
                List.copyOf(lines),
                List.copyOf(warnings));
    }

    private static BigDecimal computeIrpp(BigDecimal irppBase,
                                          List<PayrollEngineInput.TaxBracketInput> brackets,
                                          PayrollEngineInput.TaxProfileInput profile) {
        if (irppBase.signum() <= 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal annual = irppBase.multiply(TWELVE);
        BigDecimal tax = BigDecimal.ZERO;
        if (brackets != null) {
            for (PayrollEngineInput.TaxBracketInput b : brackets) {
                BigDecimal taxable = annual.min(b.upper()).subtract(b.lower());
                if (taxable.signum() > 0) {
                    tax = tax.add(applyRate(taxable, b.ratePercent()));
                }
            }
        }
        BigDecimal monthly = tax.divide(TWELVE, 10, ROUNDING);

        int units = Math.min(MAX_FAMILY_UNITS,
                profile.numberOfChildren() + profile.numberOfDisabledChildren());
        if (CHEF_DE_FAMILLE.equalsIgnoreCase(profile.situationCode()) && !profile.spouseIsWorking()) {
            units += FAMILY_UNITS_SPOUSE;
        }
        if (units > 0) {
            BigDecimal deduction = monthly.multiply(FAMILY_DEDUCTION_RATE)
                    .multiply(BigDecimal.valueOf(units));
            monthly = monthly.subtract(deduction);
            if (monthly.signum() < 0) {
                monthly = BigDecimal.ZERO;
            }
        }
        return monthly;
    }

    private static BigDecimal subjectBase(BigDecimal earnedBase,
                                          BigDecimal overtimeAmount,
                                          List<PayrollEngineInput.ComponentInput> components,
                                          boolean cnss, BigDecimal presenceFactor) {
        BigDecimal sum = earnedBase.add(overtimeAmount);
        for (PayrollEngineInput.ComponentInput c : components) {
            boolean flagged = cnss ? c.isSubjectToCnss() : c.isSubjectToCss();
            if (!"DEDUCTION".equalsIgnoreCase(c.category()) && flagged) {
                sum = sum.add(earnedAmount(c, earnedBase, presenceFactor));
            }
        }
        if (sum.signum() < 0) {
            return BigDecimal.ZERO;
        }
        return sum;
    }

    private static BigDecimal earnedAmount(PayrollEngineInput.ComponentInput c,
                                           BigDecimal earnedBase, BigDecimal presenceFactor) {
        if (c.isPercentage()) {
            // percentage bonuses apply to earned_base, which already carries the factor
            return earnedBase.multiply(c.percentageValue()).divide(ONE_HUNDRED, 10, ROUNDING);
        }
        // fixed bonuses are pro-rated with the same day-based factor (rules §4.6)
        BigDecimal amount = c.amount() == null ? BigDecimal.ZERO : c.amount();
        return amount.multiply(presenceFactor);
    }

    /**
     * DEDUCTION-category component: full amount, not prorated (business rules
     * §4.6 "fixed_deductions" — an advance deduction is repaid in full).
     */
    private static BigDecimal deductionAmount(PayrollEngineInput.ComponentInput c, BigDecimal base) {
        if (c.isPercentage()) {
            return base.multiply(c.percentageValue()).divide(ONE_HUNDRED, 10, ROUNDING);
        }
        return c.amount() == null ? BigDecimal.ZERO : c.amount();
    }

    private static BigDecimal factor(int scheduledWorkdays, int presentDays) {
        if (scheduledWorkdays <= 0) {
            return BigDecimal.ONE;
        }
        return BigDecimal.valueOf(presentDays)
                .divide(BigDecimal.valueOf(scheduledWorkdays), 10, ROUNDING);
    }

    private static BigDecimal applyRate(BigDecimal base, BigDecimal rate) {
        if (rate == null) {
            return BigDecimal.ZERO;
        }
        return base.multiply(rate).divide(ONE_HUNDRED, 10, ROUNDING);
    }

    private static String label(PayrollEngineInput.DerivedLabels labels, String key) {
        if (labels == null) {
            return null;
        }
        return switch (key) {
            case "base" -> labels.base();
            case "overtime" -> labels.overtime();
            case "absence" -> labels.absence();
            case "late" -> labels.late();
            default -> null;
        };
    }

    private static BigDecimal money(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value.setScale(MONEY_SCALE, ROUNDING);
    }
}
