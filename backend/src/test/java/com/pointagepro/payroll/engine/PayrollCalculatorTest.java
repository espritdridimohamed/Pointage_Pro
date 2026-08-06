package com.pointagepro.payroll.engine;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import com.pointagepro.payroll.engine.PayrollEngineInput.CnssInput;
import com.pointagepro.payroll.engine.PayrollEngineInput.ComponentInput;
import com.pointagepro.payroll.engine.PayrollEngineInput.CssInput;
import com.pointagepro.payroll.engine.PayrollEngineInput.DerivedLabels;
import com.pointagepro.payroll.engine.PayrollEngineInput.TaxBracketInput;
import com.pointagepro.payroll.engine.PayrollEngineInput.TaxProfileInput;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Acceptance tests for the pure payroll engine (PAYROLL_BUSINESS_RULES.md §4):
 * day-based pro-rata, flat overtime, hourly late deduction, CNSS/CSS, IRPP
 * annualized brackets + family deduction, rounding, and the SMIG warning.
 * Rates mirror the 2026 seed (V2): CNSS 9.68%/16.57% (no ceiling), CSS 0.50%,
 * SMIG 524.954, standard 8-bracket scale.
 */
class PayrollCalculatorTest {

    private static List<TaxBracketInput> brackets2026() {
        return List.of(
                new TaxBracketInput(bd("0"), bd("5000"), bd("0")),
                new TaxBracketInput(bd("5000"), bd("20000"), bd("15")),
                new TaxBracketInput(bd("20000"), bd("30000"), bd("25")),
                new TaxBracketInput(bd("30000"), bd("50000"), bd("30")),
                new TaxBracketInput(bd("50000"), bd("60000"), bd("33")),
                new TaxBracketInput(bd("60000"), bd("80000"), bd("36")),
                new TaxBracketInput(bd("80000"), bd("150000"), bd("38")),
                new TaxBracketInput(bd("150000"), bd("999999999"), bd("40")));
    }

    private static final CnssInput CNSS = new CnssInput(bd("9.68"), bd("16.57"), null);
    private static final CssInput CSS = new CssInput(bd("0.50"));
    private static final TaxProfileInput SINGLE = new TaxProfileInput("CELIBATAIRE", false, 0, 0);
    private static final TaxProfileInput FAMILY = new TaxProfileInput("CHEF_DE_FAMILLE", false, 2, 0);
    private static final BigDecimal SMIG = bd("524.954");

    private static BigDecimal bd(String v) {
        return new BigDecimal(v);
    }

    private static InputBuilder input() {
        return new InputBuilder();
    }

    @Test
    void fullMonthPresentNoOvertime() {
        PayrollCalculation c = PayrollCalculator.calculate(input().build());

        assertThat(c.workDays()).isEqualTo(21);
        assertThat(c.workHours()).isEqualByComparingTo("168.00");
        assertThat(c.baseSalary()).isEqualByComparingTo("1500.00");
        assertThat(c.overtimeAmount()).isEqualByComparingTo("0.00");
        assertThat(c.absenceDeduction()).isEqualByComparingTo("0.00");
        assertThat(c.lateDeduction()).isEqualByComparingTo("0.00");
        assertThat(c.grossSalary()).isEqualByComparingTo("1500.00");
        assertThat(c.cnssSalarial()).isEqualByComparingTo("145.20");
        assertThat(c.cnssPatronal()).isEqualByComparingTo("248.55");
        assertThat(c.css()).isEqualByComparingTo("7.50");
        assertThat(c.irpp()).isEqualByComparingTo("139.60");
        assertThat(c.netSalary()).isEqualByComparingTo("1207.71");
        assertThat(c.warnings()).isEmpty();
    }

    @Test
    void absenceProRataDeductsDays() {
        PayrollCalculation c = PayrollCalculator.calculate(input()
                .present(20).scheduled(21).absent(1).build());

        assertThat(c.workDays()).isEqualTo(20);
        assertThat(c.absenceDeduction()).isEqualByComparingTo("71.43");
        assertThat(c.grossSalary()).isEqualByComparingTo("1428.57");
        assertThat(c.cnssSalarial()).isEqualByComparingTo("138.29");
        assertThat(c.css()).isEqualByComparingTo("7.14");
        assertThat(c.irpp()).isEqualByComparingTo("129.97");
        assertThat(c.netSalary()).isEqualByComparingTo("1153.17");
        assertThat(c.components()).anyMatch(line -> "DEDUCTION_ABSENCE".equals(line.code())
                && line.amount().compareTo(bd("71.43")) == 0);
    }

