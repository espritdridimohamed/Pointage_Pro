# PointagePro — Attendance API Contract

**Version:** 0.4 (draft — modules 1–2 **implemented and verified end-to-end**; module 3: adjustments + approval workflow, designed 2026-08-05)
**Status:** single source of truth for the HTTP layer; engine behavior stays in `ATTENDANCE_BUSINESS_RULES.md`; DB layout stays in `POINTAGEPRO_DATABASE_DESIGN_PLAN.md`.
**Base path:** `/api/v1` (context path set in `application.yml`).
**Envelope:** every staff endpoint returns `com.pointagepro.shared.ApiResponse<T>` → `{ success, message, data? }`. **Exception:** `/esp32/scan` returns a **flat, firmware-shaped** body (`success`/`action`/…) — see §3.1; this is what `firmware/src/api_client.cpp` deserializes. Errors are produced by `GlobalExceptionHandler` (JSON, non-2xx). Unauthenticated requests to staff endpoints return `401 {success:false,message:"Authentication required"}` via the `SecurityConfig` entry point; authenticated-but-unauthorized returns `403`.

## 1. Scope of this document

This document defines the HTTP contract only. The controllers are thin:
- no business rules, no entity exposure, no repository access;
- they validate input via DTO annotations, resolve the authenticated user / API key,
  then delegate to the existing services (`AttendanceEventService`, `AttendanceEngineService`);
- rules live in the services and in `ATTENDANCE_BUSINESS_RULES.md`.

Module order (each module is designed, coded, tested, then documented here):

| Module | Content | Status |
|---|---|---|
| 1 | Event intake: terminal punch (`/esp32/scan`) + manual entry + event listing | **implemented + verified end-to-end (2026-08-05)** |
| 2 | Summaries: per-employee day summary, day status, recompute trigger | **implemented + verified end-to-end (2026-08-05)** |
| 3 | Adjustments: corrections + approval workflow (create / approve / reject / cancel, frozen-period 409, 1440 dry-run cap, single-day recompute on apply) | designed 2026-08-05 |
| 4 | Schedules / schedule assignments | planned |
| 5 | Holidays | planned |
| 6 | Terminal management: enrollment, heartbeat, firmware | planned |

**Module-1 verification log (2026-08-05):** `mvn -o test` green (44 tests: 17 engine + 17 service + 7 ESP32 controller + 6 staff controller); jar boots clean (Flyway 10 migrations, Hibernate `validate`, 7.4 s); live HTTP checks pass — bad API key → 401, unknown terminal → 400 `Terminal inconnu`, staff without JWT → 401. A seeded terminal + badge + default schedule were used to replay two type-less punches (IN 08:00 → OUT 17:00) plus an `externalRef` replay: events stored, replay absorbed, and `attendance_summary` computed `PRESENT / worked=480 / late=0 / missing=0` (engine rule §1.4). Test data was removed afterwards.

## 2. Cross-cutting conventions

- **Timestamps.** Wire format is ISO-8601 local datetime `yyyy-MM-dd'T'HH:mm:ss` (matches the ESP32 RTC format, see `firmware/src/rtc_manager.h`). All values are stored/compared in server-local time (Tunisia, `serverTimezone=Africa/Tunis`).
- **Enum-ish codes.** `IN` / `OUT` come from the `event_types` reference table (never free text). Any unknown code → `400`.
- **Tenant scoping.**
  - Terminal punches: company comes from the **terminal's** `company_id`; the badge's employee must belong to that company, otherwise the scan is rejected (`UNKNOWN_BADGE`).
  - Manual entries: company comes from the **authenticated user's** employee record (`users.employee_id` → `employees.company_id`). A user with no employee record cannot record attendance (`403`).
- **Authenticated endpoints** require a Bearer JWT (see `SecurityConfig`). Authorization uses Spring method security (`@PreAuthorize` on the controller methods) with the seeded permissions `attendance.read`, `attendance.write`, `attendance.adjust`, `attendance.recalculate`.
- **Device endpoints** (`/esp32/**`) are excluded from JWT auth and are protected by the shared API key in header `X-API-Key` (see §4.3). Verification is constant-time.

### 2.1 Punch classification (single-punch terminal) — decision

The ESP32 sends **one scan per badge with no IN/OUT field** (`{rfidUid, externalRef?, timestamp?}`).
The event type is therefore derived **server-side**, by alternation:

> A type-less punch is classified `OUT` when the employee's most recent stored event
> (any date, strictly before this punch's time) is `IN`; otherwise it is classified `IN`.

- Rationale: models the physical action "badge to enter, badge again to leave". Handles
  standard days and night shifts (IN 22:00 → OUT 06:00 of the next day).
- **Duplicate protection comes first.** Two punches from the same employee on the same
  terminal within `≤ 60 s` (inclusive) are a duplicate regardless of the derived type —
  the second is absorbed (no event stored, no type change). Without this, a badge
  double-read would create a bogus IN→OUT pair and corrupt worked time.
- **Explicit override.** The scan request accepts an optional `eventType` (`"IN"`/`"OUT"`).
  When present and valid, it is used verbatim (future firmware / admin tools). The 60 s
  duplicate window then applies to the explicit type as well.
- Known v1 limitation (documented, not fixed): if an employee's last event is a stale
  `IN` (missed clock-out on a previous day), the next day's first punch is classified
  `OUT`. A time-window heuristic (e.g. > 14 h since last `IN` ⇒ new `IN`) is a planned
  refinement; an explicit `eventType` from the client always wins.

This refines matrix decision #3 and business-rule §2.2: for a type-less terminal the
window is **type-agnostic** (same employee + terminal). Same-type rapid scans from typed
sources are still absorbed by the same rule. See §7.

## 3. Module 1 endpoints

### 3.1 `POST /esp32/scan` — terminal punch (device)

Authenticated by API key header (no JWT). Publishes an `IN`/`OUT` event and triggers the
recompute of the affected shift date(s) via `AttendanceEventService`.

**Request**
```json
{
  "rfidUid": "1A2B3C4D",          // required, badge UID (≤ 30 chars)
  "externalRef": "ESP32-001-00000042",  // optional, ≤ 50 chars; format <deviceSerial>-<seq>
  "timestamp": "2026-08-05T08:00:00",   // optional ISO-8601; absent on live scans → server now
  "eventType": "IN",                    // optional override: "IN" | "OUT"
  "deviceSerial": "ESP32-001"           // optional; recommended for future firmware
}
```

**Terminal resolution (no device field today).** The scan body has no device identifier; the
server resolves the terminal as follows:
1. explicit `deviceSerial` if present (future firmware), else
2. derive the serial from `externalRef`: strip the trailing `-<digits>` sequence
   (`^(.*)-[0-9]+$`), giving `ESP32-001` from `ESP32-001-00000042`, then
   `terminals.serial_number` must match;
3. none derivable → `400` (device contract violation).

**Response 200** — flat body, shape fixed by the firmware (`firmware/src/api_client.cpp`), **not** the `ApiResponse` envelope:
```json
{
  "success": true,
  "action": "IN",
  "employeeName": "Ahmed Ben Salah",
  "matricule": "EMP-0001",
  "message": "Entrée enregistrée",
  "time": "08:00"
}
```
- `success:false` is still HTTP 200 when the scan was deliberately absorbed or unknown
  (device must not see a crash): `action:""`, no employee fields, `time:""`.
- `time` is the stored event time formatted `HH:mm` (24 h).

**Business outcomes**

| Case | HTTP | `success` | `action` | `message` |
|---|---|---|---|---|
| Valid punch stored | 200 | true | `IN`/`OUT` | `Entrée enregistrée` / `Sortie enregistrée` |
| Duplicate within 60 s | 200 | false | `""` | `Scan dupliqué (doublon)` |
| Replay of `externalRef` | 200 | false | `""` | `Scan déjà reçu` |
| Badge not found / other company | 200 | false | `""` | `Badge non reconnu` |
| Terminal not found or no serial derivable | 400 | false | `""` | `Terminal inconnu` |
| Terminal disabled / maintenance | 403 | false | `""` | `Terminal inactif` |
| Missing/invalid API key | 401 | false | `""` | `Clé API invalide` |
| Malformed JSON / bad timestamp | 400 | false | `""` | (validation message) |

Unknown badge is a `200 success:false` (never an exception): the device is not authenticated,
it must display feedback and continue. Terminal disabled and key failures are genuine security
outcomes → 403/401.

### 3.2 `POST /attendance/events` — manual IN/OUT (staff)

Authenticated, `@PreAuthorize("hasAuthority('attendance.write')")`. Company resolved from the
authenticated user's employee record.

**Request**
```json
{
  "employeeId": 12,                 // required, target employee in the same company
  "eventType": "IN",                // required: "IN" | "OUT"
  "timestamp": "2026-08-05T08:00:00",  // required ISO-8601
  "source": "MANUAL"                // optional; default "MANUAL", else "ONLINE"
}
```
The service may validate the manual entry against the duplicate window; a duplicate is
reported as `400` (explicit user action, not a silent absorb). The acting user is recorded
as `computedBy` (audit trail); the raw event is never edited.

**Response 201**
```json
{ "success": true, "message": "Pointage enregistré",
  "data": { "id": 551, "employeeId": 12, "employeeName": "Ahmed Ben Salah",
            "eventType": "IN", "timestamp": "2026-08-05T08:00:00",
            "source": "MANUAL", "terminalId": null, "timeWarning": false,
            "computedBy": { "id": 3, "fullName": "Sami Trabelsi" } } }
```

**Errors** — `400` duplicate / unknown type / bad timestamp; `403` missing permission or
user has no employee record; `404` employee not found.

### 3.3 `GET /attendance/events` — list events (staff)

Authenticated, `@PreAuthorize("hasAuthority('attendance.read')")`. Company scoped to the
authenticated user; `employeeId` must belong to that company.

Query params: `employeeId` (required), `from` (optional `yyyy-MM-dd`), `to` (optional `yyyy-MM-dd`).

**Response 200** (list, ascending by event time)
```json
{ "success": true, "message": "Liste des pointages",
  "data": [ { "id": 551, "employeeId": 12, "employeeName": "Ahmed Ben Salah",
              "eventType": "IN", "timestamp": "2026-08-05T08:00:00",
              "source": "TERMINAL", "terminalId": 7, "terminalCode": "POINT-01",
              "externalRef": "ESP32-001-00000042",
              "timeWarning": false } ] }
```

## 4. Module 2 endpoints — summaries, day status, recompute trigger

All six endpoints are staff endpoints (JWT + `@PreAuthorize`), return the `ApiResponse<T>`
envelope, and resolve the tenant from the authenticated user's employee record
(`CurrentUserService.requireCompany`). Every `employeeId` / summary must belong to that
company, otherwise the resource is reported **404** (never a leak that the row exists).

The materialized per-day row is `AttendanceSummary` (one per `employee_id + work_date`).
Read endpoints never expose the entity: they return `AttendanceSummaryResponse` (§6).

### 4.1 `GET /attendance/summaries` — day-summary list (staff)

`@PreAuthorize("hasAuthority('attendance.read')")`.

Query params: `employeeId` (required), `from` (optional `yyyy-MM-dd`, default **today − 30 d**),
`to` (optional `yyyy-MM-dd`, default **today**).

**Response 200** — `ApiResponse<List<AttendanceSummaryResponse>>`, ascending by `workDate`:
```json
{ "success": true, "message": "Liste des synthèses",
  "data": [ {
    "id": 1001, "employeeId": 12, "employeeName": "Ahmed Ben Salah",
    "workDate": "2026-08-05",
    "dayTypeCode": "WORKDAY", "dayTypeLabel": "Workday",
    "scheduleId": 3, "scheduleCode": "STD-08-17",
    "firstIn": "08:02", "lastOut": "17:05",
    "workedMinutes": 480, "lateMinutes": 2, "earlyExitMinutes": 0,
    "missingMinutes": 0, "overtimeMinutes": 5,
    "nettedWorkMinutes": 0, "adjustmentMinutes": 0,
    "isWeekend": false, "isHoliday": false,
    "statusCode": "PRESENT", "statusLabel": "Present",
    "computedAt": "2026-08-05T17:05:00", "recomputeReason": "event:2026-08-04",
    "computedBy": { "id": 3, "fullName": "Sami Trabelsi" } } ] }
```

Range rules (shared by §4.1, §4.5, §4.6): `from > to` → **400**; span wider than
**365 days** between `from` and `to` (366 calendar days) → **400** (prevents an accidental
full-history recompute). Defaults apply to reads only; recompute requires explicit bounds.

### 4.2 `GET /attendance/summaries/{id}` — single summary (staff)

`@PreAuthorize("hasAuthority('attendance.read')")`. Tenant check on the summary's employee.
Not found **or** wrong company → **404** `Attendance summary not found`. Same response shape
as §4.1's item.

### 4.3 `GET /attendance/summaries/day` — day status (single day, compute-on-miss)

`@PreAuthorize("hasAuthority('attendance.read')")`.

Query params: `employeeId` (required), `date` (optional `yyyy-MM-dd`, default **today**).

**Decision — compute-on-miss (documented for payroll audit).** If no summary exists for the
requested day, the engine recomputes that single day on demand (`recomputeReason = "api:day"`,
`computedBy` = the acting user) before answering. Consequences, deliberately accepted:

- a `GET` may perform a **write**; it is idempotent and cheap (one employee, one day);
- the day status is **always current** — an `ABSENT` or `NOT_SCHEDULED` day materializes on
  first read, no batch recompute required;
- payroll never relies on reads to materialize state: payroll runs trigger **explicit**
  range recomputes (§4.5/§4.6) before freezing snapshots.

A day with no schedule resolves to `statusCode: "NOT_SCHEDULED"` (a summary row is always
materialized for a recomputed day — this endpoint never 404s on a recomputable date).

**Response 200** — `ApiResponse<AttendanceSummaryResponse>` (one item, shape of §4.1).

### 4.4 `GET /attendance/summaries/today` — own current-day status (staff)

`@PreAuthorize("hasAuthority('attendance.read')")`. No query params: the employee is the
authenticated user's own employee record (`CurrentUserService.requireEmployee`; user without
an employee record → **403**). Same compute-on-miss as §4.3 with `recomputeReason = "api:today"`.

