# Employee, Organization & Contract — Business Rules (Module 6, DRAFT v0.1)

Status: **APPROVED v0.2** (2026-08-06) with 4 clarifications incorporated (termination
closes ACTIVE contract + strict payroll exclusion; department-name uniqueness moved to
active-only via V16; IRPP `CHEF_DE_FAMILLE` family coverage confirmed; two-phase
implementation plan). Decisions finalized via Q&A: read-only single-company, schedules CRUD
included, single ACTIVE contract enforced, soft-delete everywhere, auto salary_history,
admin → default-company fallback, flat legacy-compatible employee API shape. Frozen
decisions below are normative for the implementation. Same discipline as modules 3–5:
rules are frozen first, the API contract and implementation then match them exactly.

---

## 1. Scope

Backend core-data module (`com.pointagepro.organization`, `com.pointagepro.employee`,
`com.pointagepro.contract`, `com.pointagepro.schedule` — schedule controllers may live in
`com.pointagepro.attendance` next to the existing schedule entities):

- **Organization:** departments, positions, locations (full CRUD).
- **Company:** read-only (single-company deployment).
- **Employee:** identity CRUD + flat legacy-compatible read shape + child records
  (documents, bank accounts, dependents, emergency contacts, tax profiles, assignments).
- **Contract:** `employee_contracts` CRUD + `salary_components` CRUD + auto `salary_history`.
- **Schedules:** `work_schedules` + `work_schedule_lines` CRUD + `employee_schedules`
  assignment (overlap-guarded).

All entities/repositories already exist (V1–V4); this module adds services, controllers,
DTOs and validation. **No new tables** are required. Reuses the existing permission model
(`department.*`, `position.*`, `location.*`, `employee.*`, `document.*`, `contract.*`,
`schedule.*`, `company.read`) — no migration needed for permissions.

## 2. Cross-cutting tenant rule (admin fallback — shared change)

- Every read/write is **company-scoped**: the service derives the tenant from the
  authenticated user, never from the client.
- Today `CurrentUserService.requireCompany(user)` throws for `user.employee_id = NULL`.
  The seeded `admin` has `employee_id = NULL` and is therefore locked out of every
  controller. **Module 6 introduces a fallback:** when `user.employee_id` is `NULL` and the
  user holds the `ADMIN` role, resolve the tenant to the single seeded default company
  (`companies.code = 'DEFAULT'`). This unlocks `admin` for Module 6 **and** for the
  existing modules (leave/attendance/payroll/…).
- An `ADMIN` **with** an `employee_id` keeps their own employee's company. A non-ADMIN
  user without an `employee_id` keeps the existing `AccessDeniedException` (403).
- `CurrentUserService.requireEmployee(user)` is unchanged (returns 403 for `admin`, which
  never needs its own employee row).

## 3. Organization rules

### 3.1 Departments
- Company-scoped. `name` required. `code` optional (≤ 20).
- **Uniqueness — history-safe (V16):** the hard DB key `uk_departments_company_name
  (company_id, name)` blocked historical name reuse, so it is replaced by a generated-column
  partial key that enforces **`name` unique only among active rows**
  (`valid_to IS NULL`). Closing a department (`valid_to` set) frees its name for reuse by a
  future row — history is never corrupted. `code`, when provided, stays **unique per company
  across all rows** (active or closed). Duplicate active name or duplicate code → 409.
  Service layer validates the same rule before the DB constraint trips.
- `manager_employee_id` optional; must reference an employee of the same company → 404/400
  if not. This link feeds the leave/adjustment approval chains (step 1 MANAGER) and the
  shared `ApprovalAuthority`.
- `valid_from` required (defaults to today on create); `valid_to` optional (open-ended =
  still valid). **Overlap rule:** a second department row with the same name overlapping an
  open-ended/active row → 409 (creation should close the old row first).
- **Delete** (soft-delete everywhere, §7): a department referenced by `employees.department_id`,
  `positions.department_id`, or `employee_assignments.department_id` → **409** (message
  suggests closing it by setting `valid_to` instead). Unreferenced rows are hard-deleted.
- Updating `manager_employee_id`/`name`/`code` is allowed at any time (current row).

### 3.2 Positions
- Company-scoped; `name` required; optional `department_id` (same company, FK RESTRICT).
- **Delete:** referenced by `employees.position_id` or `employee_assignments.position_id`
  → **409**; else hard-deleted. Same `valid_from`/`valid_to` semantics as departments.

