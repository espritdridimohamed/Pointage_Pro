# Employee, Organization & Contract — API Contract (Module 6, DRAFT v0.1)

Status: **APPROVED v0.2** (2026-08-06). v0.2 clarifications: DELETE employee auto-closes
the ACTIVE contract + strict payroll exclusion; department `name` unique among active rows
only (V16 migration), `code` unique per company; IRPP family coverage confirmed
(`CHEF_DE_FAMILLE` seeded); two-phase implementation.
Base URL: `http://<host>:8080/api/v1` (context-path `/api/v1`; all mappings below are
relative — do **not** prefix them with `/api/v1`).
Envelope: `ApiResponse<T>` = `{ success, message, data }`; paginated lists use
`PageResponse<T>` = `{ content, page, size, totalElements, totalPages }`.
Errors: `{ success:false, message, ... }` with HTTP 400/403/404/409 (see §8 of
`EMPLOYEE_BUSINESS_RULES.md`). Auth: `Authorization: Bearer <jwt>`.

---

## 1. Endpoint map

| # | Method | Path | Auth | Description |
|---|--------|------|------|-------------|
| 1 | GET | `/companies/me` | `company.read` | Current user's company + settings |
| 2 | GET | `/departments` | `department.read` | List departments (company) |
| 3 | POST | `/departments` | `department.write` | Create department |
| 4 | GET | `/departments/{id}` | `department.read` | Get department |
| 5 | PUT | `/departments/{id}` | `department.write` | Update department |
| 6 | DELETE | `/departments/{id}` | `department.write` | Delete (409 if referenced) |
| 7 | GET | `/positions` | `position.read` | List positions (company) |
| 8 | POST | `/positions` | `position.write` | Create position |
| 9 | GET | `/positions/{id}` | `position.read` | Get position |
| 10 | PUT | `/positions/{id}` | `position.write` | Update position |
| 11 | DELETE | `/positions/{id}` | `position.write` | Delete (409 if referenced) |
| 12 | GET | `/locations` | `location.read` | List locations (company) |
| 13 | POST | `/locations` | `location.write` | Create location |
| 14 | GET | `/locations/{id}` | `location.read` | Get location |
| 15 | PUT | `/locations/{id}` | `location.write` | Update location |
| 16 | DELETE | `/locations/{id}` | `location.write` | Delete (409 if referenced) |
| 17 | GET | `/employees` | `employee.read` | List employees (paginated/search) |
| 18 | GET | `/employees/{id}` | `employee.read` | Get employee (flat shape) |
| 19 | POST | `/employees` | `employee.write` | Create employee (+ flat translation) |
| 20 | PUT | `/employees/{id}` | `employee.write` | Update employee |
| 21 | DELETE | `/employees/{id}` | `employee.delete` | Soft-delete (TERMINATED + exit_date) |
| 22 | GET | `/employees/departments` | `employee.read` | Distinct department names (legacy) |
| 23 | GET | `/employees/count` | `employee.read` | `{ count }` (legacy) |
| 24 | GET | `/employees/{id}/documents` | `document.read` | List documents |
| 25 | POST | `/employees/{id}/documents` | `document.write` | Add document |
| 26 | GET | `/employees/{id}/documents/{docId}` | `document.read` | Get document |
| 27 | PUT | `/employees/{id}/documents/{docId}` | `document.write` | Update document |
| 28 | DELETE | `/employees/{id}/documents/{docId}` | `document.write` | Delete document |
| 29 | GET | `/employees/{id}/bank-accounts` | `employee.read` | List bank accounts |
| 30 | POST | `/employees/{id}/bank-accounts` | `employee.write` | Add bank account |
| 31 | GET | `/employees/{id}/bank-accounts/{baId}` | `employee.read` | Get bank account |
| 32 | PUT | `/employees/{id}/bank-accounts/{baId}` | `employee.write` | Update bank account |
| 33 | DELETE | `/employees/{id}/bank-accounts/{baId}` | `employee.write` | Delete (409 if referenced) |
| 34 | GET | `/employees/{id}/dependents` | `employee.read` | List dependents |
| 35 | POST | `/employees/{id}/dependents` | `employee.write` | Add dependent |
| 36 | GET | `/employees/{id}/dependents/{depId}` | `employee.read` | Get dependent |
| 37 | PUT | `/employees/{id}/dependents/{depId}` | `employee.write` | Update dependent |
| 38 | DELETE | `/employees/{id}/dependents/{depId}` | `employee.write` | Delete dependent |
| 39 | GET | `/employees/{id}/emergency-contacts` | `employee.read` | List emergency contacts |
| 40 | POST | `/employees/{id}/emergency-contacts` | `employee.write` | Add emergency contact |
| 41 | GET | `/employees/{id}/emergency-contacts/{ecId}` | `employee.read` | Get emergency contact |
| 42 | PUT | `/employees/{id}/emergency-contacts/{ecId}` | `employee.write` | Update emergency contact |
| 43 | DELETE | `/employees/{id}/emergency-contacts/{ecId}` | `employee.write` | Delete emergency contact |
| 44 | GET | `/employees/{id}/tax-profiles` | `employee.read` | List tax profiles |
| 45 | POST | `/employees/{id}/tax-profiles` | `employee.write` | Create tax profile (closes current) |
| 46 | PUT | `/employees/{id}/tax-profiles/{tpId}` | `employee.write` | Update tax profile |
| 47 | GET | `/employees/{id}/assignments` | `employee.read` | Assignment history (read-only) |
| 48 | GET | `/employees/{id}/contracts` | `contract.read` | List contracts |
| 49 | POST | `/employees/{id}/contracts` | `contract.write` | Create contract (single-ACTIVE) |
| 50 | GET | `/contracts/{id}` | `contract.read` | Get contract |
| 51 | PUT | `/contracts/{id}` | `contract.write` | Update contract (re-check overlap) |
| 52 | DELETE | `/contracts/{id}` | `contract.write` | Delete (409 if referenced) |
| 53 | GET | `/contracts/{id}/components` | `contract.read` | List salary components |
| 54 | POST | `/contracts/{id}/components` | `contract.write` | Add component (BASE replaces old) |
| 55 | GET | `/contracts/{id}/components/{cid}` | `contract.read` | Get component |
| 56 | PUT | `/contracts/{id}/components/{cid}` | `contract.write` | Update component (+ salary_history) |
| 57 | DELETE | `/contracts/{id}/components/{cid}` | `contract.write` | Delete (409 if referenced) |
| 58 | GET | `/employees/{id}/salary-history` | `contract.read` | Salary history (read-only) |
| 59 | GET | `/schedules` | `schedule.read` | List work schedules |
| 60 | POST | `/schedules` | `schedule.write` | Create schedule (with lines) |
| 61 | GET | `/schedules/{id}` | `schedule.read` | Get schedule |
| 62 | PUT | `/schedules/{id}` | `schedule.write` | Update schedule (replace lines) |
| 63 | DELETE | `/schedules/{id}` | `schedule.write` | Delete (409 if referenced) |
| 64 | GET | `/employees/{id}/schedules` | `schedule.read` | Employee schedule assignments |
| 65 | POST | `/employees/{id}/schedules` | `schedule.write` | Assign schedule (overlap-guard) |
| 66 | PUT | `/employees/{id}/schedules/{asgId}` | `schedule.write` | Change assignment dates |
| 67 | DELETE | `/employees/{id}/schedules/{asgId}` | `schedule.write` | Delete assignment |

