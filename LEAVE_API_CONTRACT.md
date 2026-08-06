# Leave Management — API Contract (Module 4, v1.0)

Frozen 2026-08-05 against `LEAVE_BUSINESS_RULES.md`. Conventions follow
`ATTENDANCE_API_CONTRACT.md`: French `ApiResponse` messages, thin controllers
(Controller → DTO → Service → Domain), no entity serialization, method-level
`@PreAuthorize` on authority codes.

Base path: `/api/v1/leaves`. All endpoints require a valid JWT.

---

## 1. Shared response envelope

Every endpoint returns `ApiResponse<T>`:

```json
{ "success": true, "message": "…", "data": { … } }
```

Error mapping (shared `GlobalExceptionHandler`): **400** validation/`IllegalArgumentException`,
**403** `AccessDeniedException`, **404** `ResourceNotFoundException` (also used for tenant
mismatch), **409** `ConflictException` (overlap, frozen month, terminal state, optimistic-lock
race). Unauthenticated → **401** JSON entry point (existing).

---

## 2. `POST /leaves` — create a leave request (staff)

- Auth: JWT, `leave.write` (seeded on ADMIN/HR/USER; MANAGER added in V14).
- **Scope (service-enforced, no bypass):** `USER`/`MANAGER` → their own employee record only
  (403 otherwise); `HR`/`ADMIN` → any employee of the caller's company.
- Body `LeaveCreateRequest`:

  ```json
  {
    "employeeId": 12,
    "leaveTypeCode": "ANNUAL",
    "startDate": "2026-08-10",
    "endDate": "2026-08-14",
    "reason": "vacances",
    "attachmentPath": null
  }
  ```

  - `employeeId`, `leaveTypeCode`, `startDate`, `endDate` required. `reason` optional (≤ 500).
    `attachmentPath` optional (≤ 255, path/URL string; no upload in v1).
- Validation (400): `startDate > endDate`; span > 366 calendar days; `leaveTypeCode` unknown
  or inactive (`leave_types.is_active = 0`); reason/attachment too long.
- **`days_requested` is server-computed** (never accepted from the client): working days in
  `[startDate, endDate]` = weekdays per the company calendar excluding legal/company holidays
  (`holidays` table). Stored and audited.
- **Overlap guard (409):** the employee already has a `PENDING` **or** `APPROVED` request
  overlapping `[startDate, endDate]` → `ConflictException` with a clear message. `REJECTED`/
  `CANCELLED` are ignored. `PENDING` requests therefore block parallel planning.
- **No balance check at create** (a `PENDING` request touches nothing).
- Chain materialized at creation (`approvals`, `request_type = 'LEAVE'`):
  - Step 1 — `MANAGER`: requester's department manager (`departments.manager_employee_id`);
    skipped when the requester has no department/manager, the requester *is* the manager, or
    the manager has no active user account.
  - Step 2 — `HR`: always present; **never auto-decided**, even when the requester is an HR
    member (a different HR must approve). If no eligible approver exists the request stays
    `PENDING` — there is no empty-chain immediate-apply for leaves.
  - The requester can never decide any of their own steps.
- 201 + `LeaveResponse`. Other: 400 validation, 403 scope, 404 employee/type unknown or wrong
  company, 409 overlap.

## 3. `GET /leaves` — list leave requests (staff)

- Auth: JWT, `leave.read` (company-scoped).
- Query: `employeeId` (optional), `status` (optional: `PENDING`/`APPROVED`/`REJECTED`/
  `CANCELLED`), `from`/`to` (optional `startDate` range; `from > to` → 400). Order `createdAt`
  desc. Returns each request with its full approvals chain.
- 200 + list of `LeaveResponse`.

## 4. `GET /leaves/{id}` — single leave request with chain (staff)

- Auth: JWT, `leave.read`. Tenant mismatch → **404**.
- 200 + `LeaveResponse`.