### 3.3 Locations
- Company-scoped; `name` required; optional `address`; `is_active` boolean (default true).
  No validity window (matches V1 schema).
- **Delete:** referenced by `employees.location_id`, `employee_contracts.location_id`, or
  `terminals.location_id` → **409**; else hard-deleted. To "deactivate" a referenced
  location, set `is_active = false` instead.

## 4. Employee rules

### 4.1 Identity
- Required: `firstName`, `lastName`, `hiringDate`. `company_id`, `status_id`
  (default `ACTIVE`), `nationality` (default `Tunisienne`) are server-set, never client.
- `matricule` optional on create; **auto-generated** when blank: `EMP-%03d` (next sequence
  per company, e.g. `EMP-001`). Unique per company (`uk_employees_company_matricule`) →
  duplicate → 409.
- `rfid_uid` optional and **globally unique** (`uk_employees_rfid`). The badge lookup
  (`findByRfidUid`) is global; a duplicate across the whole DB → 409. Assigning an RFID
  that belongs to another company's employee is rejected the same way.
- `department_id`/`position_id`/`location_id` optional; must belong to the employee's
  company → 404/400 otherwise.
- `gender`/`maritalStatus` optional lookup codes (`M`/`F`; `SINGLE`/`MARRIED`/`DIVORCED`/
  `WIDOWED`).
- `cin`, `passportNumber`, `birthDate`, `email`, `phone`, `address`, `city`, `photoPath`
  optional with length constraints; `email` validated `@Email` when present.
- `exit_date` is only set by the delete/termination flow (§4.4), never via update.

### 4.2 Flat legacy-compatible shape (response + request translation)
The frontend contract expects a flat `Employee` (fields that no longer exist as columns).
The Module-6 response DTO exposes those fields **computed** from the normalized tables;
the request accepts them and **translates** them into the proper child rows.

**Response fields (computed):**
| Field | Source |
|---|---|
| `id`, `matricule`, `firstName`, `lastName`, `cin`, `birthDate`, `email`, `phone`, `address`, `rfidUid`, `photo`, `hiringDate`, `exitDate`, `createdAt`, `updatedAt` | `employees` identity columns |
| `gender`, `maritalStatus` | lookup codes (`M/F`, `SINGLE/...`) |
| `department`, `departmentId` | `employees.department_id` → `departments.name`/`id` |
| `position`, `positionId` | `employees.position_id` → `positions.name`/`id` |
| `contractType` | active contract → `contract_types.code` (null when no active contract) |
| `baseSalary` | active BASE component amount on the active contract (`salary_component_types.code = 'BASE_SALARY'` or category `BASE`) |
| `primeTransport`, `primePerformance`, `primeOther` | BONUS components on the active contract by code `PRIME_TRANSPORT`, `PRIME_RENDEMENT`, `AUTRE` |
| `totalPrimes` | sum of all BONUS-category component amounts on the active contract |
| `weeklySchedule` | current `employee_schedules` → `work_schedules.code`; fallback: company default schedule code |
| `annualLeaveDays`, `maternityLeaveDays`, `paternityLeaveDays` | current-year `leave_balances.entitlement_days` for ANNUAL / MATERNITY / PATERNITY; fallback: `leave_types.default_days_per_year` |
| `status` | **legacy French mapping** — `ACTIVE→ACTIF`, `ON_LEAVE→CONGE`, all other English statuses → `INACTIF` |

**Request fields (flat → normalized translation, only on create/update):**
- `department`/`position` (names): resolved by name within the company → set FK. Name not
  found → **400** ("create the department/position first"). Prefer `departmentId`/`positionId`
  when both supplied (the id wins).
- `contractType` (code, e.g. `CDI`): used when creating the auto **ACTIVE** contract (§5.1).
  Unknown code → 400.
- `baseSalary`, `primeTransport`, `primePerformance`, `primeOther`: translated into
  `salary_components` rows on the auto-created active contract (§5.2). `baseSalary` > 0
  required for a payrolled employee; `0`/null allowed but the employee is excluded from
  payroll with warning "composante BASE_SALARY manquante" until set.
