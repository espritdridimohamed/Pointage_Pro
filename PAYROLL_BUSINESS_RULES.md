# Payroll Management — Business Rules (Module 5, v1.0)

Status: **frozen (approved)** — 2026-08-06. Decisions finalized via Q&A:
day-based absence pro-rata, flat overtime multiplier from `company_settings`,
standard Tunisian IRPP family deduction (10% per unit, spouse as 2 units),
single-step VALIDATE/APPROVE/PAY by any permission holder, recompute allowed in
`DRAFT`/`COMPUTED` and frozen from `VALIDATED`, mid-month hires/terminations
included with day pro-rata.

This document follows the discipline of modules 3 and 4: rules are frozen
first, then `PAYROLL_API_CONTRACT.md` and the implementation match them exactly.
Nothing is implemented before this design is approved.

---

## 1. Scope

Backend payroll module (`com.pointagepro.payroll`): monthly payroll runs with a
frozen attendance-facts snapshot, salary component resolution, Tunisian legal
math (CNSS, IRPP, CSS), a lifecycle `DRAFT → COMPUTED → VALIDATED → APPROVED →
PAID`, payslips, and the permission gates seeded in V2. The module consumes the
existing V1/V2/V3/V9/V10 schema; one small migration (`V15`) adds
`payroll_attendance_snapshots.is_paid_leave` and optimistic locking on
`payrolls`. Legal rates come from the versioned tables (`tax_brackets`,
`cnss_rates`, `css_rates`, `smig_values`, `family_allowances`), all seeded for
2026. No engine change, no leave-module change, no frontend in this scope.

## 2. Lifecycle and terminality

- Lifecycle: `DRAFT → COMPUTED → VALIDATED → APPROVED → PAID`; `CANCELLED` is
  entered only **from** `DRAFT` or `COMPUTED`.
- Status lives on `payrolls.status_id`. There is **no** approvals chain for
  payroll: `approved_by`/`approved_at`/`paid_at` on `payrolls` record the actors
  (single-step model, frozen decision).
- **Immutability:** once `VALIDATED`, a run is frozen. Only forward transitions
  are legal (`validate` on `COMPUTED`, `approve` on `VALIDATED`, `pay` on
  `APPROVED`). `compute`/`recompute`/`cancel` on a frozen run → **409**.
- This matches the frozen-month guard already used by modules 3/4: a calendar
  month is "frozen" when a payroll with status `VALIDATED`/`APPROVED`/`PAID`
  exists for it — attendance rewrites are then rejected there.
- Transitions are race-safe via `@Version` on `Payroll` (V15) →
  `ObjectOptimisticLockingFailureException` → 409.

## 3. Run creation (`POST /payrolls`)

- Permission `payroll.run`. Company = the caller's company
  (`currentUserService.requireCompany(user)`), consistent with all modules.
- Body: `periodYear`, `periodMonth` (1–12), `notes` (optional, ≤ 500).
- Period uniqueness per company:
  - existing **`CANCELLED`** run for the period → **reopened** to `DRAFT`
    (totals reset to 0, items/snapshots/payslips deleted), 200;
  - existing **`DRAFT`** run → returned as-is (idempotent), 200;
  - existing `COMPUTED`/`VALIDATED`/`APPROVED`/`PAID` → **409** (recompute or
    cancel it instead; a frozen run can never be replaced).
- A new run is created empty in `DRAFT` (`run_date = today`, all totals 0,
  `employee_count = 0`). Nothing is read and nothing is computed yet.
- Validation (400): `periodYear`/`periodMonth` missing or out of range.

## 4. Compute engine (`POST /payrolls/{id}/compute`) — freeze + arithmetic

Permission `payroll.run`. Idempotent and safe to re-run while the run is
`DRAFT` or `COMPUTED`. The run must be in `DRAFT`/`COMPUTED` (frozen → 409).
The period must not be entirely in the future (`P.first > today` → 409).

### 4.1 Period and employee eligibility

- `P = [P.first, P.last]` = first/last calendar day of `(periodYear, periodMonth)`.
- **Cut-off:** only days `≤ today` participate; later days of an in-progress
  month are excluded from all denominators and never counted as absence. A
  mid-month run therefore produces partial figures — the response carries a
  warning.
