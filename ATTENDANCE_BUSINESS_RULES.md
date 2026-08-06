# PointagePro — Attendance Engine Business Rules

**Version:** 1.0 (draft for validation)
**Scope:** Behavior of the attendance working-hours engine, absence detection, overtime, corrections, holidays, and how attendance feeds payroll.
**Basis:** `POINTAGEPRO_MASTER_SPECIFICATION.md` §3.4 (raw vs calculated), §3.2 (history), §3.3 (payroll immutability), and `POINTAGEPRO_DATABASE_DESIGN_PLAN.md` §5 conventions.

---

## 0. Engine model and definitions

### 0.1 Two-level data (spec §3.4)

| Level | Table | What it holds | Mutable? |
|---|---|---|---|
| RAW | `attendance_events` | Every physical scan / manual IN-OUT as it happened | **Never** modified |
| CALCULATED | `attendance_summary` | One row per employee per work date: worked/late/early/missing/overtime | Recomputed from raw + adjustments |

Raw scans and calculations are never mixed. Any correction is stored as an adjustment (`attendance_adjustments`) that *feeds* the summary — raw events are never edited or deleted.

### 0.2 Precision and units
- All durations stored as **whole minutes** (`INT`). No rounding to 15-min increments in v1.
- A "work date" = the **shift start date**. For night shifts starting at 22:00, events from 22:00 (day D) through the early hours (day D+1) belong to day D's summary.
- Summary key: `(employee_id, work_date)` — unique.
- **Summary audit trail:** `created_at`, `updated_at`, `computed_at`, `recompute_reason`, and `computed_by_user_id` (who triggered the recalculation — added in V8). Every recompute overwrites the summary row wholesale; the previous computed values are never needed because summaries are always recomputable from raw + adjustments (§9).

### 0.3 Configurable parameters

| Parameter | Source | Default | Notes |
|---|---|---|---|
| Weekly hours | `company_settings.weekly_working_hours` | 40 | informational / legal |
| Monthly hours | `company_settings.monthly_working_hours` | 151.67 | base for hourly rate = monthly salary ÷ this |
| Overtime enabled | `company_settings.overtime_enabled` | ON | master switch for OT counting |
| Overtime multiplier | `company_settings.overtime_rate_multiplier` | 1.25 | applied to all OT types in v1 (see §3.4) |
| Hours netting | `company_settings.hours_netting_enabled` | **OFF** | OFF: each day computed independently, no cross-day netting |
| Duplicate-scan window | code constant | 60 s | **inclusive** (`≤ 60 s`); **type-agnostic** for type-less terminal punches (same employee + terminal), typed for explicit-type entries (see §2.2, §2.8) |
| Late/early grace | code constant | 0 min | no automatic grace period in v1 |

---

## 1. Work schedule rules

### 1.1 Structure
- A **schedule** (`work_schedules`) = header (company, code, name, `is_default`) + exactly **7 lines** (`work_schedule_lines`, one per weekday 1=Monday…7=Sunday). Each line: `is_workday`, `start_time`, `end_time`, `break_minutes`.
- An employee's schedule is **dated** (`employee_schedules`: `valid_from`/`valid_to`), so assignments change over time without overwriting history (§3.2).
- **Overlap rule (service-level validation):** an employee can never have two schedule assignments whose validity ranges overlap. Creating/updating a row whose `valid_from`..`valid_to` overlaps an existing row for the same employee is rejected. `valid_to IS NULL` means "open-ended" and overlaps any later assignment.
- **Resolution for a given date** (in order):
  1. employee schedule assignment valid on that date → use it;
  2. else company default schedule (`is_default=1`) → use it;
  3. else → day is `NOT_SCHEDULED` (no expected work, no absence possible).

### 1.2 Standard default template (offered by Settings, not hard-seeded)
- Monday–Friday 08:00–17:00, `break_minutes=60` → **8 h effective** per day (40 h/week).
- Saturday, Sunday: `is_workday=0`.