    @Test
    void overtimeIsFlatAtMultiplier() {
        PayrollCalculation c = PayrollCalculator.calculate(input()
                .overtime(240).build());

        assertThat(c.overtimeMinutes()).isEqualTo(240);
        assertThat(c.overtimeAmount()).isEqualByComparingTo("49.45");
        assertThat(c.grossSalary()).isEqualByComparingTo("1549.45");
        assertThat(c.cnssSalarial()).isEqualByComparingTo("149.99");
        assertThat(c.css()).isEqualByComparingTo("7.75");
        assertThat(c.irpp()).isEqualByComparingTo("146.26");
        assertThat(c.netSalary()).isEqualByComparingTo("1245.46");
        assertThat(c.components()).anyMatch(line -> "HEURES_SUP".equals(line.code())
                && line.amount().compareTo(bd("49.45")) == 0);
    }

    @Test
    void overtimeDisabledYieldsZero() {
        PayrollCalculation c = PayrollCalculator.calculate(input()
                .overtime(240).overtimeEnabled(false).build());

        assertThat(c.overtimeAmount()).isEqualByComparingTo("0.00");
        assertThat(c.grossSalary()).isEqualByComparingTo("1500.00");
    }

    @Test
    void lateDeductionIsHourly() {
        PayrollCalculation c = PayrollCalculator.calculate(input()
                .late(45).build());

        assertThat(c.lateMinutes()).isEqualTo(45);
        assertThat(c.lateDeduction()).isEqualByComparingTo("7.42");
        assertThat(c.grossSalary()).isEqualByComparingTo("1492.58");
        assertThat(c.netSalary()).isEqualByComparingTo("1200.29");
    }

    @Test
    void fixedBonusIsProRatedAndTaxed() {
        PayrollCalculation c = PayrollCalculator.calculate(input()
                .present(20).scheduled(21)
                .components(List.of(new ComponentInput(
                        "PRIME_TRANSPORT", "Transport prime", "BONUS",
                        bd("100"), false, null, true, true, false))).build());

        assertThat(c.grossSalary()).isEqualByComparingTo("1523.81");
        assertThat(c.cnssSalarial()).isEqualByComparingTo("147.50");
        assertThat(c.css()).isEqualByComparingTo("7.14");
        assertThat(c.irpp()).isEqualByComparingTo("142.87");
        assertThat(c.netSalary()).isEqualByComparingTo("1226.29");
        assertThat(c.components()).anyMatch(line -> "PRIME_TRANSPORT".equals(line.code())
                && line.amount().compareTo(bd("95.24")) == 0);
    }

    @Test
    void fixedDeductionIsFullNotProRated() {
        PayrollCalculation c = PayrollCalculator.calculate(input()
                .present(20).scheduled(21)
                .components(List.of(new ComponentInput(
                        "DEDUCTION_AVANCE", "Avance", "DEDUCTION",
                        bd("200"), false, null, false, false, false))).build());

        assertThat(c.grossSalary()).isEqualByComparingTo("1228.57");
        assertThat(c.netSalary()).isEqualByComparingTo("953.17");
        assertThat(c.components()).anyMatch(line -> "DEDUCTION_AVANCE".equals(line.code())
                && line.amount().compareTo(bd("200.00")) == 0);
    }

    @Test
    void irppFamilyDeductionReducesTax() {
        PayrollCalculation full = PayrollCalculator.calculate(input().build());
        PayrollCalculation family = PayrollCalculator.calculate(input().profile(FAMILY).build());

        // 4 units (2 children + CHEF_DE_FAMILLE non-working spouse) -> -40%
        assertThat(family.irpp()).isEqualByComparingTo("83.76");
        assertThat(family.irpp()).isLessThan(full.irpp());
    }

    @Test
    void irppChildrenCappedAtThreeUnits() {
        TaxProfileInput many = new TaxProfileInput("MARIE", true, 4, 2);
        PayrollCalculation c = PayrollCalculator.calculate(input().profile(many).build());

        // children+disabled capped at 3 -> -30%
        assertThat(c.irpp()).isEqualByComparingTo("97.72");
    }