## 5. `GET /leaves/pending` — my approval queue (staff)

- Auth: JWT, `leave.approve`.
- Returns `PENDING` requests where the caller can decide the current pending step:
  `MANAGER` steps → the requester is an employee of the caller's department; `HR` steps → any
  HR member; `ADMIN` → any step (except requests the caller created).
- 200 + list of `LeaveResponse` (each annotated with the pending step).

## 6. `POST /leaves/{id}/approve` — approve the current step (staff)

- Auth: JWT, `leave.approve`. Caller must be the approver of the **current** pending step
  (role + scope; requester never) → 403 otherwise.
- Body `LeaveDecisionRequest`: `comment` optional (≤ 500).
- Guards (before the step flips):
  - request not `PENDING` (terminal) → **409**;
  - any month in `[startDate, endDate]` frozen (`payrolls` `VALIDATED`/`APPROVED`/`PAID`) →
    **409** `Payroll period is frozen`;
  - **balance debit dry-run per year**: for tracked types, each year's working days must be
    ≤ that year's `available` (after auto-provisioning the row if missing); otherwise **400**
    with the shortfall and the request stays `PENDING`.
- Step → `APPROVED`; when the last step is approved → request `APPROVED`
  (`approved_by`/`approved_at` = last approver) and in the **same transaction**:
  1. per-year balance debit (auto-provision tracked rows, `taken_days += yearWorkingDays`,
     `@Version` lock; `leave_balance_logs` row per debit, `ref_type = 'LEAVE'`,
     `ref_id = {id}`, reason `Approval of leave request`);
  2. engine recompute `[startDate, endDate]` with `recompute_reason = "leave:{id}"`
     (covered working days become summary status `LEAVE`).
- 200 + `LeaveResponse`.

## 7. `POST /leaves/{id}/reject` — reject (staff)

- Auth: JWT, `leave.approve`. Caller must be the current step's approver (403 otherwise).
- Body `LeaveDecisionRequest`: `comment` optional.
- Request → `REJECTED` (`rejected_reason` stored); the acted step `REJECTED` and all remaining
  pending steps set `REJECTED`. Never debits, never recomputes.
- 200 + `LeaveResponse`.

## 8. `POST /leaves/{id}/cancel` — cancel (staff)

- Auth: JWT, `leave.approve` **or** `leave.write`. The `leave.write` path lets the requester
  withdraw their own `PENDING` request (a `USER` holds only `leave.read`/`leave.write`); the
  service still denies any non-creator/HR/ADMIN attempt. **Who:** the creator while `PENDING`
  (withdrawal); any `HR`/`ADMIN` while `PENDING` **or** `APPROVED`. The creator can never cancel
  their own `APPROVED` request. Terminal `REJECTED` → 409.
- Body `LeaveCancelRequest`: `reason` (**required**, ≤ 255).
- **From `PENDING`:** pending steps → `CANCELLED`; request → `CANCELLED`. No balance effect,
  no recompute.
- **From `APPROVED` (HR/ADMIN only):** frozen-month guard first (any month in range frozen →
  **409**); then request → `CANCELLED`, and in the same transaction:
  1. per-year balance **refund** (`taken_days -= yearWorkingDays`, `leave_balance_logs` row,
     reason `Cancellation of approved leave request`);
  2. engine recompute `[startDate, endDate]` with `recompute_reason = "leave-cancel:{id}"`
     (days revert to their normal classification).
- 200 + `LeaveResponse`.

## 9. `GET /leaves/balance/{employeeId}?year=` — balances (staff)

- Auth: JWT, `leave.read`. Scope: caller's own employee record, or HR/ADMIN for any employee
  of the company (403 otherwise). `year` optional (default: current year).
