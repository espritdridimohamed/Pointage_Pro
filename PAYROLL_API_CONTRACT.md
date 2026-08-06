# Payroll Management — API Contract (Module 5, v1.0)

Frozen 2026-08-06 against `PAYROLL_BUSINESS_RULES.md`. Conventions follow
`LEAVE_API_CONTRACT.md`: French `ApiResponse` messages, thin controllers
(Controller → DTO → Service → Domain), no entity serialization, method-level
`@PreAuthorize` on authority codes.

Base path: `/api/v1/payrolls` (payslip reads under `/api/v1/payslips`) — note the
`/api/v1` prefix comes from the servlet `context-path`, so the controllers map at
`/payrolls` and `/payslips`. All endpoints require a valid JWT and are scoped to
the caller's company (`currentUserService.requireCompany(user)`); tenant
mismatch → 404.

---

## 1. Shared response envelope

Every endpoint returns `ApiResponse<T>`:

```json
{ "success": true, "message": "…", "data": { … } }
```

Error mapping (shared `GlobalExceptionHandler`): **400** validation/
`IllegalArgumentException`, **403** `AccessDeniedException`, **404**
`ResourceNotFoundException` (also tenant mismatch), **409** `ConflictException`
(period already exists, invalid state transition, frozen run, optimistic-lock
race). Unauthenticated → **401** (existing).

## 2. `POST /payrolls` — create a draft run

- Auth: JWT, `payroll.run`.
- Body `PayrollRunCreateRequest`:
  ```json
  { "periodYear": 2026, "periodMonth": 8, "notes": null }
  ```
  `periodYear`, `periodMonth` required (month 1–12); `notes` optional (≤ 500).
- Behavior per existing period (frozen in §3 of the rules):
  - no run → **201** new `DRAFT`;
  - existing `CANCELLED` → reopened to `DRAFT` (items/snapshots/payslips
    deleted, totals reset), **200** with the reopened run;
  - existing `DRAFT` → returned as-is, **200** (idempotent);
  - existing `COMPUTED`/`VALIDATED`/`APPROVED`/`PAID` → **409**.
- 400: missing/out-of-range `periodYear`/`periodMonth`. Audit `CREATE`.

## 3. `POST /payrolls/{id}/compute` — freeze + compute (and recompute)

- Auth: JWT, `payroll.run`.
- Run must be `DRAFT` or `COMPUTED` (frozen `VALIDATED`+ → **409**); period
  must not be entirely in the future (`P.first > today` → **409**).
- Idempotent: deletes existing items/snapshots/payslips, re-freezes attendance
  facts (compute-on-miss per day, incl. resolving `is_paid_leave`), computes
  items/components/totals per `PAYROLL_BUSINESS_RULES.md` §4, status →
  `COMPUTED`, `run_date = today`. Optimistic lock on `Payroll` (409 on race).
- 200 + `PayrollRunResponse` (status `COMPUTED`, totals, `employeeCount`,
  `warnings[]`). Audit `PAYROLL_RUN`.

## 4. `POST /payrolls/{id}/validate` — validate computed run

- Auth: JWT, `payroll.validate`. Only from `COMPUTED` → `VALIDATED` (else 409).
- Body optional `PayrollNoteRequest`: `notes` (≤ 500) appended.
- 200 + `PayrollRunResponse`. Audit `STATUS_CHANGE`.

## 5. `POST /payrolls/{id}/approve` — approve validated run

- Auth: JWT, `payroll.approve`. Only from `VALIDATED` → `APPROVED`;
  `approved_by`/`approved_at` set. In the same transaction a payslip is created
  per item: `payslip_number = "PP-" + yyyyMM + "-" + 3-digit sequence`
  (e.g. `PP-202608-001`), `issued_at = now`, `pdf_path` null.
- 200 + `PayrollRunResponse`. Audit `PAYROLL_APPROVE` + `STATUS_CHANGE`.

## 6. `POST /payrolls/{id}/pay` — mark approved run as paid

- Auth: JWT, `payroll.pay`. Only from `APPROVED` → `PAID` (else 409);
  `paid_at = now`. Body optional `PayrollPayRequest`: `bankTransferRef` (≤ 50)
  written to every item's `bank_transfer_ref`. `PAID` is terminal.
- 200 + `PayrollRunResponse`. Audit `PAYROLL_PAY` + `STATUS_CHANGE`.

## 7. `POST /payrolls/{id}/cancel` — cancel a draft/computed run

- Auth: JWT, `payroll.cancel`. Only from `DRAFT`/`COMPUTED` → `CANCELLED`;
  items/snapshots/payslips deleted, totals reset. Frozen/terminal → **409**.
- Body optional `PayrollNoteRequest`: `notes` (≤ 500).
- 200 + `PayrollRunResponse`. Audit `STATUS_CHANGE`. A `CANCELLED` run frees
  the period (§2).

## 8. `GET /payrolls` — list runs

- Auth: JWT, `payroll.read`.
- Query: `year` (optional), `month` (optional), `status` (optional:
  `DRAFT`/`COMPUTED`/`VALIDATED`/`APPROVED`/`PAID`/`CANCELLED`). Order
  `periodYear` desc, `periodMonth` desc.
- 200 + list of `PayrollRunResponse` (without items/warnings).

## 9. `GET /payrolls/{id}` — single run summary

- Auth: JWT, `payroll.read`. Tenant mismatch → **404**.
- 200 + `PayrollRunResponse` (totals, `employeeCount`, `warnings[]`, notes,
  audit stamps).

## 10. `GET /payrolls/{id}/items` — run items

- Auth: JWT, `payroll.read`. 404 unknown/tenant mismatch; 409 if the run has
  no computed items (still `DRAFT`).