> Route order note: static `/employees/departments` and `/employees/count` must be declared
> before `/employees/{id}` so they are not captured as an id.

---

## 2. DTOs

### 2.1 Company (read-only)
`CompanyResponse`:
```json
{
  "id": 1, "code": "DEFAULT", "name": "Default Company", "legalName": "Default Company",
  "taxId": null, "cnssNumber": null, "address": null, "city": null, "phone": null,
  "email": null, "website": null, "currency": "TND", "logoPath": null,
  "settings": { "fiscalYearStartMonth": 1, "weeklyWorkingHours": 40.00,
                "monthlyWorkingHours": 151.67, "overtimeEnabled": true,
                "overtimeRateMultiplier": 1.25, "hoursNettingEnabled": false }
}
```
`GET /companies/me` → `ApiResponse<CompanyResponse>`.

### 2.2 Department
`DepartmentRequest`: `{ code?, name*, managerEmployeeId?, validFrom?, validTo? }`
(`validFrom` defaults to today; `managerEmployeeId` must be a same-company employee).
**Uniqueness (v0.2):** `name` unique among **active** rows only (`valid_to IS NULL`) —
closing a department frees its name for historical reuse (V16 relaxes the old
`uk_departments_company_name`); `code` unique per company across all rows when provided.
Duplicate active name or duplicate code → 409.
`DepartmentResponse`:
```json
{ "id": 1, "code": "RH", "name": "Ressources Humaines",
  "managerEmployeeId": 3, "managerName": "Sami Ben Ali",
  "validFrom": "2026-01-01", "validTo": null, "employeeCount": 12 }
```
- `GET /departments` → `ApiResponse<List<DepartmentResponse>>` (name ASC).
- `POST` → 201 `ApiResponse<DepartmentResponse>`; `PUT` → 200; `DELETE` → 200 (409 if referenced).