- An employee is **eligible** iff they have a contract (`employee_contracts`)
  with status `ACTIVE` overlapping `P`:
  `start_date ≤ P.last AND (end_date IS NULL OR end_date ≥ P.first)`.
  Multiple overlapping contracts → the one with the greatest
  `start_date ≤ P.last` wins (the contract effective at the end of the period).
  Mid-month hires/terminations are included: the employee's **window** is
  `[max(contract.start_date, P.first), min(contract.end_date, P.last, today)]`
  (frozen decision).
- **Excluded employees** (listed as warnings, never as items): no active
  contract in `P`, or no `BASE_SALARY` component effective at `P.last`, or zero
  scheduled workdays in the window.

### 4.2 Day resolution and scheduled workdays

For every date in the employee's window, resolve the day exactly like the
attendance engine (schedule via `employee_schedules` + `work_schedule_lines`,
`holidays` table for legal/company holidays):

- `WORKDAY` — weekday `is_workday = 1` and not a holiday;
- `WEEKEND` / `HOLIDAY` / `NOT_SCHEDULED` — never counted.

`scheduled_workdays` = number of `WORKDAY` days in the window (used as the
denominator for base pro-rata). If `scheduled_workdays == 0` the employee is
excluded (warning).

### 4.3 Freeze step (facts snapshot)

For each day in the window, ensure an `attendance_summary` row exists
(compute-on-miss via the existing engine day materialization — the run always
sees the latest facts), then copy verbatim into
`payroll_attendance_snapshots`: day/schedule/status, `first_in`/`last_out`,
`worked/late/early_exit/missing/overtime/netted/adjustment` minutes,
`is_weekend`, `is_holiday`, `scheduled_start_time`/`scheduled_end_time`/
`scheduled_break_minutes` (resolved schedule line), `source_summary_id`, and
the new **`is_paid_leave`** (V15):

- for a day whose status is `LEAVE`, look up the covering `APPROVED` leave
  request (overlapping `[work_date, work_date]`, `status APPROVED`) and take
  its `leave_type.is_paid`; any paid covering request ⇒ `is_paid_leave = 1`;
- otherwise `0`.

The snapshot is thereafter self-contained (per the V9 contract): the run never
reads `attendance_summary` again and later recomputes cannot alter a frozen run.

### 4.4 Presence classification (WORKDAY days only)

- **paid day** — status ∈ {`PRESENT`, `LATE`, `HALF_DAY`, `ADJUSTED`}, or
  status `LEAVE` with `is_paid_leave = 1`;
- **absent day** — status `ABSENT`, or status `LEAVE` with `is_paid_leave = 0`
  (unpaid leave).
- `present_days` = paid days; `absent_days = scheduled_workdays − present_days`.
- `WEEKEND`/`HOLIDAY`/`NOT_SCHEDULED` days are never counted either way.
- **Frozen limitation:** `HALF_DAY` counts as a full paid day (day-based
  model); its `missing_minutes` are informational only. Partial-day shortfall
  is otherwise caught by the late deduction (§4.6). Early exits
  (`early_exit_minutes`) are not deducted in v1 (informational).

### 4.5 Base salary and components

- `hourly_rate = base_salary / company_settings.monthly_working_hours` (151.67).
- `presence_factor = present_days / scheduled_workdays`.
- `earned_base = base_salary × presence_factor`.
- `base_salary` = the `BASE_SALARY` component of the chosen contract effective
  at `P.last` (`start_date ≤ P.last AND (end_date IS NULL OR end_date ≥ P.first)
  AND is_active = 1`). Missing ⇒ employee excluded (warning).
- Bonus/deduction components of the contract effective at `P.last` are
  snapshotted as `payroll_item_components`:
  - fixed amount → `amount × presence_factor`;
  - percentage → `percentage_value% × earned_base`;
  - category and CNSS/IRPP/CSS flags come from `salary_component_types`.
- Component rows persisted per item: `BASE_SALARY` (earned_base), each bonus
  (earned), each deduction (earned, category `DEDUCTION`), plus derived rows
  `HEURES_SUP` (overtime amount, `BONUS`), `DEDUCTION_ABSENCE` (absence
  deduction, `DEDUCTION`), `DEDUCTION_RETARD` (late deduction, `DEDUCTION`) —
  giving a complete payslip line list.

### 4.6 Line arithmetic (all money 2 dp, HALF_UP, at the end)