- 200 + list of `PayrollItemResponse` (each with its component lines).

## 11. `GET /payrolls/{id}/payslips` — run payslips

- Auth: JWT, `payslip.read`. 200 + list of `PayslipResponse` (created at
  approve; empty list before that).

## 12. `GET /payslips/{id}` — single payslip detail

- Auth: JWT, `payslip.read`. Tenant mismatch → **404**.
- 200 + `PayslipResponse` (full line detail: components + legal deductions).

---

## 13. DTOs

```json
// PayrollRunResponse (run-level)
{
  "id": 3, "periodYear": 2026, "periodMonth": 8,
  "runDate": "2026-08-06",
  "status": { "code": "COMPUTED", "label": "Computed" },
  "totals": {
    "gross": 41520.50, "cnss": 4019.18, "irpp": 3650.12,
    "css": 207.60, "deductions": 1050.00, "net": 33593.60
  },
  "employeeCount": 8,
  "createdBy": 1, "approvedBy": null, "approvedAt": null, "paidAt": null,
  "notes": null,
  "warnings": ["Employé 12 exclu : pas de contrat actif sur la période"],
  "createdAt": "2026-08-06T10:00:00", "updatedAt": "2026-08-06T10:00:00"
}
```

```json
// PayrollItemResponse
{
  "id": 41, "employee": { "id": 5, "matricule": "E001", "firstName": "…", "lastName": "…" },
  "contractId": 9, "baseSalary": 1500.00,
  "workDays": 21, "workHours": 168.00,
  "overtimeMinutes": 240, "overtimeAmount": 29.64,
  "absenceMinutes": 0, "absenceDeduction": 0.00,
  "lateMinutes": 45, "lateDeduction": 7.42,
  "grossSalary": 1522.22, "cnssSalarial": 147.32, "cnssPatronal": 252.13,
  "irpp": 38.44, "css": 7.61, "netSalary": 1328.85,
  "cancelled": false, "bankTransferRef": null,
  "components": [
    { "componentTypeCode": "BASE_SALARY", "label": "Base salary", "category": "BASE",
      "amount": 1500.00, "isPercentage": false, "percentageValue": null, "sortOrder": 1 },
    { "componentTypeCode": "PRIME_TRANSPORT", "label": "Transport prime", "category": "BONUS",
      "amount": 40.00, "isPercentage": false, "percentageValue": null, "sortOrder": 2 }
  ]
}
```

```json
// PayslipResponse
{
  "id": 12, "payslipNumber": "PP-202608-001",
  "payrollItem": { "id": 41, "periodYear": 2026, "periodMonth": 8,
                   "employee": { "id": 5, "matricule": "E001", "firstName": "…", "lastName": "…" } },
  "grossSalary": 1522.22, "cnssSalarial": 147.32, "irpp": 38.44, "css": 7.61,
  "netSalary": 1328.85, "issuedAt": "2026-08-06T12:00:00",
  "pdfPath": null, "sentAt": null,
  "components": [ … same as PayrollItemResponse.components ]
}
```

| DTO | Purpose |
|---|---|
| `PayrollRunCreateRequest` | create body (periodYear, periodMonth, notes?) |
| `PayrollNoteRequest` | validate/cancel body (notes?) |
| `PayrollPayRequest` | pay body (bankTransferRef?) |
| `PayrollRunResponse` | run summary (period, status, totals, employeeCount, warnings, stamps) |
| `PayrollItemResponse` | item with attendance/money fields + components |
| `PayrollComponentResponse` | component line (type code, label, category, amount, %, sortOrder) |
| `PayslipResponse` | payslip (number, item ref, amounts, components, issuedAt) |

## 14. Security notes

- Writes gate on `payroll.run` (create/compute), `payroll.validate`,
  `payroll.approve`, `payroll.pay`, `payroll.cancel`; run reads on
  `payroll.read`; payslips on `payslip.read`. All seeded on ACCOUNTANT + ADMIN
  (V2). Single-step approval: any holder of the relevant authority may act
  (frozen decision).
- No entity is ever serialized; responses are DTOs. All lookups are company-
  scoped; foreign-company resources → 404.

## 15. Non-goals (v1)

Listed in `PAYROLL_BUSINESS_RULES.md` §14 (no PDF/email, flat overtime, no
post-validation correction, no employee self-service payslip, no legal-rates
endpoint).

## 16. Doc updates caused by this module

- `PAYROLL_BUSINESS_RULES.md` + `PAYROLL_API_CONTRACT.md` (this design, draft
  for approval) + `PROJECT_MAP.md`/`VERSION_NOTES.md` at close.
- Schema `V15`: `is_paid_leave` on `payroll_attendance_snapshots`, `version` on
  `payrolls`, CSS flag fix on `BASE_SALARY`/`HEURES_SUP` component types.
- No changes to modules 1–4; frozen-month guard already consumed by leave/
  adjustments remains untouched.

## 17. Verification plan (post-approval)

- Unit tests: engine math matrix (presence, absence, overtime, CNSS, IRPP
  brackets, IRPP family deduction, CSS, net, rounding), lifecycle transitions,
  eligibility/exclusion, idempotent recompute, concurrent transition 409.
- Live E2E probe (`E2ePayroll.java`) against `/api/v1`: full cycle
  create→compute→validate→approve→pay, per-state transition 409s, period 409,
  cancel→reopen, payslip numbering, permissions 403, frozen-run compute 409,
  frozen-month guard still blocking leave/adjustment edits for the run month.
- DB-integrity re-run (snapshot/items/components/payslips consistent with
  totals; `is_paid_leave` correct for unpaid-leave days).
- `mvn clean verify` + cleanup to empty tables, then commit `module-5-payroll`.