### 2.3 Position
`PositionRequest`: `{ code?, name*, departmentId?, validFrom?, validTo? }`.
`PositionResponse`: `{ id, code, name, departmentId, departmentName, validFrom, validTo,
employeeCount }`.

### 2.4 Location
`LocationRequest`: `{ code?, name*, address?, isActive? }`.
`LocationResponse`: `{ id, code, name, address, isActive, employeeCount, terminalCount }`.

### 2.5 Employee (flat legacy-compatible)
`EmployeeRequest` (POST/PUT; `*` required):
```json
{
  "matricule": "EMP-001",
  "firstName": "Ahmed",
  "lastName": "Ben Salah",
  "cin": "09543210",
  "passportNumber": null,
  "birthDate": "1990-05-14",
  "gender": "M",
  "maritalStatus": "MARRIED",
  "nationality": "Tunisienne",
  "email": "ahmed@sepabagro.tn",
  "phone": "+216 22 111 222",
  "address": "12 rue des Roses",
  "city": "Tunis",
  "department": "Ressources Humaines",
  "departmentId": 1,
  "position": "RH Officer",
  "positionId": 2,
  "locationId": 1,
  "contractType": "CDI",
  "baseSalary": 1500.00,
  "primeTransport": 50.00,
  "primePerformance": null,
  "primeOther": null,
  "rfidUid": "A1B2C3D4",
  "photo": null,
  "weeklySchedule": "STD",
  "annualLeaveDays": 18,
  "maternityLeaveDays": 90,
  "paternityLeaveDays": null,
  "hiringDate": "2026-01-01",
  "status": "ACTIF"
}
```
Rules: `matricule` optional (auto `EMP-%03d`); `department`/`position` are names resolved
within the company (id wins if both given); `contractType` code used for the auto ACTIVE
contract; `baseSalary` > 0 for a payrolled employee; `weeklySchedule` = schedule code;
`annualLeaveDays`/`maternityLeaveDays` seeded on create (tracked types) then response-only;
`status` accepts French legacy or English codes (stored English).

`EmployeeResponse` (computed flat shape — see §4.2 of the business rules):
```json
{
  "id": 1, "matricule": "EMP-001", "firstName": "Ahmed", "lastName": "Ben Salah",
  "cin": "09543210", "passportNumber": null, "birthDate": "1990-05-14",
  "gender": "M", "maritalStatus": "MARRIED", "nationality": "Tunisienne",
  "email": "ahmed@sepabagro.tn", "phone": "+216 22 111 222", "address": "12 rue des Roses",
  "city": "Tunis",
  "department": "Ressources Humaines", "departmentId": 1,
  "position": "RH Officer", "positionId": 2, "locationId": 1,
  "contractType": "CDI",
  "baseSalary": 1500.00, "primeTransport": 50.00, "primePerformance": 0.00,
  "primeOther": 0.00, "totalPrimes": 50.00,
  "rfidUid": "A1B2C3D4", "photo": null, "weeklySchedule": "STD",
  "annualLeaveDays": 18, "maternityLeaveDays": 90, "paternityLeaveDays": null,
  "hiringDate": "2026-01-01", "exitDate": null,
  "status": "ACTIF",
  "createdAt": "2026-08-06T10:00:00", "updatedAt": "2026-08-06T10:00:00"
}
```
- `GET /employees?page=0&size=50&search=&department=` → `ApiResponse<PageResponse<EmployeeResponse>>`
  (search: firstName/lastName/matricule `LIKE`; `department` = department name; sorted
  `created_at DESC`). `page` default 0, `size` default 20, max 100.