**Response 200** — `ApiResponse<AttendanceSummaryResponse>`.

### 4.5 `POST /attendance/recompute` — explicit per-employee recompute (staff)

`@PreAuthorize("hasAuthority('attendance.recalculate')")`.

**Request**
```json
{ "employeeId": 12, "from": "2026-08-01", "to": "2026-08-31", "reason": "correction après oubli" }
```
- `employeeId`, `from`, `to` required; `from ≤ to`; span ≤ 365 days between bounds (400 otherwise).
- `reason` optional (≤ 255 chars), stored in `recompute_reason` for audit; default `api:recompute`.

The service runs the engine's idempotent range recompute (upserts one `AttendanceSummary` per
day), records `computedBy` = acting user, then returns the **recomputed summaries** so the
client refreshes in one call (no second GET).

**Response 200** — `ApiResponse<List<AttendanceSummaryResponse>>`, `message: "Recalcul terminé"`.
Errors: **404** employee missing/wrong company; **400** validation.

### 4.6 `POST /attendance/recompute/all` — company-wide recompute (staff)

`@PreAuthorize("hasAuthority('attendance.recalculate')")`.

**Request**
```json
{ "from": "2026-08-01", "to": "2026-08-31", "reason": "fermeture mensuelle" }
```
`from`, `to` required, same validation as §4.5. Recomputes every employee of the company
(`employeeRepository.findByCompanyIdOrderByLastNameAsc`).