| Field | Formula |
|---|---|
| `base_salary` | monthly base (reference) |
| `work_days` | `present_days` |
| `work_hours` | Σ `worked_minutes` / 60 |
| `overtime_minutes` | Σ `overtime_minutes` |
| `overtime_amount` | `overtime_enabled` ? `overtime_minutes/60 × hourly_rate × overtime_rate_multiplier` : 0 (flat 1.25) |
| `absence_minutes` | Σ `missing_minutes` (informational; unpaid leave adds 0 minutes) |
| `absence_deduction` | `base_salary × absent_days / scheduled_workdays` |
| `late_minutes` | Σ `late_minutes` |
| `late_deduction` | `late_minutes/60 × hourly_rate` |
| `earned_bonuses` | Σ fixed `× presence_factor` + Σ percentage `× earned_base` |
| `fixed_deductions` | Σ DEDUCTION-category amounts (full, not prorated) |
| `gross_salary` | `earned_base + earned_bonuses + overtime_amount − late_deduction − fixed_deductions` |

### 4.7 Legal contributions (rates from `_rates` tables of `periodYear`, 2026)

- **CNSS** — subject base `S_cnss` = `earned_base` + Σ CNSS-flagged bonuses +
  overtime (no ceiling in 2026 seed; if `ceiling_amount` set, cap `S_cnss`):
  - `cnss_salarial = S_cnss × employee_rate` (9.68%);
  - `cnss_patronal = S_cnss × employer_rate` (16.57%) — informational employer
    cost, not in net, not stored per item.
- **CSS** — `S_css` = `earned_base` + Σ CSS-flagged bonuses + overtime:
  - `css_salarial = S_css × employee_rate` (0.50% in 2026);
  - employer 1.00% informational.
- **IRPP** (annual progressive scale, monthly prepayment):
  - `base_irpp = earned_base + Σ IRPP-flagged bonuses + overtime − cnss_salarial − css_salarial`;
  - `annual = base_irpp × 12`; if `annual ≤ 0` → 0;
  - `tax = Σ_bracket max(0, min(annual, upper) − lower) × rate_percent` over
    `tax_brackets(periodYear)` ordered by `bracket_order`;
  - `monthly_irpp = tax / 12`.
- **IRPP family deduction** (standard Tunisian rule, frozen decision):
  - profile = `employee_tax_profiles` effective at `P.last` (latest
    `valid_from` in window);
  - `children_units = min(3, number_of_children + number_of_disabled_children)`;
  - `spouse_units = (tax_situation = CHEF_DE_FAMILLE AND spouse_is_working = 0) ? 2 : 0`;
  - `deduction = monthly_irpp × 10% × (children_units + spouse_units)`;
  - `irpp = max(0, monthly_irpp − deduction)`.
- **Net:**
  - `net_salary = gross_salary − cnss_salarial − irpp − css_salarial`.
- **SMIG / family allowances:** informational only in v1 — a base below the
  SMIG monthly rate or a zero `family_allowances.amount_per_child` yields a
  warning, never a correction.

### 4.8 Run totals

`employee_count` = item count; `total_gross`/`total_cnss`/`total_irpp`/
`total_css`/`total_net` = Σ of the rounded item values; `total_deductions` = Σ
(`absence_deduction` + `late_deduction` + DEDUCTION-category components).
`run_date = today`. The run lands in **`COMPUTED`**. Audit action
`PAYROLL_RUN`.

### 4.9 Warnings

The compute response includes a `warnings` list (per excluded employee and per
informational note: no contract / no base salary / no scheduled workdays /
mid-month partial run / below-SMIG base / zero family-allowance amount). The
count is recorded in `payrolls.notes`.

## 5. Recompute

Identical to §4 (delete existing items + snapshots + payslips, re-freeze,
re-compute), allowed while the run is `DRAFT` or `COMPUTED` only. Frozen run →
**409**. Recomputation is the single correction mechanism before validation —
there is no edit endpoint.

## 6. Validate (`POST /payrolls/{id}/validate`)

- Permission `payroll.validate`. Only from `COMPUTED` → **`VALIDATED`**;
  any other state → 409. Audit `STATUS_CHANGE`. From this point the run is
  frozen (see §2).

## 7. Approve (`POST /payrolls/{id}/approve`)