- `GET /employees/departments` → `ApiResponse<List<String>>` (distinct dept names).
- `GET /employees/count` → `ApiResponse<{ "count": 27 }>`.
- `POST` → 201; `PUT` → 200; `DELETE` → 200 (soft-delete).
- **`DELETE /employees/{id}` termination semantics (v0.2):** sets `status = TERMINATED`,
  `exit_date = today`, and in the same transaction **auto-closes the open ACTIVE contract**
  (`end_date = exit_date`, `status = EXPIRED`). Combined with the payroll exclusion rule
  (§4 #1) a terminated employee can never be paid for a period starting after `exit_date`.

### 2.6 Document
`DocumentRequest`: `{ documentTypeId*, filePath*, documentNumber?, issueDate?, expiryDate?,
notes? }`.
`DocumentResponse`: `{ id, documentTypeId, documentType, filePath, documentNumber, issueDate,
expiryDate, notes, createdAt }`.
`GET /employees/{id}/documents` → `ApiResponse<List<DocumentResponse>>`.

### 2.7 Bank account
`BankAccountRequest`: `{ bankId*, accountNumber?, iban?, accountHolder?, isDefault?,
validFrom*, validTo? }`.
`BankAccountResponse`: `{ id, bankId, bankCode, bankName, accountNumber, iban,
accountHolder, isDefault, validFrom, validTo }`.
Setting `isDefault=true` unsets the employee's other defaults.

### 2.8 Dependent
`DependentRequest`: `{ firstName*, lastName*, cin?, birthDate?, relationshipId*, taxDeductible? }`.
`DependentResponse`: `{ id, firstName, lastName, cin, birthDate, relationshipId,
relationshipCode, taxDeductible, createdAt }`.

### 2.9 Emergency contact
`EmergencyContactRequest`: `{ fullName*, relationship?, phone*, address? }`.
`EmergencyContactResponse`: `{ id, fullName, relationship, phone, address }`.

### 2.10 Tax profile
`TaxProfileRequest`: `{ taxSituationId*, spouseIsWorking?, numberOfChildren?,
numberOfDisabledChildren?, validFrom*, validTo? }`.
`TaxProfileResponse`: `{ id, taxSituationId, taxSituationCode, spouseIsWorking,
numberOfChildren, numberOfDisabledChildren, validFrom, validTo }`.
Creating a new profile closes the current one (`valid_to = new.valid_from − 1 day`).
**IRPP family coverage (v0.2, verified):** `tax_situations` seeds `CELIBATAIRE` (single),
`MARIE` (married), `CHEF_DE_FAMILLE` (head of family); children, disabled children and
spouse-working-status ride on the profile columns; `resolveTaxProfile` (payroll) feeds all
four IRPP inputs and defaults to `CELIBATAIRE` when absent. Lookup:
`GET /lookups/tax-situations` → `[ { code:"CELIBATAIRE", label:"Single" }, ... ]`.

### 2.11 Assignment history (read-only)
`AssignmentResponse`: `{ id, departmentId, departmentName, positionId, positionName,
locationId, locationName, validFrom, validTo, createdAt }`.
`GET /employees/{id}/assignments` → `ApiResponse<List<AssignmentResponse>>`
(`valid_from DESC`).

### 2.12 Contract
`ContractRequest`:
```json
{
  "contractTypeCode": "CDI",
  "statusCode": "ACTIVE",
  "startDate": "2026-01-01",
  "endDate": null,
  "probationEndDate": null,
  "locationId": 1,
  "workingHoursPerDay": 8.00,
  "workingDaysPerWeek": 5,
  "noticePeriodDays": 30,
  "attachmentPath": null
}
```
`ContractResponse`:
```json
{
  "id": 1, "employeeId": 1, "employeeName": "Ahmed Ben Salah",
  "contractTypeCode": "CDI", "contractType": "CDI",
  "statusCode": "ACTIVE", "status": "Active",
  "startDate": "2026-01-01", "endDate": null, "probationEndDate": null,
  "locationId": 1, "locationName": "Siège social",
  "workingHoursPerDay": 8.00, "workingDaysPerWeek": 5,
  "noticePeriodDays": 30, "attachmentPath": null,
  "baseSalary": 1500.00, "createdAt": "...", "updatedAt": "..."
}
```
- `POST /employees/{id}/contracts` → 201. Single-ACTIVE rule: an existing ACTIVE contract
  overlapping the new one is auto-closed (`end_date = new.startDate − 1 day`,
  `status = EXPIRED`); `new.startDate` before an existing ACTIVE start → 409.
- `PUT /contracts/{id}` re-runs the overlap check on date/status changes.
- `DELETE /contracts/{id}` → 200 if no `salary_components`/`payroll_items` reference, else 409.
- `GET /employees/{id}/contracts` → `ApiResponse<List<ContractResponse>>`
  (`start_date DESC`).

### 2.13 Salary component
`SalaryComponentRequest`:
```json
{
  "componentTypeCode": "PRIME_TRANSPORT",
  "label": "Prime transport",
  "amount": 50.00,
  "isPercentage": false,
  "percentageValue": null,
  "startDate": "2026-01-01",
  "endDate": null,
  "isActive": true
}
```
`SalaryComponentResponse`:
```json
{
  "id": 1, "componentTypeCode": "PRIME_TRANSPORT", "componentType": "Prime transport",
  "category": "BONUS", "label": "Prime transport", "amount": 50.00,
  "isPercentage": false, "percentageValue": null,
  "startDate": "2026-01-01", "endDate": null, "isActive": true
}
```
- `POST /contracts/{id}/components` → 201. BASE replacement rule: a new
  `BASE_SALARY`/`BASE` component overlapping the current BASE closes the old one
  (`end_date = new.startDate − 1 day`) and writes `salary_history`.
- `PUT /contracts/{id}/components/{cid}`: amount change on a BASE component →
  `salary_history` (old→new, `change_date = today`, `changed_by = actor`).
- `DELETE` → 200 if no `salary_history`/payroll reference, else 409.
- `GET /contracts/{id}/components` → `ApiResponse<List<SalaryComponentResponse>>`
  (`start_date DESC`).

### 2.14 Salary history (read-only)
`SalaryHistoryResponse`:
```json
{ "id": 1, "employeeId": 1, "contractId": 1, "oldAmount": 1300.00, "newAmount": 1500.00,
  "changeDate": "2026-06-01", "reason": "Promotion", "changedBy": 5, "createdAt": "..." }
```
`GET /employees/{id}/salary-history` → `ApiResponse<List<SalaryHistoryResponse>>`
(`change_date DESC`).

### 2.15 Work schedule (+ lines)
`WorkScheduleRequest`:
```json
{
  "code": "STD",
  "name": "Standard",
  "isDefault": true,
  "isActive": true,
  "lines": [
    { "weekday": 1, "isWorkday": true, "startTime": "08:00", "endTime": "17:00", "breakMinutes": 60 },
    { "weekday": 2, "isWorkday": true, "startTime": "08:00", "endTime": "17:00", "breakMinutes": 60 },
    { "weekday": 3, "isWorkday": true, "startTime": "08:00", "endTime": "17:00", "breakMinutes": 60 },
    { "weekday": 4, "isWorkday": true, "startTime": "08:00", "endTime": "17:00", "breakMinutes": 60 },
    { "weekday": 5, "isWorkday": true, "startTime": "08:00", "endTime": "17:00", "breakMinutes": 60 },
    { "weekday": 6, "isWorkday": false, "startTime": null, "endTime": null, "breakMinutes": 0 },
    { "weekday": 7, "isWorkday": false, "startTime": null, "endTime": null, "breakMinutes": 0 }
  ]
}
```
`WorkScheduleResponse`:
```json
{
  "id": 1, "code": "STD", "name": "Standard", "isDefault": true, "isActive": true,
  "lineCount": 7, "lines": [ { "weekday": 1, "isWorkday": true, "startTime": "08:00",
  "endTime": "17:00", "breakMinutes": 60 }, ... ]
}
```
- `POST /schedules` → 201; duplicate `code` per company → 409; `is_default=true` unsets
  others; `PUT /schedules/{id}` replaces `lines` wholesale; `DELETE` → 409 if referenced
  by `employee_schedules`/`attendance_summary`.
- Time format `HH:mm` (`LocalTime`); night shift `endTime < startTime` allowed.
- `GET /schedules` → `ApiResponse<List<WorkScheduleResponse>>` (`code ASC`).

### 2.16 Employee schedule assignment
`ScheduleAssignmentRequest`: `{ scheduleId*, validFrom*, validTo? }`.
`ScheduleAssignmentResponse`: `{ id, scheduleId, scheduleCode, scheduleName, validFrom,
validTo, createdAt }`.
- `POST /employees/{id}/schedules` → 201 (overlap guard → 409); `validFrom ≥ hiring_date`.
- `PUT /employees/{id}/schedules/{asgId}` → 200 (overlap re-check, excluding self).
- `DELETE /employees/{id}/schedules/{asgId}` → 200 (physical delete).
- `GET /employees/{id}/schedules` → `ApiResponse<List<ScheduleAssignmentResponse>>`
  (`valid_from DESC`).

---

## 3. Lookup endpoints (read-only, for dropdowns)

Additive to the endpoint map; all company-scoped, no new permissions (reuse module reads):

| Method | Path | Auth | Response |
|--------|------|------|----------|
| GET | `/departments/lookup` | `department.read` | `ApiResponse<List<{id,name}>>` |
| GET | `/positions/lookup` | `position.read` | `ApiResponse<List<{id,name}>>` |
| GET | `/locations/lookup` | `location.read` | `ApiResponse<List<{id,name}>>` |
| GET | `/schedules/lookup` | `schedule.read` | `ApiResponse<List<{id,code,name}>>` |
| GET | `/lookups/employee-statuses` | `employee.read` | codes+labels |
| GET | `/lookups/contract-types` | `contract.read` | codes+labels |
| GET | `/lookups/contract-statuses` | `contract.read` | codes+labels |
| GET | `/lookups/genders` | `employee.read` | codes+labels |
| GET | `/lookups/marital-statuses` | `employee.read` | codes+labels |
| GET | `/lookups/banks` | `employee.read` | codes+names |
| GET | `/lookups/document-types` | `document.read` | codes+labels |
| GET | `/lookups/dependent-relationships` | `employee.read` | codes+labels |
| GET | `/lookups/tax-situations` | `employee.read` | codes+labels |
| GET | `/lookups/salary-component-types` | `contract.read` | codes+labels+category |

---

## 4. Integration guarantees (what Module 6 must not break)

1. **Payroll (`resolveContext`):** contract `status.code = 'ACTIVE'`, overlapping the
   period, with an active `BASE_SALARY` component → the employee is payrolled. The
   single-ACTIVE rule and BASE replacement rule keep this deterministic. **Termination
   exclusion (v0.2):** `resolveContext` also skips the employee when `exit_date` is strictly
   before the period start — a termination mid-period keeps the final partial month payable
   (capped at `exit_date`), and from the next period the employee is excluded regardless of
   contract status.
2. **Approval chains (leave/adjustments):** resolved from `employees.department_id` →
   `departments.manager_employee_id`. The org CRUD keeps these columns consistent.
3. **Attendance engine:** schedule bounds from `employee_schedules` + `work_schedules`
   (fallback default). Schedule CRUD + assignment CRUD feed these tables directly; frozen
   payroll snapshots are never rewritten.
4. **Tenant:** every endpoint resolves the company from the authenticated user; `admin`
   (no `employee_id`) → default company (`companies.code='DEFAULT'`). Non-admin users
   without an employee row keep 403.
5. **rfid_uid / matricule:** global (rfid) and per-company (matricule) uniqueness enforced
   at the service layer; conflicts → 409 before any DB constraint trips.

## 5. Verification plan (Module 6)

- **Two-phase implementation (v0.2):** Phase A = Company + Organization + Employee +
  Contract + Salary + Schedule (+ `CurrentUserService` fallback + payroll exclusion +
  V16 department-uniqueness migration); Phase B = Documents + Bank accounts + Dependents +
  Emergency contacts + Tax profiles. Each phase ends with `mvn clean verify` green.
- `mvn clean verify` — full build, all suites green (existing 183 + new module-6 tests).
- Unit tests: organization service, employee service (+ flat translation), contract service
  (single-ACTIVE, BASE replacement, salary_history), schedule service (default uniqueness,
  overlap guard).
- Live E2E probe against MySQL (pattern of modules 3–5): create org → create employee via
  flat payload → assert auto contract/salary/schedule → child records → salary raise →
  salary_history row → single-ACTIVE enforcement → soft-delete → 409 cases (rfid dup,
  matricule dup, delete-referenced, overlap) → admin fallback → tenant 404/403.
- DB integrity: no orphans (all FKs valid), balances/assignments consistent.
- Cleanup, documentation update (PROJECT_MAP + VERSION_NOTES), final verification report.