### 1.3 Break handling
- Lunch/break = a fixed daily allowance `break_minutes` subtracted **once** from `(last_out − first_in)`.
- Breaks are **not** tracked by RFID scans in v1 (no scan required to start/end break). Rationale: fixed-allowance model keeps pairing simple and matches the first-IN/last-OUT algorithm.

### 1.4 Effective worked minutes
```
effective_work_minutes = (last_out − first_in) − break_minutes
```
Clamped to ≥ 0. Used only when the day is a scheduled workday (§2, §4).

### 1.5 Night shifts
- A line where `end_time < start_time` (e.g. 22:00 → 06:00) is a night shift.
- Worked time computed on the logical window: `end_time` is treated as +24 h.
- Late/early computed against the same logical window.
- Summary day = shift start date (see §0.2).
- **Supported in v1.** The only engine change vs. a normal day is the +24 h arithmetic.

### 1.6 Flexible schedules
- **Not supported in v1.** All schedules have fixed start/end per weekday.
- Future: `flex_start`/`flex_end` windows would be added to `work_schedule_lines`; lateness then measured against the flex window. (Open decision, see §10.)

### 1.7 Multiple shifts per day
- **Not supported in v1.** An employee has at most one schedule line per weekday.
- Multi-shift days (split shifts) would need a second line table or `work_schedule_lines` weekday+slot keys. (Open decision, see §10.)

---

## 2. Check-in / Check-out rules

### 2.1 Event gathering
- Events for a summary = all `attendance_events` for `(employee, work_date)` sorted by `event_time`, regardless of `source` (TERMINAL or MANUAL).
- Orphan events are kept (raw), but may be ignored by pairing:
  - an `OUT` with no preceding `IN` in the day → ignored (orphan checkout);
  - the pair is always defined by first `IN` and last `OUT`.

### 2.2 First IN / Last OUT
- **First IN** = earliest `IN` event of the day.
- **Last OUT** = latest `OUT` event of the day.
- **Multiple IN/OUT in a day:** only first IN and last OUT are used for worked time; intermediate scans are ignored for calculation (fixed-break model). All raw events remain stored.
- **Duplicate scans:** consecutive rapid punches are treated as a single event. Boundary is
  **inclusive**: `≤ 60 s` (e.g. 08:00:00 and 08:01:00 **are** duplicates, 08:01:01 is not).
  For **type-less terminal punches** the window is **type-agnostic** (same employee + terminal)
  so a badge double-read can never create a bogus IN→OUT pair. For **explicit-type entries**
  (manual `attendance.write`, online) the window applies to the **same employee + type** so a
  manual IN then OUT at the same minute is legitimate. This falls out naturally: first IN / last
  OUT never take the second duplicate. (Refines matrix decision #3, see ATTENDANCE_API_CONTRACT §2.1.)
- **Future timestamps (offline skew guard):** a received event with `event_time > server_now + 5 min` is **never rejected** — it is stored with `time_warning = 1` (mirrors the drift guard in §6). Covers tampered RTCs or misconfigured devices sending 2030-01-01.

### 2.3 Missing checkout
- Day has a first IN but **no last OUT** → checkout assumed at scheduled `end_time`.
- `worked_minutes = (end_time − first_in) − break_minutes`; `early_exit_minutes = 0`.
- A single event (IN only or OUT only) → status `HALF_DAY` (§4.2).

### 2.4 Missing check-in (symmetric)
- Day has a last OUT but **no first IN** → check-in assumed at scheduled `start_time`.
- `worked_minutes = (last_out − start_time) − break_minutes`; `late_minutes = 0`.
- Rationale: a valid OUT proves the employee was present; missing IN is treated like missing OUT.

### 2.5 Late arrival
```
late_minutes = max(0, first_in − scheduled_start)
```
- No grace period (v1). Every minute past `start_time` counts.
- Late is **not** absence: the day remains worked unless §4 applies.

### 2.6 Early departure
```
early_exit_minutes = max(0, scheduled_end − last_out)
```
- Only computed when the employee worked (has a first IN). If checkout was assumed (§2.3) it is 0.
- **`early_exit_minutes` is tracked separately and is NOT counted in `missing_minutes`.** `missing_minutes` means **absence only**: it is nonzero exclusively for `ABSENT` (full day) and `HALF_DAY` (unworked portion) days. An early exit on an otherwise-present day keeps `missing_minutes = 0` (surfaced via `early_exit_minutes` for HR review).

### 2.7 Manual entries
- `source='MANUAL'` events are created by users holding `attendance.write` and go through the **same** algorithm. They are audit-traced via `audit_logs`.

### 2.8 Single-punch terminal classification (server-side)
- The ESP32 badge reader sends **one scan per badge with no IN/OUT field** (`{rfidUid, externalRef?, timestamp?}`). The event type is derived **server-side by alternation** (ATTENDANCE_API_CONTRACT §2.1):
  - A type-less punch is `OUT` when the employee's most recent stored event (any date, strictly before this punch's time) is `IN`; otherwise it is `IN`.
  - This models "badge to enter, badge to leave" and handles night shifts (IN 22:00 → OUT 06:00).
