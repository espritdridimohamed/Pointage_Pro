# PointagePro — Attendance Engine Test Matrix

**Version:** 1.0 (pre-implementation contract)
**Purpose:** Defines every expected scenario and exact engine output **before** the attendance engine is implemented. Every row is a runnable acceptance test; the engine is complete only when all cases below pass.
**Basis:** `ATTENDANCE_BUSINESS_RULES.md` v1.0 + `POINTAGEPRO_DATABASE_DESIGN_PLAN.md` §7.5.

---

## 0. Conventions (apply to every scenario)

- **Standard schedule (default):** Monday–Friday `08:00–17:00`, `break_minutes=60` → **effective day = 480 min**. Saturday/Sunday `is_workday=0`.
- **Night shift example:** `22:00–06:00`, `break_minutes=0` → **effective day = 480 min**. Work date = shift **start** date.
- **Payroll example rate:** base 1 500.00 TND/month ÷ `monthly_working_hours` 151.67 → **hourly ≈ 9.89 TND**. OT multiplier **1.25**.
- **Units:** all minutes `INT`, no rounding. `hours_netting_enabled = OFF`.
- **Grace period:** 0 min. **Duplicate window:** 60 s.
- **Status resolution (first match wins, definitive v1):**
  1. approved leave covers date → `LEAVE`
  2. holiday → `HOLIDAY` (worked or not; worked minutes recorded as OT)
  3. non-workday of schedule → `WEEKEND` (worked or not; worked minutes recorded as OT) / no schedule at all → `NOT_SCHEDULED`
  4. scheduled workday, no events → `ABSENT`
  5. scheduled workday, exactly one event → `HALF_DAY` (**incomplete evidence, NOT half pay** — missing bound completed from the schedule; full computed day credited, scenarios 4–5)
  6. scheduled workday, ≥2 events → `PRESENT` / `LATE` (late if `late_minutes>0`) / `ADJUSTED` (if adjustment applied)
- **Definitions:**
  - `worked_minutes = (last_out − first_in) − break_minutes` (first IN / last OUT only; intermediate scans ignored), clamped ≥ 0.
  - `late_minutes = max(0, first_in − scheduled_start)`.
  - `early_exit_minutes = max(0, scheduled_end − last_out)` (only when a real last OUT exists).
  - `missing_minutes` = **absence only**; nonzero exclusively for `ABSENT` (full day) and `HALF_DAY` (unworked portion). `= max(0, effective_day − worked_minutes)` for those statuses. Early departure is **never** counted here (it lives in `early_exit_minutes`).
  - `overtime_minutes` = `max(0, last_out − scheduled_end)` on a workday (real OUT only); on `WEEKEND`/`HOLIDAY` days = **all** worked minutes.
  - **Missing bound completion:** single event → the missing bound is assumed from the schedule (`IN`→ assumed OUT at `end_time`; `OUT`→ assumed IN at `start_time`) and worked is still computed, but status is `HALF_DAY` (partial evidence).
- **Idempotency:** recomputing any date range from raw events + adjustments + leaves + holidays always yields the same summaries. No cumulative state.
- **Audit invariants asserted everywhere:** raw `attendance_events` never modified; applied adjustments never modified; summary only ever rewritten wholesale (`computed_at`, `recompute_reason`); payroll reads snapshots only.

### Summary table

| # | Scenario | Status | worked | missing | late | OT | Payroll impact |
|---|---|---|---|---|---|---|---|
| 1 | Normal IN/OUT | PRESENT | 480 | 0 | 0 | 0 | base only |
| 2 | Late arrival | LATE | 450 | 0 | 30 | 0 | −0.5 h → −4.95 TND |
| 3 | Early departure | PRESENT | 420 | 0 | 0 | 0 | none (auto) — flagged |
| 4 | Missing IN | HALF_DAY | 480 | 0 | 0 | 0 | none (OUT evidence) |
| 5 | Missing OUT | HALF_DAY | 480 | 0 | 0 | 0 | none (IN evidence) |
| 6 | No-scan absence | ABSENT | 0 | 480 | 0 | 0 | −8 h → −79.11 TND |
| 7 | Approved leave | LEAVE | 0 | 0 | 0 | 0 | paid normally |
| 8 | Rejected/pending leave | ABSENT | 0 | 480 | 0 | 0 | −8 h → −79.11 TND |
| 9 | Weekend work | WEEKEND | 240 | 0 | 0 | 240 | +4 h ×1.25 → +49.45 TND |
| 10 | Holiday work | HOLIDAY | 240 | 0 | 0 | 240 | +4 h ×1.25 → +49.45 TND |
| 11 | Night shift | PRESENT | 480 | 0 | 0 | 0 | base (+ PRIME_NUIT is separate) |
| 12 | Duplicate scans | PRESENT | 480 | 0 | 0 | 0 | base only |
| 13 | Offline replay | PRESENT | 480 | 0 | 0 | 0 | base only |
| 14 | Manual adjustment | ADJUSTED | 510 | 0 | 0 | 0 | +0.5 h → +4.95 TND |
| 15 | Approval workflow | ADJUSTED | 480 | 0 | 0 | 60 | +1 h ×1.25 → +12.36 TND |
| 16 | Workday overtime | PRESENT | 540 | 0 | 0 | 60 | +1 h ×1.25 → +12.36 TND |

