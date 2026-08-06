# VERSION_NOTES

PointagePro — RFID Employee Attendance Management System. Chronological release notes.

All modules were built against the live MySQL 8.4 database and verified with standalone end-to-end probes (HTTP + DB assertions), not just unit tests.

---

## Module 5 — Payroll runs + payslips (FROZEN 2026-08-06)

**Status: FROZEN.** Full verification passed:
- `mvn clean verify`: BUILD SUCCESS — 183 tests, 0 failures / 0 errors / 0 skipped
- Fresh startup clean: Tomcat 8080, context-path `/api/v1`, Flyway schema `pointagepro` V1–V15 up to date (3 benign warnings: Hibernate 5.5.5 dialect note, explicit-dialect hint, open-in-view)
- Payroll E2E: 71/71 checks against MySQL (create → compute → validate → approve → pay; payslip `PP-202607-001`; net 1207.71 from 1500.00 base; frozen-state 409s; future-period 409; cancel → reopen; permission 403s; cross-module frozen guard; DB integrity)
- Cross-module regression probes: Attendance 33/33, Adjustments 38/38, Leave 69/69 (all "ALL E2E STEPS PASSED")
- DB integrity: payroll runs, items, components, attendance snapshots, payslips, audit trail and orphan checks consistent
  - Rounding note: each stored amount is independently rounded to 2 decimals; their sum can differ from `gross` by ±0.01 (observed 1500.01 vs 1500.00) — expected, since `net` is computed from unrounded intermediates then rounded
- All E2E data cleaned; DB restored to post-migration state (only the `admin` user, hash `$2b$10$8kLglwtaF9prVKsB51iQtuCyVVFBdXZDW3iF02TY3yaoqZmVHxkyy`, plus reference/seed data)

### API (base URL `http://<host>:8080/api/v1`)

Context-path `/api/v1` applies globally, so controllers map at `/payrolls` and `/payslips` (no `/api/v1` prefix inside the mappings — the pre-fix double-prefixed URLs return 404).

| Method | Endpoint                  | Description |
|--------|---------------------------|-------------|
| POST   | /payrolls                 | Create run (authority `payroll.run`; 201; idempotent reopen of same DRAFT/COMPUTED/CANCELLED period) |
| POST   | /payrolls/{id}/compute    | 2026 legal math; snapshots frozen attendance; future period → 409 "futur" |
| POST   | /payrolls/{id}/validate   | Validate (authority `payroll.validate`) |
| POST   | /payrolls/{id}/approve    | Approve; generates payslips `PP-YYYYMM-NNN` (`payroll.approve`) |
| POST   | /payrolls/{id}/pay        | Body `bankTransferRef`; writes `paid_at`; audit `PAYROLL_PAY` |
| POST   | /payrolls/{id}/cancel     | Only DRAFT/COMPUTED; artifacts cleared; period re-creatable |
| GET    | /payrolls/{id}            | Run detail |
| GET    | /payrolls/{id}/items      | Per-employee items |
| GET    | /payrolls/{id}/payslips   | Payslips |
| GET    | /payslips/{id}            | Payslip detail (`payslip.read`) |

Frozen states VALIDATED / APPROVED / PAID are immutable → 409. Frozen month also blocks attendance adjustments and leave transitions.

### Payroll math (2026)
- CNSS: 9.68% (employee) / 16.57% (employer)
- IRPP: progressive brackets from `legal.tax_brackets`
- CSS: 0.5% / 1.0% flags on `salary_component_types`
- SMIG floor: 524.954 checks
- Attendance: per-day frozen facts (`payroll_attendance_snapshots`, incl. `is_paid_leave`); workdays = attendance rows, weekends at 0 min

### Migration
- V15: `payrolls.version`, `payroll_attendance_snapshots.is_paid_leave`, `salary_component_types` CSS flags, indexes

---

## Module 4 — Leave management + approval + balance (2026-08-06)

`/leaves`:
- POST create (`leave.write`; MANAGER granted in V14), GET list (employeeId/statusCode/from/to), GET `/{id}`, GET `/pending`, POST `/{id}/approve|reject` (optional comment), POST `/{id}/cancel`, GET `/balance/{employeeId}?year=`
- 2-step chain (approvals, `LEAVE`): MANAGER = requester's dept manager (skipped when none) → HR (always present); requester never decides own steps; ADMIN universal approver
- `daysRequested` server-computed (weekdays − company holidays); overlap 409; span >366 days 400
- Approve: frozen-month 409 → last-step per-year balance debit (tracked types) + recompute `leave:<id>`; insufficient balance → dry-run 400
- Reject: REJECTED, all remaining steps rejected, never debits
- Cancel: creator-while-PENDING or HR/ADMIN; APPROVED-cancel → frozen guard, same-transaction refund + recompute `leave-cancel:<id>`
- Migration V14 (version columns, `leave_balance_logs.operation`, overlap index, MANAGER `leave.write`)
- Verified: 138 tests green, live E2E 69 checks

---

## Module 3 — Attendance adjustments + approval workflow (2026-08-05)

`/attendance/adjustments`:
- POST create (`attendance.adjust`), list (filters), GET `/{id}`, GET `/pending`, POST `/{id}/approve|reject`, POST `/{id}/cancel`
- Chain: MANAGER → HR (auto-decided when creator is HR); empty chain → directly APPLIED; last approval → APPLIED + same-transaction single-day recompute (`adjustment:<id>`)
- Frozen month → 409; 1440-min cap → dry-run 400; terminal states immutable → 409
- Migrations V11 (`CANCELLED`), V12 (`version`), V13 (`work_date`)
- Verified: 100 tests green, live E2E 38 checks
- Fixed during E2E: native `isMonthFrozen` Boolean-cast crash → count + default method; cap dry-run never fired → `DayResult.rawWorkedMinutes`

---

## Module 2 — Attendance API (summaries, day status, recompute) (2026-08-05)

- GET `/attendance/summaries` (employeeId required, from/to optional), `/{id}`, `/day`, `/today` (compute-on-miss), POST `/recompute` + `/recompute/all`
- Verified: 68 tests green, live E2E 33 checks
- Fixed: attributePaths String[] ; `entityEntry is null` NPE (detached computedBy user)

---

## Module 1 — Attendance API (event intake) (2026-08-05)

- POST `/esp32/scan` (X-API-Key, terminal resolution, IN/OUT alternation, 60 s duplicate window, replay dedup)
- POST `/attendance/events` (JWT + method security; 401 JSON entry point)
- Verified: 44 tests green, device flow IN→OUT→replay, summary PRESENT/480

---

## Baseline (2026-07-31)

- Auth (JWT + sessions + login history), Employee CRUD, Leave CRUD + balance, attendance check-in/out, CSV→PDF export, reports, dashboard, settings, users, ESP32 heartbeat scaffolding
- Git baseline `dd71e1a`

---

## Known notes / tech debt

- Runtime quirk (fixed in code, but stale docs/proxies may carry it): only `/payrolls` + `/payslips` (and other unprefixed mappings) resolve under context-path `/api/v1`.
- Startup warnings (benign): Hibernate HHH000511 (MySQLDialect 5.5.5 → use 8.0+), HHH90000025 (explicit dialect redundant), open-in-view enabled.
- Angular 19.2.x (system Node is v18; Angular 22 requires Node 22+).
- Spring Boot 3.4.5 (latest stable with full ecosystem support).
- RTC/NTP time is still −1h from real Tunisia time.
- ESP32 hardware wiring (WS2812B LEDs, button, SD card) still awaiting the physical kit.