- Duplicate protection runs **before** classification: two punches from the same employee on the same terminal within `≤ 60 s` are a duplicate regardless of the derived type.
- An explicit `eventType` (`"IN"`/`"OUT"`) in the scan body overrides alternation (future firmware / admin tools). The 60 s window then applies to the explicit type.
- **Known v1 limitation:** a stale `IN` from a missed clock-out makes the next day's first punch classify as `OUT`. Planned refinement: time-window heuristic (> 14 h since last `IN` ⇒ new `IN`); an explicit `eventType` always wins.

---

## 3. Overtime rules

### 3.1 When overtime starts
Overtime exists only when `overtime_enabled=ON`. Three cases:
1. **Workday**: minutes between scheduled `end_time` and `last_out` (requires a first IN and a real last OUT, not an assumed one).
2. **Weekend work** (`is_workday=0`): all worked minutes = overtime.
3. **Holiday work** (`is_holiday=1`): all worked minutes = overtime.

`workday_overtime_minutes = max(0, last_out − scheduled_end)`. No requirement to have completed the full day first (v1).
- **Documented future setting `overtime_requires_completed_schedule` (default `false`):** when `false` (v1 default), OT is paid even if the employee arrived late and did not complete normal hours (e.g. IN 10:00 / OUT 18:00 → late 120, worked 420, OT still 60). When `true` (future), OT only counts after the full effective day is worked. No schema change for v1 — documented for a later `company_settings` column.

### 3.2 Rate calculation
- v1 uses **one multiplier** `company_settings.overtime_rate_multiplier` (default **1.25**) for all overtime minutes.
- Payroll: `overtime_amount = overtime_minutes/60 × hourly_rate × multiplier`.
- `hourly_rate = monthly_base / monthly_working_hours` (151.67), from the active salary components.

### 3.3 Weekend and holiday overtime
- Same single multiplier in v1; the **day type is still recorded** (`is_weekend`, `is_holiday`, `day_type_id`) so differentiated multipliers can be applied later without recomputing history.
- (Differentiated multipliers are a recommended Settings extension — see §10.)

### 3.4 Night overtime
- Night window defined as **22:00–05:00** (code constant).
- v1: night hours use the same single multiplier; the night hours are tracked in the summary only via the worked window, not a dedicated column. A `night_overtime_minutes` column is a future migration if differentiated rates are wanted.

### 3.5 Caps
- No monthly/weekly overtime cap in v1 (flagged for legal confirmation, §10).

---

## 4. Absence rules