---

## 1. Normal IN/OUT
- **Schedule:** Mon 08:00–17:00, break 60 (effective 480).
- **Input events:** `IN 08:00`, `OUT 17:00` (source TERMINAL).
- **Expected:** status `PRESENT`; `first_in=08:00`, `last_out=17:00`.
- **worked_minutes:** `(17:00−08:00)=540 − 60 = 480`.
- **missing_minutes:** 0. **late_minutes:** 0. **early_exit_minutes:** 0. **overtime_minutes:** 0.
- **Payroll impact:** base salary only; no deduction, no OT.
- **Idempotency:** recompute → identical row.

## 2. Late arrival
- **Schedule:** Mon 08:00–17:00, break 60.
- **Input events:** `IN 08:30`, `OUT 17:00`.
- **Expected:** status `LATE`; `first_in=08:30`, `last_out=17:00`.
- **worked_minutes:** `(17:00−08:30)=510 − 60 = 450`.
- **late_minutes:** `08:30 − 08:00 = 30`. **missing_minutes:** 0. **overtime_minutes:** 0.
- **Payroll impact:** `−30/60 × 9.89 = −4.95 TND` (deduction, `DEDUCTION_RETARD`).
- Note: late is not absence; day stays worked.

## 3. Early departure
- **Schedule:** Mon 08:00–17:00, break 60.
- **Input events:** `IN 08:00`, `OUT 16:00`.
- **Expected:** status `PRESENT`; `first_in=08:00`, `last_out=16:00`.
- **worked_minutes:** `(16:00−08:00)=480 − 60 = 420`.
- **early_exit_minutes:** `17:00 − 16:00 = 60`. **late_minutes:** 0. **missing_minutes:** 0. **overtime_minutes:** 0.
- **Payroll impact:** none automatic in v1. `missing_minutes = 0` (it is **absence-only**); the 60 early-exit minutes are tracked in `early_exit_minutes` for HR review — early departure never counts as absence.

## 4. Missing IN
- **Schedule:** Mon 08:00–17:00, break 60.
- **Input events:** `OUT 17:00` only (single event).
- **Expected:** status `HALF_DAY` (partial evidence); check-in **assumed at 08:00** (§2.4 — a valid OUT proves presence).
- **worked_minutes:** `(17:00−08:00)=540 − 60 = 480`. **late_minutes:** 0. **missing_minutes:** 0. **overtime_minutes:** 0.
- **Payroll impact:** none (full day credited on OUT evidence).

## 5. Missing OUT
- **Schedule:** Mon 08:00–17:00, break 60.
- **Input events:** `IN 08:00` only (single event).
- **Expected:** status `HALF_DAY` (partial evidence); check-out **assumed at 17:00** (§2.3).
- **worked_minutes:** `(17:00−08:00)=540 − 60 = 480`. **early_exit_minutes:** 0. **late_minutes:** 0. **missing_minutes:** 0. **overtime_minutes:** 0.
- **Payroll impact:** none (full day credited on IN evidence).

## 6. No-scan absence
- **Schedule:** Mon 08:00–17:00, break 60. No approved leave.
- **Input events:** none.
- **Expected:** status `ABSENT`; `first_in=NULL`, `last_out=NULL`.
- **worked_minutes:** 0. **missing_minutes:** 480 (full effective day). **late_minutes:** 0. **overtime_minutes:** 0.
- **Payroll impact:** `−480/60 × 9.89 = −79.11 TND` (deduction, `DEDUCTION_ABSENCE`).