- `weeklySchedule` (schedule code): creates an `employee_schedules` assignment
  `valid_from = hiringDate` (or provided validFrom), overlap-guarded (§6.3). Unknown code
  → 400.
- `annualLeaveDays` / `maternityLeaveDays` / `paternityLeaveDays`: **seeded once on create**
  for **tracked** leave types only (`default_days_per_year IS NOT NULL` → ANNUAL,
  MATERNITY): a `leave_balances` row for the current year with `entitlement_days = value`
  (respecting `uk_balance`). PATERNITY is exempt (default NULL) → field is response-only.
  On update these fields are ignored (balance management belongs to the leave module).
- `status` accepts the legacy French codes (`ACTIF|INACTIF|CONGE`) **or** the full English
  set (`ACTIVE|SUSPENDED|ON_LEAVE|TERMINATED|RESIGNED|RETIRED`); stored as the English
  code (`ACTIF→ACTIVE`, `CONGE→ON_LEAVE`, `INACTIF→SUSPENDED`).

### 4.3 Update
- `PUT /employees/{id}` updates identity + org placement + (flat) salary/schedule
  translation. Changing `department_id`/`position_id`/`location_id` **also writes an
  `employee_assignments` history row** (§4.6) and keeps `employees.*` as the live snapshot.
- rfid/matricule uniqueness re-checked (a change to a value already used → 409).
- Salary fields on update: a provided `baseSalary` **greater than the current active BASE**
  closes the current BASE row (`end_date = new start − 1 day`) and opens the new one, and
  **auto-writes `salary_history`** (§5.3). A provided prime amount updates/replaces the
  matching BONUS component (same code) or creates it.

### 4.4 Soft-delete (`employee.delete`) — termination and payroll
- Sets `status → TERMINATED` and `exit_date = today`. **Never** a physical DELETE: every
  dependent table (attendance, payroll, leave, users.employee_id, manager links) is
  FK RESTRICT. Row stays for history (spec 3.2).
- **Auto-close the ACTIVE contract (added v0.2):** the same transaction also closes the
  employee's open ACTIVE contract — `end_date = exit_date`, `status = EXPIRED`. This
  guarantees a terminated employee can never carry an open ACTIVE contract.
- **Strict payroll exclusion (added v0.2, belt-and-braces):** `PayrollService.resolveContext`
  now also excludes the employee when `exit_date` is strictly **before** the payroll period
  start (`pFirst`). A termination recorded *during* the period keeps the employee payrolled
  pro-rata, capped at `exit_date` (final partial month is still payable); from the **next**
  period on the employee is excluded **regardless of contract status**. Operational note:
  to pay a clean final period, run payroll before recording the termination.
- Re-activating = `PUT` with `status = ACTIVE` and clearing `exit_date`. The auto-closed
  contract stays closed; a new ACTIVE contract is required to resume payroll.

### 4.5 Child records (each scoped to the employee, tenant-checked)
- **Documents** (`document.*`): `documentTypeId` required (lookup), `filePath` required
  (≤ 255; **no upload endpoint in v1** — a path/URL string is stored), optional
  `documentNumber`, `issueDate`, `expiryDate`, `notes`. Parent `employee_documents` is
  FK CASCADE → DELETE physically removes the row.
- **Bank accounts**: `bankId` required (lookup), `accountNumber`/`iban`/`accountHolder`
  optional, `validFrom` required, `validTo` optional. `is_default`: **at most one default
  per employee** — setting `is_default = true` unsets the others (same transaction).
  FK RESTRICT → DELETE only when no payroll/payslip reference exists, else 409.
- **Dependents**: `firstName`, `lastName`, `relationshipId` required; `cin`, `birthDate`,
  `taxDeductible` (default true) optional. FK CASCADE → DELETE physical.
- **Emergency contacts**: `fullName`, `phone` required; `relationship`, `address` optional.
  FK CASCADE → DELETE physical.
- **Tax profiles** (IRPP inputs, dated): `taxSituationId` required, `spouseIsWorking`,
  `numberOfChildren` (0..99), `numberOfDisabledChildren` (0..99), `validFrom` required.
  **Single active row** (`valid_to IS NULL`): creating a new profile closes the current one
  (`valid_to = new.valid_from − 1 day`). Payroll reads the active profile at period end
  (`resolveTaxProfile`).