### 4.1 Full-day absence
A scheduled workday with **no events** and no approved leave → status `ABSENT`:
- `worked_minutes = 0`
- `missing_minutes = (end_time − start_time) − break_minutes` (full effective day)

### 4.2 Half-day = incomplete attendance evidence
- **Semantics (important):** `HALF_DAY` means **incomplete attendance evidence** — exactly one terminal event was recorded (IN only or OUT only). It does **NOT** mean half salary and does **NOT** mean half worked.
- The missing side is completed by the schedule bound (§2.3 / §2.4) and `worked_minutes` reflects the full computed day (e.g. OUT-only at 17:00 → assumed IN 08:00 → `worked_minutes = 480`).
- `missing_minutes = the unworked portion of the effective day` (e.g. IN-only at 10:00 → worked 360, missing 120). A HALF_DAY whose missing side completes fully gets `missing_minutes = 0`.
- UI/reports must display `HALF_DAY` as "partial evidence" (e.g. "IN only"), never as "half salary".
- **Payroll:** the day is paid per `worked_minutes`; `HALF_DAY` itself never halves anything.

### 4.3 Unjustified absence
- A scheduled workday with no events, no approved leave covering the date, and not a holiday → **unjustified** absence.
- Justification is decided purely by the leave interaction below; there is no separate "justified/unjustified" flag — an absent day with an approved leave is `LEAVE`, without one it is `ABSENT`.

### 4.4 Leave interaction
- An **approved** leave request covering the work date → summary status `LEAVE`, `worked_minutes=0`, `missing_minutes=0` (not counted as absence).
- **Leave is evaluated by date range.** A request `start_date..end_date` (e.g. 01/08→05/08) is resolved **dynamically** by the summary engine for each work date in the range — the engine checks whether each date falls inside any approved request for the employee. **No per-day leave rows are created**; `leave_requests` rows stay as requests. Each covered date produces its own `attendance_summary` row on recompute (status `LEAVE`).
- The leave is paid or not per `leave_types.is_paid` (payroll concern, §7).
- A **pending/rejected/cancelled** leave has no effect on the summary.
- Holiday dates are never treated as absence even if no leave exists (§6).

### 4.5 Non-scheduled days
- Weekend or day off with no events → status `NOT_SCHEDULED`, no absence, all zeros.
- Weekend/holiday **with** events → worked/OT per §3.

---

## 5. Correction workflow

### 5.1 Who can modify
| Action | Permission | Scope |
|---|---|---|
| Manual IN/OUT entry | `attendance.write` | any employee (HR) / own department (Manager) |
| Create adjustment request | `attendance.adjust` | HR any, Manager own department |
| Approve/reject adjustment step | `attendance.adjust` | Manager step 1 (own department), HR step 2; nobody approves their own request |
| Recompute a date range | `attendance.recalculate` | HR / Accountant |

Employees (role USER) cannot modify attendance; they can only view (`attendance.read`) and request corrections **through their manager** in v1.

### 5.2 Approval process (Manager → HR, multi-step) — finalized for module 3
1. Creator creates `attendance_adjustments` (status `PENDING`) with **mandatory** `reason`, type, and minutes.
2. The approval chain is **materialized at creation** in `approvals` (`request_type='ATTENDANCE_ADJUST'`):
   - **Step 1 — MANAGER**: the target employee's current department manager (`departments.manager_employee_id`, current dept via `employees.department_id`). Skipped when the employee has no manager, the employee IS the department manager, or the creator IS the department manager.
   - **Step 2 — HR**: any HR member. When the creator is HR, this step is **auto-decided by the creator at creation** (HR creation = the HR sign-off; avoids single-HR deadlock).
   - Invariant: the **creator and the target employee can never decide a step** ("nobody approves their own request").