- 200 + list of `LeaveBalanceResponse` (one per tracked leave type of that year):

  ```json
  {
    "success": true, "message": "Soldes de congés",
    "data": [ {
      "leaveTypeCode": "ANNUAL", "leaveTypeName": "Annual leave",
      "year": 2026,
      "entitlementDays": 18.0, "carriedOverDays": 0.0,
      "adjustedDays": 0.0, "takenDays": 3.0,
      "availableDays": 15.0 } ] }
  ```

  `availableDays = entitlementDays + carriedOverDays + adjustedDays − takenDays`. Types with
  no balance row are omitted.

---

## 10. Security notes

- Writes gate on `leave.write` (create, and requester-withdrawal via cancel) and `leave.approve`
  (approve/reject; cancel for HR/ADMIN decisions); reads on `leave.read`. `V14` grants
  `leave.write` to `MANAGER` (manager self-service).
- Approver resolution is step-role based and shares one helper with module 3 (extracted in
  V14/refactor): `MANAGER` → requester's department manager; `HR` → any HR member; `ADMIN` →
  universal. The requester can never decide their own steps; the manager of the requester's
  own department decides the MANAGER step.
- No entity is ever serialized: responses use DTOs (`LeaveResponse`, `LeaveBalanceResponse`,
  shared `ApprovalStepResponse`).

## 11. Data types (DTOs)

| DTO | Purpose |
|---|---|
| `LeaveCreateRequest` | create body (employeeId, leaveTypeCode, startDate, endDate, reason?, attachmentPath?) |
| `LeaveDecisionRequest` | approve/reject body (comment?) |
| `LeaveCancelRequest` | cancel body (reason — required) |
| `LeaveResponse` | request item (id, employee, type, start/end dates, daysRequested, reason, attachmentPath, status, creator/approver, rejectedReason, timestamps, approvals chain) |
| `LeaveBalanceResponse` | per-year balance (leaveType, year, entitlement, carriedOver, adjusted, taken, available) |
| `ApprovalStepResponse` | shared (moved from `attendance/dto` to `shared/approval/dto` in module 4) |

## 12. Non-goals (v1)

- Half-day leave (whole working days only; `days_requested` DECIMAL supports 0.5 later).
- File upload for `attachmentPath`; approval delegation/notifications; edit/delete of requests.
- Balance provisioning UI (auto-provisioned on first approval for tracked types).
- Payroll consumption of leave (payroll module consumes summaries + leave types later).

## 13. Doc updates caused by this module

- `LEAVE_BUSINESS_RULES.md` (this module's rules, frozen) + `PROJECT_MAP.md`
  (pending + VERSION_NOTES at close).
- Schema V14: `version` on `leave_requests` + `leave_balances`; `leave.write` for `MANAGER`.
- Shared-approval refactor (§10): `ApprovalStepResponse` → `shared/approval/dto`;
  ADMIN universal approver/creator helper applied to **both** leaves and adjustments;
  module-3 tests updated and full suite + E2E re-run.

## 14. Verified behaviour (live E2E, 2026-08-06)

`E2eLeave` probe against the running app (`/api/v1`): create → 2-step chain
(`MANAGER` + `HR`), requester / wrong-dept manager / no-authority denials (403),
step-by-step approval, balance debit (`13` available after 5/18), overlap 409,
cross-year split with auto-provisioned 2027 row + per-year logs, creator cannot
cancel `APPROVED` (403), HR cancel → refund + `leave-cancel:{id}` recompute,
insufficient-balance 400 (stays `PENDING`), pending-queue scoping, ADMIN universal
approver, ADMIN own-request 403, ADMIN create-for-any, reject → `REJECTED` +
`rejectedReason` + both steps rejected, creator withdrawal (200), MANAGER
self-create with single (HR) step, frozen-month approve 409, terminal-state 409.
Full unit suite: 138 tests / 0 failures.

**Correction found by the E2E:** the cancel endpoint originally required only
`leave.approve`, which blocked requester withdrawal (a `USER` lacks that authority).
Relaxed to `leave.approve or leave.write`; service-level guards unchanged and
re-verified.