- Permission `payroll.approve`. Only from `VALIDATED` → **`APPROVED`**;
  `approved_by`/`approved_at` = actor. In the same transaction, one
  **payslip** per item is created:
  `payslip_number = "PP-" + yyyyMM + "-" + 3-digit sequence within the run`
  (e.g. `PP-202608-001`), `issued_at = now`, `pdf_path` null (no PDF in v1).
- Audit `PAYROLL_APPROVE` + `STATUS_CHANGE`.

## 8. Pay (`POST /payrolls/{id}/pay`)

- Permission `payroll.pay`. Only from `APPROVED` → **`PAID`**; `paid_at` =
  now. Optional body `bankTransferRef` (≤ 50) → stored on the payroll items
  (`payroll_items.bank_transfer_ref`). Audit `PAYROLL_PAY` + `STATUS_CHANGE`.
- **PAID is fully terminal**: no transition out.

## 9. Cancel (`POST /payrolls/{id}/cancel`)

- Permission `payroll.cancel`. Only from `DRAFT` or `COMPUTED` →
  **`CANCELLED`**; items/snapshots/payslips deleted; totals reset. Frozen or
  terminal run → **409**. Audit `STATUS_CHANGE`.
- A `CANCELLED` run frees the period for a fresh draft (§3).

## 10. Permissions (V2 seeds)

| Action | Authority | Seeded roles |
|---|---|---|
| Create / compute / recompute | `payroll.run` | ACCOUNTANT, ADMIN |
| Validate | `payroll.validate` | ACCOUNTANT, ADMIN |
| Approve | `payroll.approve` | ACCOUNTANT, ADMIN |
| Pay | `payroll.pay` | ACCOUNTANT, ADMIN |
| Cancel | `payroll.cancel` | ACCOUNTANT, ADMIN |
| Read runs/items | `payroll.read` | ACCOUNTANT, ADMIN |
| Read payslips | `payslip.read` | ACCOUNTANT, ADMIN |

No separation-of-duties split between the ACCOUNTANT sub-actions (frozen
decision); the four gates exist so a company can later grant them to distinct
users.

## 11. Audit

`AuditService`: `CREATE` on run creation; `PAYROLL_RUN` on compute/recompute;
`STATUS_CHANGE` on validate/approve/pay/cancel; `PAYROLL_APPROVE` on approve;
`PAYROLL_PAY` on pay. Snapshots and item lines are the fact trail.

## 12. API surface (summary — full contract in `PAYROLL_API_CONTRACT.md`)

Base `/api/v1/payrolls`:

- `POST /payrolls` — create draft / reopen cancelled / idempotent draft
- `POST /payrolls/{id}/compute` — freeze + compute (also the recompute path)
- `POST /payrolls/{id}/validate` | `approve` | `pay` | `cancel`
- `GET /payrolls` — list (filters `year`, `month`, `status`)
- `GET /payrolls/{id}` — run summary with totals + warnings
- `GET /payrolls/{id}/items` — items with components
- `GET /payrolls/{id}/payslips` — payslips (`payslip.read`)
- `GET /payslips/{id}` — single payslip detail (`payslip.read`)

## 13. Schema change (V15)

```sql
ALTER TABLE payroll_attendance_snapshots
    ADD COLUMN is_paid_leave TINYINT(1) NOT NULL DEFAULT 0 AFTER status_id;
ALTER TABLE payrolls
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
UPDATE salary_component_types
   SET is_subject_to_css = 1
 WHERE code IN ('BASE_SALARY', 'HEURES_SUP');
```

`is_paid_leave` makes the snapshot self-contained for unpaid-leave handling;
`version` gives optimistic locking (module 3/4 precedent). The CSS flag fix
aligns the seed with the legal rule that the base salary and overtime are
subject to the 2026 CSS levy.

## 14. Out of scope (v1)

- PDF generation / payslip emailing (`pdf_path`/`sent_at` stay null).
- Progressive overtime (1.25/1.5/2.0) and night/holiday premiums beyond the
  flat multiplier.
- Minute-based partial-day absence; early-exit deduction.
- CNSS ceiling and family-allowance **payment** (rates present, amount 0).
- Transport-prime IRPP exemption; multi-contract mid-month salary change
  (single contract effective at `P.last`).
- Payroll corrections after validation (validated = immutable; correction via
  next-period adjustment).
- Employee self-service payslip access, legal-rates maintenance UI
  (`legal.read/write` seeded, endpoint deferred).