## 7. Approved leave
- **Schedule:** Mon 08:00–17:00. Approved ANNUAL leave covers the date.
- **Input events:** none.
- **Expected:** status `LEAVE`; `worked=0`, `missing=0`, `late=0`, `overtime=0`.
- **Payroll impact:** none — paid normally (`leave_types.is_paid=1`); the day is not an absence.

## 8. Rejected / pending leave
- **Schedule:** Mon 08:00–17:00. Leave is `PENDING` or `REJECTED`.
- **Input events:** none.
- **Expected:** status `ABSENT`; `missing_minutes=480` (a non-approved leave does not protect the day).
- **Payroll impact:** `−79.11 TND` (`DEDUCTION_ABSENCE`).

## 9. Weekend work
- **Schedule:** Sat `is_workday=0`.
- **Input events:** `IN 08:00`, `OUT 12:00`.
- **Expected:** status `WEEKEND` (day class), `day_type_id=WEEKEND`, `is_weekend=1`; `worked_minutes=240`.
- **overtime_minutes:** **240** (all weekend minutes are OT). **missing_minutes:** 0. **late_minutes:** 0.
- **Payroll impact:** `+240/60 × 9.89 × 1.25 = +49.45 TND` (`HEURES_SUP`, multiplier 1.25).

## 10. Holiday work
- **Schedule:** Thu 2026-01-01 is a legal holiday (`holidays`, `is_recurring=1`); weekday normally `is_workday=1`.
- **Input events:** `IN 08:00`, `OUT 12:00`.
- **Expected:** status `HOLIDAY` (day class), `day_type_id=HOLIDAY`, `is_holiday=1`; `worked_minutes=240`.
- **overtime_minutes:** **240** (all holiday minutes are OT). **missing_minutes:** 0. **late_minutes:** 0.
- **Payroll impact:** `+240/60 × 9.89 × 1.25 = +49.45 TND` (`HEURES_SUP`).
- Note: HOLIDAY outranks WEEKEND when a holiday falls on a weekend. **UI must display "Holiday (worked)"** when `worked_minutes > 0` on a HOLIDAY day — never bare "Holiday" (same for WEEKEND with worked minutes).

## 11. Night shift
- **Schedule:** line `22:00–06:00`, break 0 (effective 480). Work date = shift start date (day D).
- **Input events:** `IN 22:00` (day D), `OUT 06:00` (day D+1).
- **Expected:** status `PRESENT`; summary key `(employee, day D)`; `first_in=22:00`, `last_out=06:00`.
- **worked_minutes:** `(06:00+24h − 22:00)=480 − 0 = 480`. **late/minus/missing/OT:** 0.
- **Payroll impact:** base only. Night premium (`PRIME_NUIT`) is a separate salary component, not an engine output. Night window 22:00–05:00 uses the same OT multiplier in v1.

## 12. Duplicate scans
- **Schedule:** Mon 08:00–17:00, break 60.
- **Input events:** `IN 08:00`, `IN 08:01` (duplicate < 60 s), `OUT 17:00`, `OUT 17:00` (double read).
- **Expected:** `first_in=08:00` (duplicate ignored), `last_out=17:00` (duplicate ignored); status `PRESENT`; `worked_minutes=480`.
- All duplicates remain stored as raw events; they never affect calculation.
- **Payroll impact:** base only.

## 13. Offline replay
- **Schedule:** Mon 08:00–17:00, break 60.
- **Input events (replayed):** device buffered offline `IN 08:00` (`external_ref …-00000045`) and `OUT 17:00` (`…-00000046`), replayed with their stored timestamps on reconnect.
- **Expected:** identical to scenario 1: `PRESENT`, `worked_minutes=480`, zeros.
- **Idempotency:** replaying `…-00000045` a second time is absorbed by `UNIQUE(terminal_id, external_ref)` → no new event, summary unchanged.
- **Drift guard:** if `|device time − server time| > 24 h` → `time_warning=1` on the event; event still stored and counted.
- **Payroll impact:** base only.

## 14. Manual adjustments
- **Schedule:** Mon 08:00–17:00, break 60.
- **Input events:** `IN 08:00`, `OUT 17:00` (PRESENT, 480). HR applies an approved `ADD_MINUTES 30` adjustment (reason mandatory).
- **Expected after recompute:** status `ADJUSTED`; `worked_minutes=510`; `adjustment_minutes=+30`; `computed_at`/`recompute_reason` set.
- **Variant `SET_ABSENT`:** flips the day to `ABSENT`, `missing_minutes=480`, `worked_minutes=0`.
- **Payroll impact:** `+30/60 × 9.89 = +4.95 TND` (or full absence deduction for SET_ABSENT).
- **Audit:** applied adjustment is immutable; raw events untouched.