3. **Empty chain** (HR creator, no manager step applicable): the request is created **directly `APPLIED`** in the same transaction, with the HR step recorded and the creator as approver (audit trail preserved). Applies immediately.
4. Each step: `APPROVED` → next step; `REJECTED` → the request becomes `REJECTED` and the remaining steps are set `REJECTED` (chain stays consistent).
5. Last approval → `APPLIED` → the engine recomputes **exactly that employee/date in the same transaction**, reason `adjustment:<id>`.
6. `approved_by`/`approved_at` on the request record the **last** approver; the `approvals` table keeps every step for audit.
7. All decisions are race-safe: the step transition uses a guarded update (`status_id` from PENDING → APPROVED/REJECTED), so a request can only be decided once.

### 5.3 Adjustment types → effect on summary
| Type (`adjustment_types`) | Effect |
|---|---|
| `ADD_MINUTES` / `REMOVE_MINUTES` | add/subtract from `worked_minutes` |
| `ADD_OVERTIME` / `REMOVE_OVERTIME` | add/subtract from `overtime_minutes` |
| `SET_ABSENT` | status → `ABSENT` for that date |

`adjustment_minutes` stores the net applied value on the summary; the change is visible in `recompute_reason` + `computed_at`.
- **Daily cap — dry-run reject at approval (finalized for module 3):** the final `worked_minutes` for a day can never exceed a configurable daily maximum — default **1440** (one day in minutes). At **approval time** the service performs a dry-run of the day's calculation **including this adjustment**; if the resulting `worked_minutes` would exceed 1440 the approval is rejected with 400 and the request stays `PENDING` (approver can reject, or a smaller amount is requested). The engine's 1440 clamp (`DayCalculator.MAX_DAILY_WORKED_MINUTES`) remains **only as a defensive backstop** — normal business flow fails explicitly rather than silently dropping payable minutes. `SET_ABSENT` zeroes worked.

### 5.6 Cancellation (finalized for module 3)
- `CANCELLED` is a terminal state added to `adjustment_statuses` (and `approval_statuses`) via migration.
- Who: the **creator** while PENDING, or **any HR** while PENDING. A **cancellation reason is mandatory**.
- Pending approval steps transition to `CANCELLED` automatically (dead steps never appear in approval queues).
- Once `APPLIED`, `REJECTED`, or `CANCELLED`, the request can no longer be cancelled and is **immutable**; any further correction is a **new** adjustment request (full audit trail preserved).

### 5.7 Frozen payroll periods — block (finalized for module 3)
- An adjustment whose target date falls in a **frozen payroll month** is rejected. A month is frozen when a `payrolls` row exists for `(company, period)` with status **`VALIDATED`, `APPROVED`, or `PAID`** (`DRAFT`/`COMPUTED` are still regenerable → corrections allowed).
- The API returns **409 Conflict** with a clear "Payroll period is frozen" message on create and on approve.
- Corrections to a frozen month are resolved via either a next-period adjustment (company policy) or the explicit, audit-logged payroll reopening workflow (§7.4). Attendance stays independent from payroll; payroll immutability is preserved.

### 5.4 Audit history
- Raw events: never changed (spec §3.4).
- Adjustments: never changed/deleted once `APPLIED` (spec §3.2).
- Summary recomputes: every run sets `computed_at` + `recompute_reason` + `computed_by_user_id` (who triggered it).
- Status changes (create/approve/reject) logged to `audit_logs` (`STATUS_CHANGE`).

### 5.5 Reason requirement
`reason` is **NOT NULL** in the schema and enforced by the service on create. A change without a reason is rejected.

---

## 6. Holiday rules

### 6.1 Holiday definition
- `holidays` per company: `type_id` = `LEGAL` (recurring, `is_recurring=1`) or `COMPANY` (one-off, with `year`).
- Unique `(company_id, holiday_date)`.
- Legal holiday list is **seeded per year** in the DB (V-migration) or maintained in Settings; the 2026 list is fixed and published before payroll.