- **IRPP family-situation coverage (verified against V3 seeds, added v0.2):** the full
  Tunisian IRPP family model is present — `tax_situations`: `CELIBATAIRE` (single),
  `MARIE` (married), **`CHEF_DE_FAMILLE`** (head of family, seeded V3); children via
  `number_of_children`; disabled children via `number_of_disabled_children`; spouse working
  status via `spouse_is_working`. `resolveTaxProfile` already feeds all four into the IRPP
  engine and defaults to `CELIBATAIRE` when no profile exists. No migration needed.
- **Assignments** (`employee_assignments`, dated history — currently dead code): written
  automatically by the service on employee create (initial placement) and on every org
  placement change; exposed read-only. `valid_to` of the previous row set to
  `new valid_from − 1 day`; `employees.*` columns stay the current snapshot.

## 5. Contract rules

### 5.1 Contracts (`employee_contracts`)
- `employeeId`, `contractTypeId`, `startDate` required; `endDate ≥ startDate` (when set);
  `probationEndDate ≥ startDate` and `≤ endDate` (when both set); `workingHoursPerDay`
  default 8.00 (0.5..24); `workingDaysPerWeek` default 5 (1..7); optional
  `noticePeriodDays`, `attachmentPath`, `locationId` (same company).
- **Single ACTIVE contract per employee (enforced).** On create with `status = ACTIVE`:
  - If an ACTIVE contract already exists whose period overlaps the new one → the old
    contract is **auto-closed**: `end_date = new.startDate − 1 day`, `status = EXPIRED`.
  - If the new `startDate` is **before** an existing ACTIVE contract's `startDate` → **409**
    ("a more recent contract already covers this period"). No back-dating over an existing
    ACTIVE contract.
  - The same overlap check runs on update (a change to dates/status re-evaluates siblings).
- Creating a contract with `status = EXPIRED/TERMINATED/SUSPENDED` never closes anything.
- `contract_type` and `status` are looked up by code; unknown code → 400.
- **Delete** (`contract.write`): physical delete only when the contract has **no**
  `salary_components` and no `payroll_items.contract_id` reference → else **409**
  (terminate/expire instead). Because payroll hard-references `contract_id` on every item,
  contracts with any payroll history can never be deleted.
- Auto-created by `POST /employees` when `contractType`/`baseSalary` is supplied
  (§4.2): `CDI` by default, `start_date = hiringDate`, `status = ACTIVE`.

### 5.2 Salary components (`salary_components`)
- Attached to a contract; `componentTypeId` required (lookup), `label` required,
  `startDate` required, `endDate ≥ startDate` (when set), `isActive` default true.
- **Amount vs percentage:** a component is either a fixed `amount` (DECIMAL(10,2) ≥ 0) or a
  percentage (`isPercentage = true` + `percentageValue` 0..100, `amount` may be null).
  Validation rejects: percentage with no `percentageValue`; fixed with `isPercentage = true`.
- **BASE replacement rule (spec 3.2):** at most one BASE component (`code = 'BASE_SALARY'`
  or category `BASE`) is effective on a contract at any date. Creating a new BASE row whose
  `startDate` overlaps the current BASE row closes the old one
  (`end_date = new.startDate − 1 day`); both rows stay (history). Never update the amount on
  the old row.
- **Salary history (auto):** every BASE create/update that changes the effective amount or
  end date writes a `salary_history` row
  (`employee_id`, `contract_id`, `old_amount`, `new_amount`, `change_date = today`,
  `reason`, `changed_by = authenticated user`). Satisfies spec 3.2 (§3.2).
- **Delete** (`contract.write`): physical delete only when no `salary_history` references
  the component and no payroll run consumed it → else **409** (deactivate via `end_date` /
  `is_active = false`).
- Components are resolved by payroll via `findByContractIdAndIsActiveTrueOrderByStartDateDesc`
  filtered to the period — the rules above keep that resolution deterministic.

### 5.3 Salary history (read-only)
- `GET /employees/{id}/salary-history` returns the ordered history
  (`change_date DESC`). Written only by the BASE component flow (§5.2).

## 6. Schedule rules

### 6.1 Work schedules (`work_schedules`)
- Company-scoped; `code` required and **unique per company**, `name` required.
- `is_default`: **at most one default per company** — setting `is_default = true` unsets
  all others (same transaction).