## 15. Approval workflow
- **Schedule:** Mon 08:00–17:00, break 60.
- **Input events:** `IN 08:00`, `OUT 17:00` (PRESENT, 480). Employee requests `ADD_OVERTIME 60`.
- **Workflow:** adjustment `PENDING` + `approvals` step1 `MANAGER` (PENDING) → approve → step2 `HR` (PENDING) → approve → adjustment `APPLIED`.
- **Expected after apply + recompute:** status `ADJUSTED`; `overtime_minutes=60`; `adjustment_minutes=+60`; both `approvals` rows + last `approved_by`/`approved_at` recorded.
- **Reject path:** any step `REJECTED` → adjustment `REJECTED`; **no** summary change.
- **Self-approval:** nobody approves their own request; if creator is the dept manager, step 1 is skipped (HR-only).
- **Payroll impact (applied):** `+60/60 × 9.89 × 1.25 = +12.36 TND`.

## 16. Workday overtime
- **Schedule:** Mon 08:00–17:00, break 60.
- **Input events:** `IN 08:00`, `OUT 18:00`.
- **Expected:** status `PRESENT`; `first_in=08:00`, `last_out=18:00`.
- **worked_minutes:** `(18:00−08:00)=600 − 60 = 540`.
- **overtime_minutes:** `18:00 − 17:00 = 60`. **late_minutes:** 0. **missing_minutes:** 0.
- **Payroll impact:** `+60/60 × 9.89 × 1.25 = +12.36 TND` (`HEURES_SUP`).
- Note: real OUT required — an assumed OUT (missing OUT) never produces OT.

---

## 7. Confirmed decisions (2026-08-05)

1. OT multiplier is **single 1.25** for workday/weekend/holiday/night OT. Day type still recorded for later differentiation.
2. Annual leave entitlement = the seeded **18 days** (`leave_types` ANNUAL).
3. Grace period **0 min**; duplicate-scan window **≤ 60 s inclusive**. **Refined (module-1 API, see ATTENDANCE_API_CONTRACT §2.1):** for type-less terminal punches the window is **type-agnostic** (same employee + terminal, regardless of derived type) so a badge double-read cannot create a bogus IN→OUT pair; explicit-type entries keep the **same employee + type** rule (manual IN then OUT at the same minute is legitimate). 08:00:00 → 08:01:00 **is** a duplicate in both modes.
4. **No OT caps.**
5. **Half-day rule:** single event → `HALF_DAY` = **incomplete attendance evidence** (one terminal event), **not** half pay and **not** half worked; missing bound completed from the schedule, `worked_minutes` reflects the full computed day.
6. **Flexible schedules / multiple shifts per day:** out of scope for v1.
7. **Worked weekend/holiday status:** day class wins (`WEEKEND`/`HOLIDAY`) with all worked minutes as OT; UI must render "Holiday (worked)" / "Weekend (worked)" when minutes exist.
8. **Audit invariants confirmed:** raw `attendance_events` never edited/deleted; applied adjustments immutable; summaries always recomputable/idempotent; payroll consumes snapshots only.
9. `DEDUCTION_RETARD` seeded in **V7** (`salary_component_types`, DEDUCTION, no CNSS/IRPP/CSS) — lateness deduction ready for the payroll phase.
10. **`missing_minutes` = absence only** (ABSENT/HALF_DAY); early departure tracked separately in `early_exit_minutes`, never inflates `missing_minutes`.
11. **Leave by date range:** approved requests resolved dynamically per covered date; no per-day leave rows; each covered date yields its own summary row.
12. **Schedule overlap forbidden:** employee cannot have overlapping `employee_schedules` ranges (service validation).
13. **Future timestamps:** `event_time > now + 5 min` → stored with `time_warning = 1`, never rejected (drift guard extended to the future).
14. **Adjustment sanity cap:** final `worked_minutes` ≤ 1440/day default (configurable) — enforced at apply time.
15. **Payroll freeze:** paid periods are immutable snapshots; later corrections go to the next period or an explicit audited reopening.
16. **`computed_by_user_id`** added to `attendance_summary` (V8) to record who triggered each recompute.