**Response 200** — `ApiResponse<RecomputeStats>` with `message: "Recalcul de masse terminé"`:
```json
{ "success": true, "message": "Recalcul de masse terminé",
  "data": { "companyId": 1, "from": "2026-08-01", "to": "2026-08-31",
            "employeeCount": 12, "dayCount": 372 } }
```
`dayCount = employeeCount × days(from..to)` — the number of summary rows upserted.

## 5. Module 3 endpoints — adjustments (corrections + approval workflow)

Design finalized 2026-08-05 (business rules §5.2/§5.3/§5.6/§5.7, decisions #19). A "day" is always addressed by **`employeeId` + `workDate`**; `attendance_adjustments.summary_id` is attached by the engine when the adjustment is applied, never by the client.

### 5.1 `POST /attendance/adjustments` — create an adjustment request (staff)

- Auth: JWT, `attendance.adjust`. Scope: HR → any employee of the company; Manager → employees of **own department only** (current dept via `employees.department_id`). Employees (`USER` role) cannot create in v1.
- Body `AdjustmentRequest`: `employeeId`, `workDate`, `adjustmentTypeCode` (`ADD_MINUTES` / `REMOVE_MINUTES` / `ADD_OVERTIME` / `REMOVE_OVERTIME` / `SET_ABSENT`), `minutes` (int ≥ 0; `SET_ABSENT` requires 0), `reason` (**required**, ≤ 255).
- Chain materialized at creation (`approvals`, `request_type='ATTENDANCE_ADJUST'`):
  - Step 1 — `MANAGER`: target employee's department manager; skipped when the employee has no manager, the employee IS the manager, or the creator IS the manager.
  - Step 2 — `HR`: any HR member; **auto-decided by the creator** when the creator is HR (creation = HR sign-off).
  - Creator and target employee can never be a step approver.
  - Empty chain (HR creator, no manager step) → created **directly `APPLIED`** with a single-day recompute in the same transaction, reason `adjustment:<id>`.
- Frozen month target (`payrolls` for `(company, year, month)` with status `VALIDATED`/`APPROVED`/`PAID`) → **409** `Payroll period is frozen` (§5.7).
- 201 + `AdjustmentResponse`. Other: 400 validation, 403 scope, 404 employee unknown / other company.

### 5.2 `GET /attendance/adjustments` — list adjustments (staff)

- Auth: JWT, `attendance.read` (company-scoped, like summaries).
- Query: `employeeId` (optional), `status` (optional: `PENDING`/`APPLIED`/`REJECTED`/`CANCELLED`), `from`/`to` (optional `workDate` range; `from > to` → 400). No 366-day cap on this list (status filter is the bound); order `createdAt` desc.
- 200 + list of `AdjustmentResponse`.

### 5.3 `GET /attendance/adjustments/{id}` — single adjustment with chain (staff)

- Auth: JWT, `attendance.read`. Tenant mismatch → **404**.
- 200 + `AdjustmentResponse` (includes the full `approvals` chain: step, role, approver, status, comment, decidedAt).

### 5.4 `GET /attendance/adjustments/pending` — my approval queue (staff)

- Auth: JWT, `attendance.adjust`.
- Returns adjustments where the caller is the current step's approver and the step is `PENDING`: `MANAGER` steps resolve the target employee's department manager; `HR` steps resolve any HR member.
- 200 + list of `AdjustmentResponse` (each annotated with the pending step).

### 5.5 `POST /attendance/adjustments/{id}/approve` — approve the current step (staff)

- Auth: JWT, `attendance.adjust`. Caller must be the approver of the **current** pending step (role + scope); otherwise **403**.
- Body `AdjustmentDecisionRequest`: `comment` (optional, ≤ 500).
- Guards (all evaluated before the step flips):
  - not `PENDING` (already decided/terminal) → **409**;
  - target month frozen → **409**;
  - **dry-run cap**: the day's final `worked_minutes` computed with this adjustment included must be ≤ 1440; otherwise **400** and the request stays `PENDING` (engine clamp remains only a backstop).
- Step → `APPROVED`; next step becomes current, or, if this was the last step → adjustment `APPLIED` + single-day engine recompute (same transaction, reason `adjustment:<id>`).
- 200 + `AdjustmentResponse`.

### 5.6 `POST /attendance/adjustments/{id}/reject` — reject (staff)

- Auth: JWT, `attendance.adjust`. Caller must be the current step's approver (403 otherwise).
- Body `AdjustmentDecisionRequest`: `comment` optional.
- Request → `REJECTED`; the acted step `REJECTED` and all remaining steps set `REJECTED` (consistent chain). Never recomputes.
- 200 + `AdjustmentResponse`.

### 5.7 `POST /attendance/adjustments/{id}/cancel` — cancel a pending request (staff)

- Auth: JWT, `attendance.adjust` (any HR) **or** the creator. Only while `PENDING` (409 otherwise).
- Body `AdjustmentCancelRequest`: `reason` (**required**, ≤ 255).
- Request → `CANCELLED`; pending steps set `CANCELLED`. Never recomputes.
- 200 + `AdjustmentResponse`.

### 5.8 Terminality and audit

- `APPLIED` / `REJECTED` / `CANCELLED` are terminal and immutable — no edit/delete; further correction = new request.
- Every transition is race-safe (guarded status update `WHERE status_id = PENDING`) and written to `audit_logs` (`CREATE`/`STATUS_CHANGE`, old/new JSON) via the module's `AuditService`.
- Adjustments never touch payroll; summaries recompute freely, `computed_at`/`recompute_reason`/`computed_by` are overwritten as usual.

## 6. Security notes

- `SecurityConfig` already whitelists `/esp32/**` (no JWT). The ESP32 endpoints are protected
  by the shared key `app.esp32.api-key` in `X-API-Key`, verified in constant time.
- JWT still applies to everything else; method-level `@PreAuthorize` granularity is enabled
  via `@EnableMethodSecurity` on `SecurityConfig`.
- `AttendanceEventService.record` enforces the company-consistency invariant: an employee
  whose company differs from the terminal's company is rejected at the service layer.
- Module 2 gates recompute endpoints on `attendance.recalculate` (seeded on the HR role,
  `V2__seed_reference_data.sql`).
  Recompute is explicit and audited: every summary stores `computedBy` + `recompute_reason`.
- Module 3: all adjustment **writes** gate on `attendance.adjust` (create/approve/reject/cancel),
  reads on `attendance.read`. Approver resolution is **step-role based** (`MANAGER` → target
  employee's department manager; `HR` → any HR member) and never lets the creator or the target
  employee decide a step. Manager scope on create is limited to their own department.
- No entity is ever serialized: responses use DTOs.

## 7. Data types (DTOs)

| DTO | Purpose |
|---|---|
| `TerminalScanRequest` | `/esp32/scan` request (rfidUid, externalRef, timestamp, eventType) |
| `TerminalScanResponse` | `/esp32/scan` response — flat, firmware-shaped (see §3.1) |
| `AttendanceEventRequest` | manual entry request |
| `AttendanceEventResponse` | manual entry response / list item |
| `AttendanceEventResult` (service record) | service → controller outcome: status + event + employee + action |
| `AttendanceSummaryResponse` | summary / day-status item (id, employee, workDate, dayType, schedule, times, minutes, flags, status, audit) |
| `RecomputeStats` (service record) | `POST /attendance/recompute/all` result: companyId, from, to, employeeCount, dayCount |
| `RecomputeRequest` | per-employee recompute body (employeeId, from, to, reason?) |
| `RecomputeAllRequest` | company-wide recompute body (from, to, reason?) |
| `AdjustmentRequest` | create body (employeeId, workDate, adjustmentTypeCode, minutes, reason) |
| `AdjustmentDecisionRequest` | approve/reject body (comment?) |
| `AdjustmentCancelRequest` | cancel body (reason — required) |
| `AdjustmentResponse` | adjustment item (id, employee, workDate, type, minutes, reason, status, creator/approver, dates, approvals chain) |
| `ApprovalStepResponse` | one approvals row (stepOrder, approverRole, approver, status, comment, decidedAt) |

`AttendanceEventResult.Status`: `STORED`, `DUPLICATE`, `REPLAY`, `UNKNOWN_BADGE`, `TERMINAL_INACTIVE`.
Statuses that are genuine errors (`UNKNOWN_BADGE`, `TERMINAL_INACTIVE`) are **not** thrown —
the ESP32 controller maps them to a `success:false` body; the manual controller maps them to
HTTP errors.

## 8. Non-goals for this module

- Heartbeat, firmware version check, enrollment/activation → module 6.
- Day-level status is served by module 2 (§4.3/§4.4). Period aggregation (payroll summaries,
  monthly stats, reports) → payroll/reports modules; payroll will call the recompute
  endpoints (or the engine directly) before freezing snapshots.
- Pagination: deferred until list endpoints are finalized (summary lists are currently bounded
  by the §4 range cap of 366 calendar days).
- Module 3 additions: **editing/deleting adjustments** (terminal states are immutable by design); **employee self-service requests** (v1 is in-person via HR/Manager, §5.1); **payroll reopening workflow** (explicit, audit-logged, future payroll module); **approval delegation / notifications** (later).

## 9. Doc updates caused by this module

- `ATTENDANCE_BUSINESS_RULES.md` §0.3 + §2.2: duplicate window is **type-agnostic** for
  terminal punches (was "same event type"); alternation classification added as §2.8.
- Module 2 additions: §4.3/§4.4 compute-on-miss decision, §4 range cap and audit rule —
  mirrored in `ATTENDANCE_BUSINESS_RULES.md` (recompute trigger section) and
  `PROJECT_MAP.md` (pending + VERSION_NOTES).
- Module 3 additions: business rules §5.2 rewritten (chain builder + immediate apply),
  §5.3 cap corrected to dry-run-reject (was "rejected at apply time"; engine actually
  clamps), new §5.6 (cancellation) and §5.7 (frozen-period block), §7.4 enforcement note,
  confirmed decision #19. Schema: **V11** seeds `CANCELLED` into `adjustment_statuses` +
  `approval_statuses`; **V12** adds `version` optimistic-locking columns to
  `attendance_adjustments` + `approvals`; **V13** adds `attendance_adjustments.work_date`
  (the target day must be stored directly while the request is PENDING, before any
  summary link exists). `PROJECT_MAP.md` pending + VERSION_NOTES updated at close.