### 6.2 Effect on attendance
- A holiday work date → `day_type=HOLIDAY`, `is_holiday=1`, status `HOLIDAY`, zeros.
- Work on a holiday → all minutes are overtime (§3.1 case 3); status stays `HOLIDAY` (day class wins over PRESENT).
- A holiday falling on a weekend is still `HOLIDAY` (not `WEEKEND`); priority order is **HOLIDAY > WEEKEND**.
- **UI/display requirement:** when a `HOLIDAY` day has `worked_minutes > 0`, the UI must show **"Holiday (worked)"** — not bare "Holiday" — so HR never mistakes a worked holiday for an absent one. Same convention for `WEEKEND` with worked minutes.

### 6.3 Working on holidays
- Allowed: employees may work on legal/company holidays; all such minutes are overtime.
- Compensatory rest is granted via a `COMPENSATORY` leave request (leave type exists) — the engine treats that day as `LEAVE`.

---

## 7. Payroll impact

Attendance produces three inputs to payroll per employee per month. Nothing else from attendance touches salary directly.

### 7.1 From summary to monthly values
- **Unjustified absence minutes** = sum of `missing_minutes` for `ABSENT` days (half-days included).
- **Lateness minutes** = sum of `late_minutes` (no grace).
- **Overtime minutes** = sum of `overtime_minutes` (workday + weekend + holiday).

### 7.2 Salary effects
| Input | Payroll effect | Component type |
|---|---|---|
| Base | monthly base from active `salary_components` (BASE_SALARY + allowances) | per component |
| Absence | `− (absence_minutes/60 × hourly_rate)` | `DEDUCTION_ABSENCE` |
| Lateness | `− (late_minutes/60 × hourly_rate)` | `DEDUCTION_RETARD` (new type to seed — see §8.1) |
| Overtime | `+ (overtime_minutes/60 × hourly_rate × multiplier)` | `HEURES_SUP` |
| Bonuses | independent of attendance (set per contract/salary component) | e.g. `PRIME_RENDEMENT` |

- Hourly rate = monthly base ÷ `company_settings.monthly_working_hours` (151.67).
- Payroll computes **only** from `payroll_attendance_snapshots` (frozen per-day facts copied from `attendance_summary` at generation time — freeze-before-compute). All payroll output is then **snapshotted** into `payroll_items` / `payroll_item_components`; a paid payroll never changes (spec §3.3). Payroll never reads the mutable `attendance_summary`.