    @Test
    void zeroTaxableBaseYieldsNoContributions() {
        PayrollCalculation c = PayrollCalculator.calculate(input()
                .scheduled(21).present(0).components(List.of(new ComponentInput(
                        "DEDUCTION_AVANCE", "Avance", "DEDUCTION",
                        bd("5000"), false, null, false, false, false))).build());

        assertThat(c.grossSalary()).isEqualByComparingTo("0.00");
        assertThat(c.cnssSalarial()).isEqualByComparingTo("0.00");
        assertThat(c.irpp()).isEqualByComparingTo("0.00");
        assertThat(c.netSalary()).isEqualByComparingTo("0.00");
    }

    @Test
    void cnssCeilingCapsSubjectBase() {
        CnssInput capped = new CnssInput(bd("9.68"), bd("16.57"), bd("1000"));
        PayrollCalculation c = PayrollCalculator.calculate(input().cnss(capped).build());

        assertThat(c.cnssSalarial()).isEqualByComparingTo("96.80");
        assertThat(c.cnssPatronal()).isEqualByComparingTo("165.70");
    }

    @Test
    void smigBelowBaseYieldsWarning() {
        PayrollCalculation c = PayrollCalculator.calculate(input().base("400").build());

        assertThat(c.warnings()).anyMatch(w -> w.contains("SMIG"));
    }

    @Test
    void workHoursRoundedFromMinutes() {
        PayrollCalculation c = PayrollCalculator.calculate(input().worked(10079).build());
        assertThat(c.workHours()).isEqualByComparingTo("167.98");
    }

    @Test
    void componentLinesAreOrderedForThePayslip() {
        PayrollCalculation c = PayrollCalculator.calculate(input()
                .present(20).scheduled(21).absent(1)
                .overtime(60).late(15)
                .components(List.of(
                        new ComponentInput("PRIME_TRANSPORT", "Transport prime", "BONUS",
                                bd("40"), false, null, true, true, false),
                        new ComponentInput("DEDUCTION_AVANCE", "Avance", "DEDUCTION",
                                bd("100"), false, null, false, false, false)))
                .build());

        List<Integer> orders = c.components().stream().map(PayrollCalculation.ComponentResult::sortOrder).toList();
        assertThat(orders).isSorted();
        assertThat(c.components()).extracting(PayrollCalculation.ComponentResult::code)
                .containsExactly("BASE_SALARY", "PRIME_TRANSPORT", "HEURES_SUP",
                        "DEDUCTION_AVANCE", "DEDUCTION_ABSENCE", "DEDUCTION_RETARD");
    }

    /** Mutable builder around the immutable {@link PayrollEngineInput} record. */
    private static final class InputBuilder {
        private String base = "1500";
        private String monthly = "151.67";
        private boolean overtimeEnabled = true;
        private String overtimeMultiplier = "1.25";
        private int scheduled = 21;
        private int present = 21;
        private int worked = 10080;
        private int overtime = 0;
        private int late = 0;
        private int absent = 0;
        private List<ComponentInput> components = List.of();
        private CnssInput cnss = CNSS;
        private CssInput css = CSS;
        private List<TaxBracketInput> brackets = brackets2026();
        private TaxProfileInput profile = SINGLE;

        InputBuilder present(int present) { this.present = present; return this; }
        InputBuilder scheduled(int scheduled) { this.scheduled = scheduled; return this; }
        InputBuilder worked(int worked) { this.worked = worked; return this; }
        InputBuilder overtime(int overtime) { this.overtime = overtime; return this; }
        InputBuilder late(int late) { this.late = late; return this; }
        InputBuilder absent(int absent) { this.absent = absent; return this; }
        InputBuilder base(String base) { this.base = base; return this; }
        InputBuilder overtimeEnabled(boolean enabled) { this.overtimeEnabled = enabled; return this; }
        InputBuilder components(List<ComponentInput> components) { this.components = components; return this; }
        InputBuilder cnss(CnssInput cnss) { this.cnss = cnss; return this; }
        InputBuilder profile(TaxProfileInput profile) { this.profile = profile; return this; }

        PayrollEngineInput build() {
            return new PayrollEngineInput(
                    bd(base), bd(monthly), overtimeEnabled, bd(overtimeMultiplier),
                    scheduled, present, worked, overtime, late, absent, components,
                    cnss, css, brackets, profile, SMIG,
                    new PayrollEngineInput.DerivedLabels(
                            "Base salary", "Overtime", "Absence deduction", "Late deduction"));
        }
    }
}
