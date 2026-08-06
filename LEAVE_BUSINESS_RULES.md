# Leave Management — Business Rules (Module 4, DRAFT v0.1)

Status: **frozen (approved)** — 2026-08-05. Decisions finalized via Q&A: debit-at-approval
with refund, no HR self-approval, MANAGER gains `leave.write` (V14), server-computed
working days, per-year splitting, PENDING+APPROVED overlap guard, auto-provision of tracked
balances, optional reason, tracked/exempt split by `default_days_per_year`.
Frozen decisions are normative for implementation. This document follows the same
discipline as module 3 (adjustments): rules are frozen first, the API contract and
implementation then match them exactly.

---

## 1. Scope

Backend leave-management module (`com.pointagepro.leave`): request lifecycle, approval
workflow via the shared `approvals` table (`request_type = 'LEAVE'`), per-year balances,
balance logging, and attendance recompute on approval/cancellation. Reuses the existing
entities/repositories (`LeaveRequest`, `LeaveBalance`, `LeaveType`, `LeaveRequestStatus`,
`LeaveBalanceLog`) and the module-3 approval plumbing. No engine change.

## 2. Lifecycle and terminality

- Lifecycle: `PENDING → APPROVED | REJECTED | CANCELLED`.
- Aggregate status lives on `leave_requests.status_id`; each chain step lives in `approvals`.
- **No edit endpoint** (cancel + re-create instead).
- Terminal states `APPROVED`/`REJECTED`/`CANCELLED` are immutable: any transition on a
  terminal request → **409** (exception: `CANCELLED` is entered *from* `APPROVED` by HR).
- Transitions are race-safe via `@Version` optimistic locking on `LeaveRequest` and
  `LeaveBalance` (V14) → `ObjectOptimisticLockingFailureException` → 409
  ("modified concurrently; reload and retry").

## 3. Approval chain (materialized at creation)

`request_type = 'LEAVE'` in `approvals`.

1. **Step 1 `MANAGER`** — the requester's department manager (`departments.manager_employee_id`).
   Skipped when: no department/manager, the requester *is* the manager, or the manager has no
   active user account.
2. **Step 2 `HR`** — always present.

Rules that differ deliberately from adjustments:

- **No auto-approval.** The requester can never decide any of their own steps, whatever their
  role (HR requester must be approved by *another* HR member; the HR step stays `PENDING`).
- **No empty-chain immediate apply.** A leave always requires at least one real approver
  (there is always an `HR` step). If no eligible approver exists the request simply stays
  `PENDING` — never auto-approved.

Decision rights (`leave.approve` gate on the endpoint, role checks in the service):

- `MANAGER` role → only the `MANAGER` step, and only for an employee of their own department.
- `HR` role → only `HR` steps.
- `ADMIN` role → universal: may decide any step, but never on a request they created
  ("never their own request").
- The creator and the target employee can never decide.

## 4. Creation rules

- Permission `leave.write` on the endpoint.
- Service scope: `USER`/`MANAGER` may create for **themselves only**; `HR`/`ADMIN` may create
  for any employee of the caller's company. No hidden bypass — permission answers "can this
  role create?", the service answers "for whom?".
- `leave_type` must be active (`leave_types.is_active = 1`).
- `startDate ≤ endDate`; range span capped at **366 calendar days** (consistent with the
  attendance recompute cap) → 400.
- **Overlap guard:** a `PENDING` or `APPROVED` request already overlapping `[start,end]` for
  the employee → **409** with a clear conflict message. `REJECTED`/`CANCELLED` are ignored.
- **`days_requested` is server-computed, never client-provided:** working days in
  `[start,end]` = weekdays per the company calendar, excluding legal/company holidays from
  the `holidays` table. Value is stored and audited.
- **No balance check at creation.** A `PENDING` request touches nothing.
- `reason` optional (≤ 500 chars); `attachment_path` optional (≤ 255; no upload endpoint in
  v1 — a path/URL string is stored if supplied).

## 5. Approval (`approve`)

- Only from `PENDING`; the current pending step (lowest `step_order`) must be decidable by
  the actor; optional comment (≤ 500).
- **Frozen-month guard:** if any calendar month in `[start,end]` has a frozen payroll
  (`payrolls` status `VALIDATED`/`APPROVED`/`PAID`) → **409**.