### 7.3 Netting
`hours_netting_enabled = OFF`: absence/lateness and overtime are computed independently and never offset each other. Late and absent minutes are deducted; overtime is paid; no cross-day compensation. (This was a confirmed decision — the spec's netting discussion is resolved to OFF.)

### 7.4 Payroll freeze rule (critical)
- Payroll generation first **freezes** the period's attendance facts into `payroll_attendance_snapshots` (per employee/day, same transaction), then computes `payroll_items`/`payroll_item_components` **only from that snapshot**. Once finalized/paid, `payroll_attendance_snapshots` + `payroll_items`/`payroll_item_components` are **immutable** (spec §3.3) and can never be changed by later attendance edits.
- If attendance is corrected **after** a paid period (e.g. a July adjustment made in August), the change does **not** touch the paid `payroll_items`. It is resolved as either:
  1. an adjustment applied to the **next** payroll period, or
  2. a **reopening** of the period, which is an explicit privileged action (audit-logged, requires `payroll.approve`/admin) that regenerates the payroll before re-freezing it.
- The attendance engine is never allowed to write payroll; it only marks `computed_at` so payroll knows which periods are up to date.
- **Enforcement at the adjustment API (module 3):** create/approve of an adjustment targeting a frozen month (payroll `VALIDATED`/`APPROVED`/`PAID`) is blocked with **409 Conflict** (§5.7).

---

## 8. Tunisia-specific rules

### 8.1 CNSS / IRPP / CSS implications for attendance components
- Every earnings component carries taxation flags (`salary_component_types.is_subject_to_cnss/irpp/css`) added in V3.
- Defaults: overtime `HEURES_SUP` → CNSS yes, IRPP yes, CSS no. Deduction types (`DEDUCTION_ABSENCE`, new `DEDUCTION_RETARD`) reduce the taxable base and are not earnings.
- 2026 rates (seeded in V2): CNSS employee **9.68%**, employer **16.57%**, family allocations **0.55%**; CSS employee **0.5%**; IRPP via annual brackets; SMIG 3.000 / h.

### 8.2 Legal leave types (seeded)
`ANNUAL` (18 days/yr — see §10 legal note), `SICK`, `MATERNITY` (90 days), `PATERNITY`, `EXCEPTIONAL`, `UNPAID`, `COMPENSATORY`. `is_paid` governs pay; attendance engine only checks "approved leave covers this date".
- **Legal verification required before production:** Tunisian leave entitlement can depend on worker category, seniority, and the applicable collective agreement. The seeded 18 days must be **verified against the actual legal/agreement value before deployment** and updated as a one-row `leave_types` change — never hardcoded in code.

### 8.3 Family situations for payroll
- IRPP monthly withholding uses `employee_tax_profiles` (V3): `tax_situation_id` (`CELIBATAIRE / MARIE / CHEF_DE_FAMILLE`), `spouse_is_working`, `number_of_children`, `number_of_disabled_children`, dated (`valid_from`/`valid_to`).
- The profile current on the pay date is used. Attendance does not feed IRPP directly.

---

## 9. Engine execution rules

- **Idempotency:** recomputing the same date range always produces the same summaries (raw + adjustments + leaves + holidays are the only inputs). No cumulative state.
- **Recompute trigger (module 2, implemented):**
  - `POST /attendance/recompute` — per-employee date range, authority `attendance.recalculate` (seeded on `HR` role). `reason` ≤ 255 chars, defaults to `api:recompute`; stored in `recompute_reason`. Returns the recomputed summaries (tenant-scoped).
  - `POST /attendance/recompute/all` — same authority, recomputes every employee of the caller's company over the range; returns `RecomputeStats {companyId, from, to, employeeCount, dayCount}` with `dayCount = employeeCount × days(from..to)`.
  - Range rule shared by both: `from > to` → 400; span strictly greater than 366 calendar days → 400.
  - **Compute-on-miss reads:** `GET /attendance/summaries/day` and `GET /attendance/summaries/today` do NOT 404 when no summary exists — they materialize one on the fly (reasons `api:day` / `api:today`), so a fresh employee always has an answer. Reads and recomputes set `computed_by` to the acting user (resolved to a managed reference inside the engine transaction).
  - The engine sets `computed_at` and `recompute_reason` on every write; recomputation is idempotent and always overwrites the same summary rows.
- **Order of status resolution** (first match wins):
  1. approved leave covers date → `LEAVE`
  2. holiday → `HOLIDAY`
  3. non-workday of schedule → `WEEKEND` / `NOT_SCHEDULED`
  4. no events → `ABSENT`
  5. exactly one event → `HALF_DAY`
  6. otherwise → `PRESENT` / `LATE` / `ADJUSTED`
- **Company scoping:** all queries/rows carry `company_id`; every created row derives it from the employee's company (design plan §5 rule).

---

## 10. Confirmed decisions (2026-08-05)

1. **Overtime multiplier:** single **1.25** for all OT types. Differentiated rates can be added later (history-safe).
2. **Annual leave days:** **18** (seeded). Legal value re-check before the payroll phase; one-row update in `leave_types` if needed.
3. **Duplicate-scan window:** **60 s** (code constant).
4. **Grace period:** **0 min**.
5. **Flexible schedules / multiple shifts per day:** out of scope for v1 (§1.6, §1.7).
6. **Half-day rule:** single event → `HALF_DAY` with schedule-bound completion of the missing side (§2.3/§2.4).
7. **Overtime caps:** none in v1.
8. **Lateness deduction** `DEDUCTION_RETARD`: **seeded in V7** (`salary_component_types`), ready for the payroll phase.
9. **Audit/history invariants confirmed:** raw `attendance_events` never edited/deleted; applied adjustments immutable; summaries always recomputable (idempotent); payroll consumes snapshots only.
10. **HALF_DAY meaning:** incomplete attendance evidence (one terminal event), **not** half pay and **not** half worked — missing side completed from the schedule, `worked_minutes` reflects the full computed day (§4.2).
11. **`missing_minutes` = absence only:** nonzero exclusively for `ABSENT`/`HALF_DAY`; early departure is tracked separately in `early_exit_minutes` and never inflates `missing_minutes` (§2.6).
12. **Leave by date range:** approved requests are resolved dynamically per covered date; no per-day leave rows; each covered date yields its own summary row (§4.4).
13. **Schedule overlap forbidden:** an employee cannot have overlapping `employee_schedules` ranges; service-level validation (§1.1).
14. **Duplicate window inclusive** `≤ 60 s` (same employee + terminal + type); future timestamps beyond `now + 5 min` stored with `time_warning = 1`, never rejected (§2.1/§2.2).
15. **Overtime settings:** `overtime_requires_completed_schedule = false` documented for v1 (OT paid regardless of completed hours); daily `worked_minutes` cap default **1440** (adjustment validation) (§3.1/§5.3).
16. **Payroll freeze:** paid periods are immutable snapshots; later corrections go to the next period or an explicit, audited reopening (§7.4).
17. **`computed_by` detached-entity fix:** the engine never attaches a controller/security-context `User` to a summary — it resolves `computed_by` to a managed reference (`UserRepository.getReferenceById`) inside the transaction, avoiding Hibernate `entityEntry is null` failures during entity-graph reads after a write (§9).
18. **Recompute API scope caps:** single employee range recompute and company-wide `recompute/all` share the same 366-calendar-day span cap; `from > to` rejected with 400 (§9).
19. **Adjustment workflow (module 3, finalized 2026-08-05):** lifecycle `PENDING → APPLIED | REJECTED | CANCELLED`; chain materialized at creation (MANAGER then HR, HR auto-sign-off when HR is the creator, empty chain → immediate `APPLIED`); creator/target-employee can never decide a step; cancel = creator-while-PENDING or HR-while-PENDING with mandatory reason; frozen-month target (payroll `VALIDATED`/`APPROVED`/`PAID`) blocked with 409; dry-run cap rejection at approve time (1440), engine clamp kept only as backstop; `APPLIED` triggers single-day recompute `adjustment:<id>` (§5.2/§5.3/§5.6/§5.7).

---

## 11. Schema mapping (reference)

- `attendance_events` (raw) → `attendance_summary` (one row per employee/day, mutable working table) → `attendance_adjustments` (corrections) → `payroll_attendance_snapshots` (frozen per-run facts) → `payroll_items`/`payroll_item_components` (frozen monetary snapshots).
- Approval steps for adjustments and leaves: `approvals` (per-step) + `approval_statuses`; the request table keeps the aggregate status and last approver.
- Schedules: `work_schedules` → `work_schedule_lines` (7 per schedule) → `employee_schedules` (dated assignments) → `attendance_summary.schedule_id`.
- Day classification: `day_types`, `attendance_statuses`, `holidays` + `holiday_types`.
- Settings: `company_settings` (hours, OT switch/multiplier, netting).
- Leave: `leave_types`, `leave_balances`, `leave_requests`, `leave_balance_logs`.
- Legal: `tax_brackets`, `cnss_rates`, `css_rates`, `smig_values`, `family_allowances`; tax profile: `tax_situations`, `employee_tax_profiles`.