- **Lines** (`work_schedule_lines`, nested in the schedule payload): `weekday` 1..7 (Mon=1)
  required, unique per schedule; `isWorkday` default true; when `isWorkday` is true,
  `startTime` and `endTime` are required and must differ; **night shifts allowed**
  (`endTime < startTime` → crosses midnight; the engine handles `+1440`). `breakMinutes`
  default 0 (0..1440).
- **Delete** (`schedule.write`): referenced by `employee_schedules.schedule_id` or
  `attendance_summary.schedule_id` → **409** (deactivate via `is_active = false`); else
  physical delete (lines cascade).
- The engine resolves an employee's day bounds from `work_schedule_lines` at runtime and
  payroll **snapshots** them into `payroll_attendance_snapshots` — so editing a schedule
  never rewrites frozen payroll, only future recomputes.

### 6.2 Schedule line edits
- Lines are edited through the schedule endpoints (`PUT /schedules/{id}` accepts a full
  `lines` array; replaced wholesale). Removing a line for a weekday = that day becomes
  `NOT_SCHEDULED` for future recomputes.

### 6.3 Employee schedule assignment (`employee_schedules`)
- `scheduleId` must belong to the company; `validFrom` required (≥ employee hiring date →
  else 400), `validTo ≥ validFrom` (when set). Open-ended (`validTo` null) = current.
- **Non-overlap invariant** (reuses `ScheduleAssignmentValidationService.assertNoOverlap`):
  an employee may never have two `employee_schedules` rows whose `[valid_from, valid_to∨∞]`
  intervals overlap → 409. Closing the previous assignment (or editing dates) is the caller's
  job; creating a new open-ended assignment when one is already open → 409.
- **Delete:** physical (no FK references `employee_schedules`); summaries already computed
  keep their own `schedule_id` snapshot.
- Assignment changes do **not** auto-recompute attendance (the manual
  `POST /attendance/recompute` endpoint exists); the rule is documented so operators can
  recompute a changed period.

## 7. Soft-delete policy (all modules)

1. Entities referenced by FK RESTRICT columns are **never** physically deleted:
   `employees` (→ TERMINATED + exit_date), `departments`/`positions`/`locations` (→ 409 if
   referenced, else delete), `employee_contracts` (→ 409 if referenced by components or
   payroll items), `salary_components` (→ 409 if referenced by salary_history/payroll).
2. FK-CASCADE leaf rows (documents, dependents, emergency contacts) are physically deleted.
3. Dated history rows (assignments, tax profiles, salary components, contracts) are closed
   (`valid_to`/`end_date`) rather than deleted whenever they carry history.

## 8. Error model

- **400** validation / business errors (unknown lookup codes, bad dates, bad status).
- **404** unknown id within the tenant (`ResourceNotFoundException`).
- **409** uniqueness conflicts, FK-reference delete blocks, single-ACTIVE/overlap conflicts
  (`ConflictException` / `DuplicateResourceException`).
- **403** missing authority or wrong tenant actor (`AccessDeniedException`).
- Body shape matches the shared `ApiResponse<T> { success, message, data }`; lists of
  resources return `PageResponse<T>` where pagination applies (employee list).

## 9. Out of scope (Module 6)

- Employee file **upload** (path/URL string only). Leave/payroll/attendance recompute
  triggers. Company multi-tenancy management. Auth/user management, notifications, settings
  (settings module). Frontend binding (separate step, contract in `EMPLOYEE_API_CONTRACT.md`).

## 10. Implementation phases (added v0.2 — approved 2026-08-06)

To keep the 67-endpoint surface safe, implementation proceeds in two internal phases; each
ends with `mvn clean verify` green before the next starts:

- **Phase A — core:** Company (read) → Organization (departments/positions/locations) →
  Employee (identity + flat translation + soft-delete/termination) → Contract
  (single-ACTIVE, BASE replacement, salary_history) → Schedule (work schedules + lines +
  assignments) → `CurrentUserService` admin→DEFAULT fallback → payroll exclusion rule.
  Includes the V16 department-uniqueness migration. Unit tests + full build.
- **Phase B — employee child records:** Documents → Bank accounts (single default) →
  Dependents → Emergency contacts → Tax profiles (close-on-create). Unit tests + full
  build.
- E2E probe, DB integrity, cleanup, docs, report, commit + milestone tag run once at the
  end, on the whole Module 6 surface.