- When the last pending step is approved → request becomes `APPROVED`,
  `approved_by`/`approved_at` set to the last approver, then in the **same transaction**:
  1. **Balance debit, atomic per year.** Split `[start,end]` by calendar year; for each year
     compute its working days. For each year whose type is **tracked** (has
     `default_days_per_year` set):
     - missing `leave_balances` row → **auto-provision** it in the same transaction
       (`entitlement_days = default_days_per_year`, `taken_days = 0`, other counters 0) and
       write an audit entry noting the auto-provision (source: type default).
     - debit `taken_days += yearWorkingDays` under `@Version` optimistic lock.
     - insufficient balance (`available < yearWorkingDays`) → **400**, request stays `PENDING`.
  2. `leave_balance_logs` row per debit (`ref_type = 'LEAVE'`, `ref_id = request id`,
     `delta = +days`, reason `Approval of leave request`).
  3. **Recompute** `[start,end]` for the employee with `recompute_reason = "leave:<id>"`
     (covered days become summary status `LEAVE`).
- **Tracked vs exempt.** A type is **tracked** iff `default_days_per_year IS NOT NULL`
  (currently ANNUAL, MATERNITY): balance row required/provisioned, debit + cap applied.
  A type with `default_days_per_year IS NULL` (SICK, PATERNITY, EXCEPTIONAL, UNPAID,
  COMPENSATORY) is **exempt**: approved normally, no balance row, no entitlement cap, no
  debit. Auto-provision + fail-if-undeterminable applies only to tracked types.
- `available = entitlement_days + carried_over_days + adjusted_days − taken_days`.

## 6. Rejection (`reject`)

- Only from `PENDING`; actor must be able to decide the current pending step; optional
  comment; all remaining pending steps → `REJECTED`; request → `REJECTED`;
  `rejected_reason` stored. No balance effect (nothing was debited while `PENDING`).

## 7. Cancellation (`cancel`)

- Permission `leave.approve`; **reason required** (≤ 255).
- **Who:** the creator while `PENDING` (withdrawal), or any `HR`/`ADMIN` while
  `PENDING` or `APPROVED`. The creator can **never** cancel their own `APPROVED` request.
- **From `PENDING`:** pending steps → `CANCELLED`; request → `CANCELLED`. No balance effect,
  no recompute.
- **From `APPROVED` (HR/ADMIN only):**
  1. **Frozen-month guard** (same as §5) → 409 (the affected attendance can no longer be
     rewritten).
  2. Request → `CANCELLED`.
  3. **Balance refund, atomic per year** — reverse of §5 debit (`delta = −days`,
     reason `Cancellation of approved leave request`).
  4. **Recompute** `[start,end]` with `recompute_reason = "leave-cancel:<id>"` (days revert).

## 8. Frozen-month rule (shared with module 3)

Same helper and message as adjustments: any frozen payroll month in the affected range →
409 "Payroll period is frozen…". Applies to `approve` and to `cancel` of an `APPROVED`
request; **not** to `create`/`reject`/`cancel` of a `PENDING` request (no attendance
effect).

## 9. Audit

- `AuditService`: `CREATE` (→ PENDING) on the request; `STATUS_CHANGE` per approval step
  (old/new JSON); `STATUS_CHANGE` on the request for `APPROVED`/`REJECTED`/`CANCELLED`.
- Balance movements have their own trail: `leave_balance_logs` (idempotent, ref-linked to
  the request).

## 10. API surface (summary — full contract in `LEAVE_API_CONTRACT.md`)

Base `/api/v1/leaves`:

- `POST /leaves` — create (`leave.write`)
- `GET /leaves` — list (filters `employeeId`, `statusCode`, `from`, `to`) (`leave.read`)
- `GET /leaves/{id}` — single (`leave.read`)
- `GET /leaves/pending` — only requests with a step the caller can decide (`leave.approve`)
- `POST /leaves/{id}/approve` | `reject` | `cancel` (`leave.approve`)
- `GET /leaves/balance/{employeeId}?year=` — balances (`leave.read`; self or HR/ADMIN scope)

## 11. Schema change (V14)

- `ALTER TABLE leave_requests ADD COLUMN version BIGINT NOT NULL DEFAULT 0`.
- `ALTER TABLE leave_balances ADD COLUMN version BIGINT NOT NULL DEFAULT 0`.
- Seed: grant `leave.write` to the `MANAGER` role (`role_permissions`).

## 12. Module-3 consistency alignment (small, safe)

- Extract a shared decision/scope helper so `ADMIN` is a universal approver in **both**
  modules (adjustments currently deny ADMIN in `canDecideStep`/`assertCanCreate` despite
  seeding `attendance.adjust`). Behavior change is additive (ADMIN gains rights it already
  nominally holds); existing module-3 tests updated accordingly.
- Move `ApprovalStepResponse` from `attendance/dto` to `shared/approval/dto` so both modules
  reuse the same DTO (no behavior change).

## 13. Out of scope (v1)

- Half-day leave (needs a dedicated model; `days_requested` is whole working days only).
- File upload for `attachment_path` (path/URL string only).
- Employee self-service approval delegation / notifications.
- Editing or deleting requests (cancel + re-create).
