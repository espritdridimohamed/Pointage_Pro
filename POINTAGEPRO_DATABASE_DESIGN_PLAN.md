# PointagePro - Database Design Plan

Status: **DRAFT - AWAITING APPROVAL** (no code generated yet)
Basis: `POINTAGEPRO_MASTER_SPECIFICATION.md` + validated PLAN v2 payroll math
Scope: MySQL 8+, Flyway, Spring Boot 3.x / JPA / Hibernate

---

## Table of contents

1. [Architecture analysis](#1-architecture-analysis)
2. [Missing modules](#2-missing-modules)
3. [Database concept problems found](#3-database-concept-problems-found)
4. [Proposed improvements](#4-proposed-improvements)
5. [Design conventions](#5-design-conventions)
6. [Complete table list](#6-complete-table-list)
7. [Detailed schema (DDL)](#7-detailed-schema-ddl)
8. [Relationships summary](#8-relationships-summary)
9. [Index strategy](#9-index-strategy)
10. [Spec rule enforcement map](#10-spec-rule-enforcement-map)
11. [ER diagram](#11-er-diagram)
12. [Open decisions to confirm](#12-open-decisions-to-confirm)

---

## 1. Architecture analysis

### 1.1 Specified architecture (section 6)

13 modules: `auth, company, organization, employee, contract, attendance, leave, payroll, legal, reports, audit, terminal, shared`.
Each module: `controller / service / repository / entity / dto / mapper / exception`.

### 1.2 Current backend (10 packages)

`auth, attendance, employee, esp32, leave, payroll, reports, settings, dashboard, notification, cleanup, config, security, shared`.

### 1.3 Module comparison

| Spec module | Current state | Verdict |
|---|---|---|
| auth | users, sessions, login history, 2FA, password reset | OK, keep & normalize |
| company | single-row `company_settings` (key/value) | **Weak** - needs real `companies` |
| organization | departments/positions as free strings on `employees` | **Missing** |
| employee | monolithic `employees` (salary, primes, schedule, leave limits embedded) | **Wrong shape** - split identity/history |
| contract | nothing | **Missing** |
| attendance | single `attendance` table mixing raw + computed | **Wrong shape** - split raw/calculated |
| leave | leave_requests + allocations | Partial - needs balances/logs/lookup |
| payroll | payrolls + payroll_items | Partial - needs statuses + snapshot lines |
| legal | hardcoded rates + key/value `tax_settings` | **Missing** - needs versioned tables |
| reports | read-only module | OK (no tables needed) |
| audit | nothing | **Missing** |
| terminal | terminal_status + scan_events | Partial - needs real `terminals` |
| shared | exists | OK |

---

## 2. Missing modules

1. **organization** - no `departments`, `positions`, `locations` tables (free-text columns today).
2. **contract** - no contract or salary-history model at all.
3. **legal** - no versioned legal-rate tables (`tax_brackets`, `cnss_rates`, `css_rates`, `smig_values`, `family_allowances`).
4. **audit** - no `audit_logs` table despite the spec explicitly requiring audit logging.
5. **company** - no `companies` entity.
6. **terminal** - no first-class `terminals` table (only heartbeat status).
7. **Lookup/reference tables** - zero exist today; every status is a free `VARCHAR`.

---

## 3. Database concept problems found

1. **Violates 3.1 (no ENUM / free strings).** All statuses are raw `VARCHAR`: `employees.status='ACTIVE'`, `attendance.status='PRESENT'`, `leave_requests.status='PENDING'`, `payrolls.status='DRAFT'`. No `*_statuses` lookup tables exist.

2. **Violates 3.2 (history preserved).** Salary, primes, schedule, leave limits are **columns on `employees`** (`base_salary`, `prime_*` from V16, schedule/leave fields from V17, schedule versioning V28). Updating them overwrites history. Contracts are not modeled at all.

3. **Violates 3.4 (raw vs calculated).** One `attendance` table stores both raw times and computed status. `scan_events` (V21) is raw but disconnected from the computed rows.

4. **Violates 3.3 (payroll snapshot).** `payroll_items` snapshots fixed columns, but bonuses/deductions are denormalized into fixed columns (`prime_transport`, `prime_performance`, `prime_other`, V18/V22 patches) — not a true line-item snapshot. Payroll `status` is a free string.

5. **Legal rates hardcoded / key-value.** `company_settings` holds `cnss_rate`, `ir_tranche1..5`, etc. as key/value rows (V13). Rates can't be versioned per year (2026 vs 2027 IRPP/CSS changes), and the old 4-bracket IR table is factually outdated vs the validated 8-tranche scheme.

6. **Single-company assumption.** `company_settings` is a single row (V14/V15), no `companies` table, yet spec lists "companies".

7. **No uniqueness/validation.** `matricule` is unique, but `rfid_uid` nullable-unique; no `UNIQUE(company_id, matricule)`; no check constraints on money/minutes.

8. **Legacy/mixed concerns on `employees`:** `department`, `position`, `contract_type` (from V9 seed) are free-text — not FK-normalized.

---

## 4. Proposed improvements

1. **Lookup tables for every status/type** (employee_statuses, contract_types, leave_types, payroll_statuses, document_types, attendance_statuses, ...) referenced by `*_id` FKs. Seeded by Flyway, no ENUMs.
2. **Split `employees` into identity + history:**
   - `employees` = identity + current org placement only.
   - `employee_contracts` = contracts (CDI/CDD/... with dates).
   - `salary_components` = base salary, primes, deductions as dated line items (history-safe).
   - `employee_schedules` = dated schedule assignments.
   - `employee_bank_accounts` = dated bank accounts.
   - `salary_history` = explicit before/after audit trail of gross changes.
3. **Attendance split (3.4):** `attendance_events` (immutable raw scans) → engine → `attendance_summary` (one row per employee per day, all minutes, netting flag OFF by default). Corrections never touch raw rows; they go to `attendance_adjustments`.
4. **Payroll snapshot (3.3):** `payroll_items` (fixed snapshot header) + `payroll_item_components` (line-level frozen bonuses/deductions/contributions) + `payroll_attendance_snapshots` (frozen per-day attendance facts used by the run, written BEFORE any monetary computation — payroll reads only this table, never the mutable `attendance_summary`). A PAID payroll is read-only; any correction produces a new/reversal component, never an edit.
5. **Versioned legal tables (3.2):** `tax_brackets`, `cnss_rates`, `css_rates`, `smig_values`, `family_allowances` keyed by `year`. 2026 values seeded; 2027 CSS 1% is just a new row.
6. **Multi-company ready:** `companies` table; every business table carries `company_id`. Current app seeds one default company; backend fills `company_id` from the authenticated user's context — frontend API contract unchanged.
7. **Audit module:** `audit_logs` (JSON before/after) + `audit_actions` lookup, written for entity changes, login, payroll runs/approvals/payments.
8. **Consistent conventions:** `BIGINT AUTO_INCREMENT` PKs, `DECIMAL(10,2)` money, `DECIMAL(5,2)` percentages, `INT` minutes, `DATE`/`DATETIME`, `TINYINT(1)` booleans, `created_at/updated_at` everywhere, FK + unique + query indexes on every table.

---

## 5. Design conventions

- **PK:** `id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY` (matches existing schema).
- **Money:** `DECIMAL(10,2)` amounts, `DECIMAL(12,2)` payroll totals.
- **Rates/percentages:** `DECIMAL(5,2)`.
- **Minutes:** `INT`. **Hours:** `DECIMAL(5,2)`. **Days:** `DECIMAL(5,2)`.
- **Booleans:** `TINYINT(1)`.
- **Integers (year, month, weekday, counts):** `INT` (maps cleanly to Java `Integer` under Hibernate `validate` — avoids TINYINT/SMALLINT type-mismatch).
- **Dates:** `DATE`; **timestamps:** `DATETIME` with `DEFAULT CURRENT_TIMESTAMP` (MySQL 8 uses `DATETIME` defaults fine).
- **FK names:** `fk_<child>_<parent>`; **index names:** `idx_<table>_<cols>`; **unique:** `uk_<table>_<cols>`.
- **FK strategy:** `ON DELETE RESTRICT` on business tables (never cascade-delete history); `ON DELETE CASCADE` only on pure join tables and child-only snapshots.
- **History rule:** tables with validity windows use `valid_from` + nullable `valid_to` (current row = `valid_to IS NULL`). Never UPDATE a history row.
- **Raw data:** `attendance_events` and payroll snapshots are immutable once written.
- Every table has `created_at`, most have `updated_at`.
- **Company consistency (service layer):** every business row carrying both `employee_id` and `company_id` (contracts, schedules, attendance, etc.) must have `company_id` **derived from the employee's company at creation** — never taken from client input. Service layer rejects any mismatch (`Employee.company_id != Contract.company_id`). Enforced in Phase 6 services.

---

## 6. Complete table list (74 tables)

**Auth (9):** `users`, `user_statuses`, `roles`, `permissions`, `user_roles`, `role_permissions`, `user_sessions`, `login_history`, `password_reset_tokens`

**Company / Organization (6):** `companies`, `company_statuses`, `departments`, `positions`, `locations`, `company_settings`

**Employee (14):** `employees`, `employee_statuses`, `genders`, `marital_statuses`, `employee_documents`, `document_types`, `employee_dependents`, `dependent_relationships`, `employee_bank_accounts`, `banks`, `employee_emergency_contacts`, `tax_situations`, `employee_tax_profiles`, `employee_assignments`

**Contract (6):** `employee_contracts`, `contract_types`, `contract_statuses`, `salary_components`, `salary_component_types`, `salary_history`

**Attendance (13):** `attendance_events`, `event_types`, `attendance_summary`, `day_types`, `attendance_statuses`, `attendance_adjustments`, `adjustment_types`, `adjustment_statuses`, `work_schedules`, `work_schedule_lines`, `employee_schedules`, `holidays`, `holiday_types`

**Leave (5):** `leave_types`, `leave_balances`, `leave_requests`, `leave_request_statuses`, `leave_balance_logs`

**Payroll (6):** `payrolls`, `payroll_statuses`, `payroll_items`, `payroll_item_components`, `payslips`, `payroll_attendance_snapshots`

**Legal (5):** `tax_brackets`, `cnss_rates`, `css_rates`, `smig_values`, `family_allowances`

**Audit (2):** `audit_logs`, `audit_actions`

**Terminal (4):** `terminals`, `terminal_statuses`, `terminal_logs`, `terminal_firmware_versions`

**Notification (2):** `notifications`, `notification_preferences`

**Workflow (2):** `approvals`, `approval_statuses`

---

## 7. Detailed schema (DDL)

### 7.1 Auth module

```sql
CREATE TABLE user_statuses (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(20) NOT NULL UNIQUE,
    label VARCHAR(50) NOT NULL,
    is_active TINYINT(1) NOT NULL DEFAULT 1,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE users (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    email VARCHAR(100) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    full_name VARCHAR(100),
    phone VARCHAR(20),
    employee_id BIGINT NULL,
    status_id BIGINT NOT NULL DEFAULT 1,
    failed_attempts INT NOT NULL DEFAULT 0,
    account_locked TINYINT(1) NOT NULL DEFAULT 0,
    two_factor_enabled TINYINT(1) NOT NULL DEFAULT 0,
    two_factor_secret VARCHAR(255),
    last_login_at DATETIME,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_users_status FOREIGN KEY (status_id) REFERENCES user_statuses(id) ON DELETE RESTRICT,
    CONSTRAINT fk_users_employee FOREIGN KEY (employee_id) REFERENCES employees(id) ON DELETE RESTRICT
);
```

> `employees` is created later; the FK above is shown for completeness (ordering handled in migration).

```sql
CREATE TABLE roles (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(30) NOT NULL UNIQUE,
    name VARCHAR(50) NOT NULL,
    description VARCHAR(255)
);

CREATE TABLE permissions (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(50) NOT NULL UNIQUE,
    name VARCHAR(80) NOT NULL,
    description VARCHAR(255)
);

CREATE TABLE user_roles (
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    PRIMARY KEY (user_id, role_id),
    CONSTRAINT fk_user_roles_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_user_roles_role FOREIGN KEY (role_id) REFERENCES roles(id) ON DELETE CASCADE
);

CREATE TABLE role_permissions (
    role_id BIGINT NOT NULL,
    permission_id BIGINT NOT NULL,
    PRIMARY KEY (role_id, permission_id),
    CONSTRAINT fk_role_perm_role FOREIGN KEY (role_id) REFERENCES roles(id) ON DELETE CASCADE,
    CONSTRAINT fk_role_perm_permission FOREIGN KEY (permission_id) REFERENCES permissions(id) ON DELETE CASCADE
);

CREATE TABLE user_sessions (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    token_hash VARCHAR(255) NOT NULL UNIQUE,
    ip_address VARCHAR(45),
    user_agent VARCHAR(255),
    expires_at DATETIME NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_sessions_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_sessions_user (user_id),
    INDEX idx_sessions_expires (expires_at)
);

CREATE TABLE login_history (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NULL,
    username VARCHAR(50),
    success TINYINT(1) NOT NULL DEFAULT 0,
    ip_address VARCHAR(45),
    user_agent VARCHAR(255),
    failure_reason VARCHAR(100),
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_login_history_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE SET NULL,
    INDEX idx_login_history_user (user_id),
    INDEX idx_login_history_created (created_at)
);

CREATE TABLE password_reset_tokens (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    token_hash VARCHAR(255) NOT NULL UNIQUE,
    expires_at DATETIME NOT NULL,
    used_at DATETIME,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_reset_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_reset_user (user_id)
);
```

### 7.2 Company / Organization module

```sql
CREATE TABLE company_statuses (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(20) NOT NULL UNIQUE,
    label VARCHAR(50) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE companies (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(20) NOT NULL UNIQUE,
    name VARCHAR(100) NOT NULL,
    legal_name VARCHAR(150),
    tax_id VARCHAR(50),
    cnss_number VARCHAR(50),
    address VARCHAR(255),
    city VARCHAR(50),
    phone VARCHAR(20),
    email VARCHAR(100),
    website VARCHAR(100),
    currency VARCHAR(10) NOT NULL DEFAULT 'TND',
    logo_path VARCHAR(255),
    status_id BIGINT NOT NULL DEFAULT 1,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_companies_status FOREIGN KEY (status_id) REFERENCES company_statuses(id) ON DELETE RESTRICT
);

CREATE TABLE departments (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    company_id BIGINT NOT NULL,
    code VARCHAR(20),
    name VARCHAR(100) NOT NULL,
    manager_employee_id BIGINT NULL,
    valid_from DATE NOT NULL,
    valid_to DATE NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_departments_company FOREIGN KEY (company_id) REFERENCES companies(id) ON DELETE CASCADE,
    INDEX idx_departments_company (company_id),
    UNIQUE KEY uk_departments_company_name (company_id, name)
);

CREATE TABLE positions (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    company_id BIGINT NOT NULL,
    code VARCHAR(20),
    name VARCHAR(100) NOT NULL,
    department_id BIGINT NULL,
    valid_from DATE NOT NULL,
    valid_to DATE NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_positions_company FOREIGN KEY (company_id) REFERENCES companies(id) ON DELETE CASCADE,
    CONSTRAINT fk_positions_department FOREIGN KEY (department_id) REFERENCES departments(id) ON DELETE RESTRICT,
    INDEX idx_positions_company (company_id),
    INDEX idx_positions_department (department_id)
);

CREATE TABLE locations (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    company_id BIGINT NOT NULL,
    code VARCHAR(20),
    name VARCHAR(100) NOT NULL,
    address VARCHAR(255),
    is_active TINYINT(1) NOT NULL DEFAULT 1,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_locations_company FOREIGN KEY (company_id) REFERENCES companies(id) ON DELETE CASCADE,
    INDEX idx_locations_company (company_id)
);

CREATE TABLE company_settings (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    company_id BIGINT NOT NULL UNIQUE,
    fiscal_year_start_month INT NOT NULL DEFAULT 1,
    weekly_working_hours DECIMAL(5,2) NOT NULL DEFAULT 40.00,
    monthly_working_hours DECIMAL(5,2) NOT NULL DEFAULT 151.67,
    overtime_enabled TINYINT(1) NOT NULL DEFAULT 1,
    overtime_rate_multiplier DECIMAL(5,2) NOT NULL DEFAULT 1.25,
    hours_netting_enabled TINYINT(1) NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_settings_company FOREIGN KEY (company_id) REFERENCES companies(id) ON DELETE CASCADE
);
```

### 7.3 Employee module

```sql
CREATE TABLE employee_statuses (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(20) NOT NULL UNIQUE,
    label VARCHAR(50) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE genders (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(10) NOT NULL UNIQUE,
    label VARCHAR(30) NOT NULL
);

CREATE TABLE marital_statuses (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(20) NOT NULL UNIQUE,
    label VARCHAR(30) NOT NULL
);

CREATE TABLE employees (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    company_id BIGINT NOT NULL,
    matricule VARCHAR(20) NOT NULL,
    first_name VARCHAR(50) NOT NULL,
    last_name VARCHAR(50) NOT NULL,
    cin VARCHAR(20),
    passport_number VARCHAR(20),
    birth_date DATE,
    gender_id BIGINT NULL,
    marital_status_id BIGINT NULL,
    nationality VARCHAR(50) NOT NULL DEFAULT 'Tunisienne',
    email VARCHAR(100),
    phone VARCHAR(20),
    address VARCHAR(255),
    city VARCHAR(50),
    rfid_uid VARCHAR(30),
    photo_path VARCHAR(255),
    status_id BIGINT NOT NULL DEFAULT 1,
    department_id BIGINT NULL,
    position_id BIGINT NULL,
    location_id BIGINT NULL,
    hiring_date DATE NOT NULL,
    exit_date DATE,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_employees_company FOREIGN KEY (company_id) REFERENCES companies(id) ON DELETE RESTRICT,
    CONSTRAINT fk_employees_status FOREIGN KEY (status_id) REFERENCES employee_statuses(id) ON DELETE RESTRICT,
    CONSTRAINT fk_employees_gender FOREIGN KEY (gender_id) REFERENCES genders(id) ON DELETE RESTRICT,
    CONSTRAINT fk_employees_marital FOREIGN KEY (marital_status_id) REFERENCES marital_statuses(id) ON DELETE RESTRICT,
    CONSTRAINT fk_employees_department FOREIGN KEY (department_id) REFERENCES departments(id) ON DELETE RESTRICT,
    CONSTRAINT fk_employees_position FOREIGN KEY (position_id) REFERENCES positions(id) ON DELETE RESTRICT,
    CONSTRAINT fk_employees_location FOREIGN KEY (location_id) REFERENCES locations(id) ON DELETE RESTRICT,
    UNIQUE KEY uk_employees_company_matricule (company_id, matricule),
    UNIQUE KEY uk_employees_rfid (rfid_uid),
    INDEX idx_employees_department (department_id),
    INDEX idx_employees_position (position_id),
    INDEX idx_employees_status (status_id)
);

CREATE TABLE document_types (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(20) NOT NULL UNIQUE,
    label VARCHAR(50) NOT NULL
);

CREATE TABLE employee_documents (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    employee_id BIGINT NOT NULL,
    document_type_id BIGINT NOT NULL,
    document_number VARCHAR(50),
    file_path VARCHAR(255) NOT NULL,
    issue_date DATE,
    expiry_date DATE,
    notes VARCHAR(255),
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_docs_employee FOREIGN KEY (employee_id) REFERENCES employees(id) ON DELETE CASCADE,
    CONSTRAINT fk_docs_type FOREIGN KEY (document_type_id) REFERENCES document_types(id) ON DELETE RESTRICT,
    INDEX idx_docs_employee (employee_id),
    INDEX idx_docs_expiry (expiry_date)
);

CREATE TABLE dependent_relationships (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(20) NOT NULL UNIQUE,
    label VARCHAR(30) NOT NULL
);

CREATE TABLE employee_dependents (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    employee_id BIGINT NOT NULL,
    first_name VARCHAR(50) NOT NULL,
    last_name VARCHAR(50) NOT NULL,
    cin VARCHAR(20),
    birth_date DATE,
    relationship_id BIGINT NOT NULL,
    tax_deductible TINYINT(1) NOT NULL DEFAULT 1,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_dependents_employee FOREIGN KEY (employee_id) REFERENCES employees(id) ON DELETE CASCADE,
    CONSTRAINT fk_dependents_relationship FOREIGN KEY (relationship_id) REFERENCES dependent_relationships(id) ON DELETE RESTRICT,
    INDEX idx_dependents_employee (employee_id)
);

CREATE TABLE banks (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(20) NOT NULL UNIQUE,
    name VARCHAR(100) NOT NULL
);

CREATE TABLE employee_bank_accounts (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    employee_id BIGINT NOT NULL,
    bank_id BIGINT NOT NULL,
    account_number VARCHAR(50),
    iban VARCHAR(34),
    account_holder VARCHAR(100),
    is_default TINYINT(1) NOT NULL DEFAULT 0,
    valid_from DATE NOT NULL,
    valid_to DATE NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_bank_employee FOREIGN KEY (employee_id) REFERENCES employees(id) ON DELETE RESTRICT,
    CONSTRAINT fk_bank_bank FOREIGN KEY (bank_id) REFERENCES banks(id) ON DELETE RESTRICT,
    INDEX idx_bank_employee (employee_id)
);

CREATE TABLE employee_emergency_contacts (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    employee_id BIGINT NOT NULL,
    full_name VARCHAR(100) NOT NULL,
    relationship VARCHAR(50),
    phone VARCHAR(20) NOT NULL,
    address VARCHAR(255),
    CONSTRAINT fk_emergency_employee FOREIGN KEY (employee_id) REFERENCES employees(id) ON DELETE CASCADE,
    INDEX idx_emergency_employee (employee_id)
);

-- Tax profile (pre-payroll, dated - V3)

CREATE TABLE tax_situations (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(20) NOT NULL UNIQUE,
    label VARCHAR(50) NOT NULL
);

CREATE TABLE employee_tax_profiles (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    employee_id BIGINT NOT NULL,
    tax_situation_id BIGINT NOT NULL,
    spouse_is_working TINYINT(1) NOT NULL DEFAULT 0,
    number_of_children INT NOT NULL DEFAULT 0,
    number_of_disabled_children INT NOT NULL DEFAULT 0,
    valid_from DATE NOT NULL,
    valid_to DATE NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_tax_profile_employee FOREIGN KEY (employee_id) REFERENCES employees(id) ON DELETE CASCADE,
    CONSTRAINT fk_tax_profile_situation FOREIGN KEY (tax_situation_id) REFERENCES tax_situations(id) ON DELETE RESTRICT,
    UNIQUE KEY uk_tax_profile_employee_date (employee_id, valid_from),
    INDEX idx_tax_profile_employee (employee_id)
);
```

> `tax_situations`: `CELIBATAIRE / MARIE / CHEF_DE_FAMILLE`. The tax profile is dated (like salary components) — one current row (`valid_to IS NULL`) per employee plus history. `employee_dependents.tax_deductible` records each person individually; the profile holds the aggregate IRPP inputs (children / disabled children / spouse working).

-- Employee assignments (dated history - V4). employees.department_id/position_id/
-- location_id remain the current snapshot, kept in sync by the service.

CREATE TABLE employee_assignments (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    employee_id BIGINT NOT NULL,
    department_id BIGINT NULL,
    position_id BIGINT NULL,
    location_id BIGINT NULL,
    valid_from DATE NOT NULL,
    valid_to DATE NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_assign_employee FOREIGN KEY (employee_id) REFERENCES employees(id) ON DELETE RESTRICT,
    CONSTRAINT fk_assign_department FOREIGN KEY (department_id) REFERENCES departments(id) ON DELETE RESTRICT,
    CONSTRAINT fk_assign_position FOREIGN KEY (position_id) REFERENCES positions(id) ON DELETE RESTRICT,
    CONSTRAINT fk_assign_location FOREIGN KEY (location_id) REFERENCES locations(id) ON DELETE RESTRICT,
    UNIQUE KEY uk_assign_employee_date (employee_id, valid_from),
    INDEX idx_assign_employee (employee_id),
    INDEX idx_assign_department (department_id),
    INDEX idx_assign_position (position_id)
);

> Assignment change = close current row (`valid_to`), open new row, update `employees.*` — same dated pattern as salary components.

### 7.4 Contract module

```sql
CREATE TABLE contract_types (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(20) NOT NULL UNIQUE,
    label VARCHAR(50) NOT NULL
);

CREATE TABLE contract_statuses (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(20) NOT NULL UNIQUE,
    label VARCHAR(50) NOT NULL
);

CREATE TABLE employee_contracts (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    employee_id BIGINT NOT NULL,
    company_id BIGINT NOT NULL,
    contract_type_id BIGINT NOT NULL,
    status_id BIGINT NOT NULL DEFAULT 1,
    start_date DATE NOT NULL,
    end_date DATE,
    probation_end_date DATE,
    location_id BIGINT NULL,
    working_hours_per_day DECIMAL(4,2) NOT NULL DEFAULT 8.00,
    working_days_per_week INT NOT NULL DEFAULT 5,
    notice_period_days INT,
    attachment_path VARCHAR(255),
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_contracts_employee FOREIGN KEY (employee_id) REFERENCES employees(id) ON DELETE RESTRICT,
    CONSTRAINT fk_contracts_company FOREIGN KEY (company_id) REFERENCES companies(id) ON DELETE RESTRICT,
    CONSTRAINT fk_contracts_type FOREIGN KEY (contract_type_id) REFERENCES contract_types(id) ON DELETE RESTRICT,
    CONSTRAINT fk_contracts_status FOREIGN KEY (status_id) REFERENCES contract_statuses(id) ON DELETE RESTRICT,
    CONSTRAINT fk_contracts_location FOREIGN KEY (location_id) REFERENCES locations(id) ON DELETE RESTRICT,
    INDEX idx_contracts_employee (employee_id),
    INDEX idx_contracts_company (company_id),
    INDEX idx_contracts_status (status_id)
);

CREATE TABLE salary_component_types (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(30) NOT NULL UNIQUE,
    label VARCHAR(100) NOT NULL,
    category VARCHAR(20) NOT NULL DEFAULT 'BONUS',
    is_subject_to_cnss TINYINT(1) NOT NULL DEFAULT 1,
    is_subject_to_irpp TINYINT(1) NOT NULL DEFAULT 1,
    is_subject_to_css TINYINT(1) NOT NULL DEFAULT 0
);

CREATE TABLE salary_components (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    contract_id BIGINT NOT NULL,
    component_type_id BIGINT NOT NULL,
    label VARCHAR(100) NOT NULL,
    amount DECIMAL(10,2),
    is_percentage TINYINT(1) NOT NULL DEFAULT 0,
    percentage_value DECIMAL(5,2),
    start_date DATE NOT NULL,
    end_date DATE,
    is_active TINYINT(1) NOT NULL DEFAULT 1,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_salary_comp_contract FOREIGN KEY (contract_id) REFERENCES employee_contracts(id) ON DELETE RESTRICT,
    CONSTRAINT fk_salary_comp_type FOREIGN KEY (component_type_id) REFERENCES salary_component_types(id) ON DELETE RESTRICT,
    INDEX idx_salary_comp_contract (contract_id),
    INDEX idx_salary_comp_dates (start_date, end_date)
);

CREATE TABLE salary_history (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    employee_id BIGINT NOT NULL,
    contract_id BIGINT NULL,
    old_amount DECIMAL(10,2),
    new_amount DECIMAL(10,2) NOT NULL,
    change_date DATE NOT NULL,
    reason VARCHAR(255),
    changed_by BIGINT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_salhist_employee FOREIGN KEY (employee_id) REFERENCES employees(id) ON DELETE RESTRICT,
    CONSTRAINT fk_salhist_contract FOREIGN KEY (contract_id) REFERENCES employee_contracts(id) ON DELETE SET NULL,
    CONSTRAINT fk_salhist_user FOREIGN KEY (changed_by) REFERENCES users(id) ON DELETE SET NULL,
    INDEX idx_salhist_employee (employee_id)
);
```

> `salary_component_types.category`: `BASE / BONUS / DEDUCTION`. BASE_SALARY is the base-salary component; a salary change = new component row (old row keeps `end_date`), never an UPDATE.

### 7.5 Attendance module

```sql
CREATE TABLE event_types (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(10) NOT NULL UNIQUE,
    label VARCHAR(30) NOT NULL
);

CREATE TABLE terminals (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    company_id BIGINT NOT NULL,
    code VARCHAR(20) NOT NULL UNIQUE,
    serial_number VARCHAR(50) NOT NULL UNIQUE,
    model VARCHAR(50),
    location_id BIGINT NULL,
    status_id BIGINT NOT NULL DEFAULT 1,
    firmware_version VARCHAR(20),
    last_heartbeat_at DATETIME,
    last_ip VARCHAR(45),
    api_key_hash VARCHAR(100) NULL,
    activation_code_hash VARCHAR(100) NULL,
    enrolled_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_terminals_company FOREIGN KEY (company_id) REFERENCES companies(id) ON DELETE RESTRICT,
    CONSTRAINT fk_terminals_location FOREIGN KEY (location_id) REFERENCES locations(id) ON DELETE RESTRICT,
    CONSTRAINT fk_terminals_status FOREIGN KEY (status_id) REFERENCES terminal_statuses(id) ON DELETE RESTRICT,
    INDEX idx_terminals_company (company_id),
    INDEX idx_terminals_status (status_id)
);

CREATE TABLE attendance_events (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    company_id BIGINT NOT NULL,
    employee_id BIGINT NOT NULL,
    terminal_id BIGINT NULL,
    event_type_id BIGINT NOT NULL,
    event_time DATETIME NOT NULL,
    rfid_uid VARCHAR(30),
    source VARCHAR(20) NOT NULL DEFAULT 'TERMINAL',
    external_ref VARCHAR(50),
    time_warning TINYINT(1) NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_events_company FOREIGN KEY (company_id) REFERENCES companies(id) ON DELETE RESTRICT,
    CONSTRAINT fk_events_employee FOREIGN KEY (employee_id) REFERENCES employees(id) ON DELETE RESTRICT,
    CONSTRAINT fk_events_terminal FOREIGN KEY (terminal_id) REFERENCES terminals(id) ON DELETE RESTRICT,
    CONSTRAINT fk_events_type FOREIGN KEY (event_type_id) REFERENCES event_types(id) ON DELETE RESTRICT,
    UNIQUE KEY uk_events_terminal_ref (terminal_id, external_ref),
    INDEX idx_events_employee_time (employee_id, event_time),
    INDEX idx_events_terminal_time (terminal_id, event_time),
    INDEX idx_events_time (event_time)
);

CREATE TABLE day_types (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(20) NOT NULL UNIQUE,
    label VARCHAR(30) NOT NULL
);

CREATE TABLE attendance_statuses (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(20) NOT NULL UNIQUE,
    label VARCHAR(30) NOT NULL
);

CREATE TABLE attendance_summary (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    company_id BIGINT NOT NULL,
    employee_id BIGINT NOT NULL,
    work_date DATE NOT NULL,
    day_type_id BIGINT NULL,
    schedule_id BIGINT NULL,
    first_in TIME,
    last_out TIME,
    worked_minutes INT NOT NULL DEFAULT 0,
    late_minutes INT NOT NULL DEFAULT 0,
    early_exit_minutes INT NOT NULL DEFAULT 0,
    missing_minutes INT NOT NULL DEFAULT 0,
    overtime_minutes INT NOT NULL DEFAULT 0,
    netted_work_minutes INT NOT NULL DEFAULT 0,
    is_weekend TINYINT(1) NOT NULL DEFAULT 0,
    is_holiday TINYINT(1) NOT NULL DEFAULT 0,
    status_id BIGINT NULL,
    adjustment_minutes INT NOT NULL DEFAULT 0,
    computed_at DATETIME,
    recompute_reason VARCHAR(255),
    computed_by_user_id BIGINT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_summary_company FOREIGN KEY (company_id) REFERENCES companies(id) ON DELETE RESTRICT,
    CONSTRAINT fk_summary_employee FOREIGN KEY (employee_id) REFERENCES employees(id) ON DELETE RESTRICT,
    CONSTRAINT fk_summary_day_type FOREIGN KEY (day_type_id) REFERENCES day_types(id) ON DELETE RESTRICT,
    CONSTRAINT fk_summary_schedule FOREIGN KEY (schedule_id) REFERENCES work_schedules(id) ON DELETE RESTRICT,
    CONSTRAINT fk_summary_status FOREIGN KEY (status_id) REFERENCES attendance_statuses(id) ON DELETE RESTRICT,
    CONSTRAINT fk_summary_computed_by FOREIGN KEY (computed_by_user_id) REFERENCES users(id) ON DELETE SET NULL,
    UNIQUE KEY uk_summary_employee_date (employee_id, work_date),
    INDEX idx_summary_company_date (company_id, work_date),
    INDEX idx_summary_status (status_id)
);

CREATE TABLE adjustment_types (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(30) NOT NULL UNIQUE,
    label VARCHAR(60) NOT NULL
);

CREATE TABLE adjustment_statuses (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(20) NOT NULL UNIQUE,
    label VARCHAR(30) NOT NULL
);

CREATE TABLE attendance_adjustments (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    company_id BIGINT NOT NULL,
    employee_id BIGINT NOT NULL,
    summary_id BIGINT NULL,
    adjustment_type_id BIGINT NOT NULL,
    minutes INT NOT NULL,
    reason VARCHAR(255) NOT NULL,
    status_id BIGINT NOT NULL DEFAULT 1,
    approved_by BIGINT NULL,
    approved_at DATETIME,
    created_by BIGINT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_adj_company FOREIGN KEY (company_id) REFERENCES companies(id) ON DELETE RESTRICT,
    CONSTRAINT fk_adj_employee FOREIGN KEY (employee_id) REFERENCES employees(id) ON DELETE RESTRICT,
    CONSTRAINT fk_adj_summary FOREIGN KEY (summary_id) REFERENCES attendance_summary(id) ON DELETE SET NULL,
    CONSTRAINT fk_adj_type FOREIGN KEY (adjustment_type_id) REFERENCES adjustment_types(id) ON DELETE RESTRICT,
    CONSTRAINT fk_adj_status FOREIGN KEY (status_id) REFERENCES adjustment_statuses(id) ON DELETE RESTRICT,
    CONSTRAINT fk_adj_approver FOREIGN KEY (approved_by) REFERENCES users(id) ON DELETE SET NULL,
    CONSTRAINT fk_adj_creator FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE RESTRICT,
    INDEX idx_adj_employee (employee_id),
    INDEX idx_adj_summary (summary_id),
    INDEX idx_adj_status (status_id)
);

CREATE TABLE work_schedules (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    company_id BIGINT NOT NULL,
    code VARCHAR(20) NOT NULL,
    name VARCHAR(100) NOT NULL,
    is_default TINYINT(1) NOT NULL DEFAULT 0,
    is_active TINYINT(1) NOT NULL DEFAULT 1,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_schedules_company FOREIGN KEY (company_id) REFERENCES companies(id) ON DELETE CASCADE,
    UNIQUE KEY uk_schedules_company_code (company_id, code)
);

CREATE TABLE work_schedule_lines (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    schedule_id BIGINT NOT NULL,
    weekday INT NOT NULL,
    is_workday TINYINT(1) NOT NULL DEFAULT 1,
    start_time TIME,
    end_time TIME,
    break_minutes INT NOT NULL DEFAULT 0,
    CONSTRAINT fk_schedule_lines_schedule FOREIGN KEY (schedule_id) REFERENCES work_schedules(id) ON DELETE CASCADE,
    UNIQUE KEY uk_schedule_lines (schedule_id, weekday),
    CHECK (weekday BETWEEN 1 AND 7)
);

CREATE TABLE employee_schedules (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    employee_id BIGINT NOT NULL,
    schedule_id BIGINT NOT NULL,
    valid_from DATE NOT NULL,
    valid_to DATE NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_emp_sched_employee FOREIGN KEY (employee_id) REFERENCES employees(id) ON DELETE RESTRICT,
    CONSTRAINT fk_emp_sched_schedule FOREIGN KEY (schedule_id) REFERENCES work_schedules(id) ON DELETE RESTRICT,
    INDEX idx_emp_sched_employee (employee_id),
    INDEX idx_emp_sched_dates (valid_from, valid_to)
);

CREATE TABLE holiday_types (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(20) NOT NULL UNIQUE,
    label VARCHAR(30) NOT NULL
);

CREATE TABLE holidays (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    company_id BIGINT NOT NULL,
    name VARCHAR(100) NOT NULL,
    holiday_date DATE NOT NULL,
    is_recurring TINYINT(1) NOT NULL DEFAULT 0,
    year INT,
    type_id BIGINT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_holidays_company FOREIGN KEY (company_id) REFERENCES companies(id) ON DELETE CASCADE,
    CONSTRAINT fk_holidays_type FOREIGN KEY (type_id) REFERENCES holiday_types(id) ON DELETE RESTRICT,
    UNIQUE KEY uk_holidays_company_date (company_id, holiday_date),
    INDEX idx_holidays_date (holiday_date)
);
```

### 7.6 Leave module

```sql
CREATE TABLE leave_types (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(30) NOT NULL UNIQUE,
    name VARCHAR(100) NOT NULL,
    is_paid TINYINT(1) NOT NULL DEFAULT 1,
    default_days_per_year DECIMAL(5,2),
    is_active TINYINT(1) NOT NULL DEFAULT 1
);

CREATE TABLE leave_request_statuses (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(20) NOT NULL UNIQUE,
    label VARCHAR(30) NOT NULL
);

CREATE TABLE leave_balances (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    employee_id BIGINT NOT NULL,
    leave_type_id BIGINT NOT NULL,
    year INT NOT NULL,
    entitlement_days DECIMAL(5,2) NOT NULL DEFAULT 0,
    taken_days DECIMAL(5,2) NOT NULL DEFAULT 0,
    carried_over_days DECIMAL(5,2) NOT NULL DEFAULT 0,
    adjusted_days DECIMAL(5,2) NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_balance_employee FOREIGN KEY (employee_id) REFERENCES employees(id) ON DELETE RESTRICT,
    CONSTRAINT fk_balance_type FOREIGN KEY (leave_type_id) REFERENCES leave_types(id) ON DELETE RESTRICT,
    UNIQUE KEY uk_balance (employee_id, leave_type_id, year),
    INDEX idx_balance_employee (employee_id)
);

CREATE TABLE leave_requests (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    employee_id BIGINT NOT NULL,
    leave_type_id BIGINT NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    days_requested DECIMAL(5,2) NOT NULL,
    reason VARCHAR(500),
    attachment_path VARCHAR(255),
    status_id BIGINT NOT NULL DEFAULT 1,
    approved_by BIGINT NULL,
    approved_at DATETIME,
    rejected_reason VARCHAR(255),
    created_by BIGINT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_leave_employee FOREIGN KEY (employee_id) REFERENCES employees(id) ON DELETE RESTRICT,
    CONSTRAINT fk_leave_type FOREIGN KEY (leave_type_id) REFERENCES leave_types(id) ON DELETE RESTRICT,
    CONSTRAINT fk_leave_status FOREIGN KEY (status_id) REFERENCES leave_request_statuses(id) ON DELETE RESTRICT,
    CONSTRAINT fk_leave_approver FOREIGN KEY (approved_by) REFERENCES users(id) ON DELETE SET NULL,
    CONSTRAINT fk_leave_creator FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE RESTRICT,
    INDEX idx_leave_employee (employee_id),
    INDEX idx_leave_dates (start_date, end_date),
    INDEX idx_leave_status (status_id)
);

CREATE TABLE leave_balance_logs (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    employee_id BIGINT NOT NULL,
    leave_type_id BIGINT NOT NULL,
    year INT NOT NULL,
    delta_days DECIMAL(5,2) NOT NULL,
    reason VARCHAR(255),
    ref_type VARCHAR(30),
    ref_id BIGINT,
    created_by BIGINT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_bal_log_employee FOREIGN KEY (employee_id) REFERENCES employees(id) ON DELETE RESTRICT,
    CONSTRAINT fk_bal_log_type FOREIGN KEY (leave_type_id) REFERENCES leave_types(id) ON DELETE RESTRICT,
    CONSTRAINT fk_bal_log_creator FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE RESTRICT,
    INDEX idx_bal_log_employee (employee_id)
);
```

### 7.7 Payroll module

```sql
CREATE TABLE payroll_statuses (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(20) NOT NULL UNIQUE,
    label VARCHAR(30) NOT NULL
);

CREATE TABLE payrolls (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    company_id BIGINT NOT NULL,
    period_year INT NOT NULL,
    period_month INT NOT NULL,
    run_date DATE,
    status_id BIGINT NOT NULL DEFAULT 1,
    total_gross DECIMAL(12,2) NOT NULL DEFAULT 0,
    total_cnss DECIMAL(12,2) NOT NULL DEFAULT 0,
    total_irpp DECIMAL(12,2) NOT NULL DEFAULT 0,
    total_css DECIMAL(12,2) NOT NULL DEFAULT 0,
    total_deductions DECIMAL(12,2) NOT NULL DEFAULT 0,
    total_net DECIMAL(12,2) NOT NULL DEFAULT 0,
    employee_count INT NOT NULL DEFAULT 0,
    created_by BIGINT NOT NULL,
    approved_by BIGINT NULL,
    approved_at DATETIME,
    paid_at DATETIME,
    notes VARCHAR(500),
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_payrolls_company FOREIGN KEY (company_id) REFERENCES companies(id) ON DELETE RESTRICT,
    CONSTRAINT fk_payrolls_status FOREIGN KEY (status_id) REFERENCES payroll_statuses(id) ON DELETE RESTRICT,
    CONSTRAINT fk_payrolls_creator FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE RESTRICT,
    CONSTRAINT fk_payrolls_approver FOREIGN KEY (approved_by) REFERENCES users(id) ON DELETE SET NULL,
    UNIQUE KEY uk_payrolls_period (company_id, period_year, period_month),
    INDEX idx_payrolls_status (status_id)
);

CREATE TABLE payroll_items (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    payroll_id BIGINT NOT NULL,
    employee_id BIGINT NOT NULL,
    contract_id BIGINT NULL,
    base_salary DECIMAL(10,2) NOT NULL,
    work_days INT NOT NULL DEFAULT 0,
    work_hours DECIMAL(5,2) NOT NULL DEFAULT 0,
    overtime_minutes INT NOT NULL DEFAULT 0,
    overtime_amount DECIMAL(10,2) NOT NULL DEFAULT 0,
    absence_minutes INT NOT NULL DEFAULT 0,
    absence_deduction DECIMAL(10,2) NOT NULL DEFAULT 0,
    late_minutes INT NOT NULL DEFAULT 0,
    late_deduction DECIMAL(10,2) NOT NULL DEFAULT 0,
    gross_salary DECIMAL(10,2) NOT NULL DEFAULT 0,
    cnss_salarial DECIMAL(10,2) NOT NULL DEFAULT 0,
    cnss_patronal DECIMAL(10,2) NOT NULL DEFAULT 0,
    irpp DECIMAL(10,2) NOT NULL DEFAULT 0,
    css DECIMAL(10,2) NOT NULL DEFAULT 0,
    net_salary DECIMAL(10,2) NOT NULL DEFAULT 0,
    cancelled TINYINT(1) NOT NULL DEFAULT 0,
    paid_at DATETIME,
    bank_transfer_ref VARCHAR(50),
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_pitem_payroll FOREIGN KEY (payroll_id) REFERENCES payrolls(id) ON DELETE CASCADE,
    CONSTRAINT fk_pitem_employee FOREIGN KEY (employee_id) REFERENCES employees(id) ON DELETE RESTRICT,
    CONSTRAINT fk_pitem_contract FOREIGN KEY (contract_id) REFERENCES employee_contracts(id) ON DELETE RESTRICT,
    UNIQUE KEY uk_pitem (payroll_id, employee_id),
    INDEX idx_pitem_employee (employee_id)
);

CREATE TABLE payroll_item_components (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    payroll_item_id BIGINT NOT NULL,
    component_type_id BIGINT NULL,
    label VARCHAR(100) NOT NULL,
    category VARCHAR(20) NOT NULL,
    amount DECIMAL(10,2) NOT NULL,
    is_percentage TINYINT(1) NOT NULL DEFAULT 0,
    percentage_value DECIMAL(5,2),
    sort_order INT NOT NULL DEFAULT 0,
    CONSTRAINT fk_pic_item FOREIGN KEY (payroll_item_id) REFERENCES payroll_items(id) ON DELETE CASCADE,
    CONSTRAINT fk_pic_type FOREIGN KEY (component_type_id) REFERENCES salary_component_types(id) ON DELETE RESTRICT,
    INDEX idx_pic_item (payroll_item_id)
);

CREATE TABLE payslips (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    payroll_item_id BIGINT NOT NULL UNIQUE,
    payslip_number VARCHAR(30),
    pdf_path VARCHAR(255),
    issued_at DATETIME,
    sent_at DATETIME,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_payslips_item FOREIGN KEY (payroll_item_id) REFERENCES payroll_items(id) ON DELETE CASCADE
);

CREATE TABLE payroll_attendance_snapshots (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    payroll_id BIGINT NOT NULL,
    company_id BIGINT NOT NULL,
    employee_id BIGINT NOT NULL,
    work_date DATE NOT NULL,
    day_type_id BIGINT NULL,
    schedule_id BIGINT NULL,
    scheduled_start_time TIME NULL,
    scheduled_end_time TIME NULL,
    scheduled_break_minutes INT NOT NULL DEFAULT 0,
    status_id BIGINT NULL,
    first_in TIME NULL,
    last_out TIME NULL,
    worked_minutes INT NOT NULL DEFAULT 0,
    late_minutes INT NOT NULL DEFAULT 0,
    early_exit_minutes INT NOT NULL DEFAULT 0,
    missing_minutes INT NOT NULL DEFAULT 0,
    overtime_minutes INT NOT NULL DEFAULT 0,
    netted_work_minutes INT NOT NULL DEFAULT 0,
    adjustment_minutes INT NOT NULL DEFAULT 0,
    is_weekend TINYINT(1) NOT NULL DEFAULT 0,
    is_holiday TINYINT(1) NOT NULL DEFAULT 0,
    source_summary_id BIGINT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_pas_payroll FOREIGN KEY (payroll_id) REFERENCES payrolls(id) ON DELETE RESTRICT,
    CONSTRAINT fk_pas_company FOREIGN KEY (company_id) REFERENCES companies(id) ON DELETE RESTRICT,
    CONSTRAINT fk_pas_employee FOREIGN KEY (employee_id) REFERENCES employees(id) ON DELETE RESTRICT,
    CONSTRAINT fk_pas_day_type FOREIGN KEY (day_type_id) REFERENCES day_types(id) ON DELETE RESTRICT,
    CONSTRAINT fk_pas_schedule FOREIGN KEY (schedule_id) REFERENCES work_schedules(id) ON DELETE RESTRICT,
    CONSTRAINT fk_pas_status FOREIGN KEY (status_id) REFERENCES attendance_statuses(id) ON DELETE RESTRICT,
    CONSTRAINT fk_pas_source_summary FOREIGN KEY (source_summary_id) REFERENCES attendance_summary(id) ON DELETE SET NULL,
    UNIQUE KEY uk_pas (payroll_id, employee_id, work_date),
    INDEX idx_pas_payroll (payroll_id),
    INDEX idx_pas_employee_date (employee_id, work_date)
);
```

> **3.3 enforcement:** once `payrolls.status` reaches `PAID`, the service layer refuses all UPDATE/DELETE on `payroll_items`/`payroll_item_components`/`payroll_attendance_snapshots`. Corrections = a new reversal line on the next payroll, never an edit.
> **Freeze-before-compute:** payroll generation first copies the period's `attendance_summary` rows into `payroll_attendance_snapshots` (same transaction), including the resolved schedule values (`scheduled_start_time` / `scheduled_end_time` / `scheduled_break_minutes`) so each run is self-contained and reproducible even after the schedule definition changes. Payroll then computes `payroll_items`/components exclusively from the snapshot. Payroll never reads the mutable `attendance_summary`; later recomputes of summaries never touch a frozen run.

### 7.8 Legal module (Tunisian, versioned by year)

```sql
CREATE TABLE tax_brackets (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    year INT NOT NULL,
    bracket_order INT NOT NULL,
    lower_bound DECIMAL(10,2) NOT NULL,
    upper_bound DECIMAL(12,2) NOT NULL,
    rate_percent DECIMAL(5,2) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_tax_bracket_year_order (year, bracket_order),
    CHECK (upper_bound > lower_bound)
);

CREATE TABLE cnss_rates (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    year INT NOT NULL UNIQUE,
    employee_rate DECIMAL(5,2) NOT NULL,
    employer_rate DECIMAL(5,2) NOT NULL,
    family_allocations_rate DECIMAL(5,2) NOT NULL,
    ceiling_amount DECIMAL(12,2),
    active_from DATE NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE css_rates (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    year INT NOT NULL UNIQUE,
    employee_rate DECIMAL(5,2) NOT NULL,
    employer_rate DECIMAL(5,2) NOT NULL,
    active_from DATE NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE smig_values (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    year INT NOT NULL UNIQUE,
    hourly_rate DECIMAL(6,3),
    monthly_rate DECIMAL(10,3),
    weekly_rate DECIMAL(10,3),
    active_from DATE NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE family_allowances (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    year INT NOT NULL UNIQUE,
    max_children INT NOT NULL DEFAULT 6,
    amount_per_child DECIMAL(6,2) NOT NULL,
    active_from DATE NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);
```

**2026 seed values (validated):**

```sql
-- IRPP 2026 (8 tranches, annual)
INSERT INTO tax_brackets (year, bracket_order, lower_bound, upper_bound, rate_percent) VALUES
(2026, 1, 0, 5000, 0),
(2026, 2, 5000, 20000, 15),
(2026, 3, 20000, 30000, 25),
(2026, 4, 30000, 50000, 30),
(2026, 5, 50000, 60000, 33),
(2026, 6, 60000, 80000, 36),
(2026, 7, 80000, 150000, 38),
(2026, 8, 150000, 999999999, 40);

INSERT INTO cnss_rates (year, employee_rate, employer_rate, family_allocations_rate, ceiling_amount, active_from) VALUES
(2026, 9.68, 16.57, 0.55, NULL, '2026-01-01'); -- salariale 9.68% no ceiling

INSERT INTO css_rates (year, employee_rate, employer_rate, active_from) VALUES
(2026, 0.50, 1.00, '2026-01-01'); -- 2027 row will be 1.00

INSERT INTO smig_values (year, hourly_rate, monthly_rate, weekly_rate, active_from) VALUES
(2026, 3.000, 524.954, 121.144, '2026-01-01');

INSERT INTO family_allowances (year, max_children, amount_per_child, active_from) VALUES
(2026, 6, 0, '2026-01-01'); -- amount filled when regulation confirmed
```

### 7.9 Audit module

```sql
CREATE TABLE audit_actions (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(30) NOT NULL UNIQUE,
    label VARCHAR(60) NOT NULL
);

CREATE TABLE audit_logs (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    company_id BIGINT NULL,
    user_id BIGINT NULL,
    action_id BIGINT NOT NULL,
    entity_type VARCHAR(80) NOT NULL,
    entity_id BIGINT,
    old_value JSON,
    new_value JSON,
    ip_address VARCHAR(45),
    user_agent VARCHAR(255),
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_audit_company FOREIGN KEY (company_id) REFERENCES companies(id) ON DELETE RESTRICT,
    CONSTRAINT fk_audit_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE SET NULL,
    CONSTRAINT fk_audit_action FOREIGN KEY (action_id) REFERENCES audit_actions(id) ON DELETE RESTRICT,
    INDEX idx_audit_entity (entity_type, entity_id),
    INDEX idx_audit_user (user_id),
    INDEX idx_audit_created (created_at)
);
```

### 7.10 Terminal module

```sql
CREATE TABLE terminal_statuses (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(20) NOT NULL UNIQUE,
    label VARCHAR(30) NOT NULL
);

CREATE TABLE terminal_logs (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    terminal_id BIGINT NOT NULL,
    category VARCHAR(30) NOT NULL DEFAULT 'SYSTEM',
    level VARCHAR(10) NOT NULL,
    message VARCHAR(500) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_tlog_terminal FOREIGN KEY (terminal_id) REFERENCES terminals(id) ON DELETE CASCADE,
    INDEX idx_tlog_terminal_time (terminal_id, created_at)
);

CREATE TABLE terminal_firmware_versions (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    version VARCHAR(20) NOT NULL UNIQUE,
    released_date DATE NOT NULL,
    is_mandatory TINYINT(1) NOT NULL DEFAULT 0,
    download_url VARCHAR(255),
    notes VARCHAR(500),
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);
```

> `terminals` itself is defined in 7.5 (referenced by `attendance_events`). Terminal heartbeat updates `terminals.last_heartbeat_at`; enrollment sets `api_key_hash`/`activation_code_hash`. A scheduled job (1 min) marks a terminal `OFFLINE` when `last_heartbeat_at` is older than 180 s.

### 7.11 Notification module

```sql
CREATE TABLE notifications (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    title VARCHAR(150) NOT NULL,
    body VARCHAR(500),
    type VARCHAR(30) NOT NULL DEFAULT 'INFO',
    is_read TINYINT(1) NOT NULL DEFAULT 0,
    read_at DATETIME,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_notif_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_notif_user_read (user_id, is_read)
);

CREATE TABLE notification_preferences (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL UNIQUE,
    email_enabled TINYINT(1) NOT NULL DEFAULT 1,
    leave_alerts TINYINT(1) NOT NULL DEFAULT 1,
    payroll_alerts TINYINT(1) NOT NULL DEFAULT 1,
    attendance_alerts TINYINT(1) NOT NULL DEFAULT 1,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_notif_pref_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);
```

### 7.12 Approval workflow module (V4)

Approval steps for `leave_requests` and `attendance_adjustments` (Manager → HR). The request tables keep the aggregate status + last approver; `approvals` records every step.

```sql
CREATE TABLE approval_statuses (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(20) NOT NULL UNIQUE,
    label VARCHAR(30) NOT NULL
);

CREATE TABLE approvals (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    request_type VARCHAR(20) NOT NULL,
    request_id BIGINT NOT NULL,
    step_order INT NOT NULL,
    approver_role VARCHAR(20) NOT NULL,
    approver_id BIGINT NULL,
    status_id BIGINT NOT NULL DEFAULT 1,
    comment VARCHAR(500),
    decided_at DATETIME,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_approval_status FOREIGN KEY (status_id) REFERENCES approval_statuses(id) ON DELETE RESTRICT,
    CONSTRAINT fk_approval_approver FOREIGN KEY (approver_id) REFERENCES users(id) ON DELETE SET NULL,
    UNIQUE KEY uk_approval_request_step (request_type, request_id, step_order),
    INDEX idx_approval_request (request_type, request_id),
    INDEX idx_approval_status (status_id)
);
```

> `request_type`: `LEAVE` / `ATTENDANCE` (classifier, like `leave_balance_logs.ref_type`). `approver_role`: `MANAGER` / `HR`. Workflow: LEAVE = Manager → HR (requester is manager → HR only); ATTENDANCE = Manager/HR (creator is approver → HR only).

### 7.13 Lookup seed data (all modules)

```sql
INSERT INTO user_statuses (code, label) VALUES ('ACTIVE','Active'),('DISABLED','Disabled'),('LOCKED','Locked');

INSERT INTO company_statuses (code, label) VALUES ('ACTIVE','Active'),('SUSPENDED','Suspended');

INSERT INTO employee_statuses (code, label) VALUES
('ACTIVE','Active'),('SUSPENDED','Suspended'),('ON_LEAVE','On leave'),
('TERMINATED','Terminated'),('RESIGNED','Resigned'),('RETIRED','Retired');

INSERT INTO genders (code, label) VALUES ('M','Male'),('F','Female');
INSERT INTO marital_statuses (code, label) VALUES ('SINGLE','Single'),('MARRIED','Married'),('DIVORCED','Divorced'),('WIDOWED','Widowed');

INSERT INTO document_types (code, label) VALUES
('CIN','CIN'),('PASSPORT','Passport'),('CONTRACT','Contract'),('DIPLOMA','Diploma'),
('CERTIFICATE','Certificate'),('MEDICAL','Medical certificate'),('OTHER','Other');

INSERT INTO dependent_relationships (code, label) VALUES ('SPOUSE','Spouse'),('CHILD','Child'),('PARENT','Parent'),('OTHER','Other');

INSERT INTO contract_types (code, label) VALUES
('CDI','CDI'),('CDD','CDD'),('INTERN','Internship'),('FREELANCE','Freelance'),('PART_TIME','Part-time'),('APPRENTICE','Apprentice');

INSERT INTO contract_statuses (code, label) VALUES ('ACTIVE','Active'),('EXPIRED','Expired'),('TERMINATED','Terminated'),('SUSPENDED','Suspended');

INSERT INTO salary_component_types (code, label, category) VALUES
('BASE_SALARY','Base salary','BASE'),
('PRIME_TRANSPORT','Transport prime','BONUS'),
('PRIME_RENDEMENT','Performance prime','BONUS'),
('PRIME_NUIT','Night work prime','BONUS'),
('PRIME_DANGER','Danger prime','BONUS'),
('PRIME_PANIER','Meal allowance','BONUS'),
('PRIME_LOGEMENT','Housing allowance','BONUS'),
('HEURES_SUP','Overtime','BONUS'),
('AVANTAGE_NATURE','Benefits in kind','BONUS'),
('DEDUCTION_ABSENCE','Absence deduction','DEDUCTION'),
('DEDUCTION_AVANCE','Advance deduction','DEDUCTION'),
('AUTRE','Other','BONUS');

INSERT INTO event_types (code, label) VALUES ('IN','Check-in'),('OUT','Check-out');

INSERT INTO day_types (code, label) VALUES ('WORKDAY','Workday'),('WEEKEND','Weekend'),('HOLIDAY','Holiday'),('COMPENSATORY','Compensatory rest');

INSERT INTO attendance_statuses (code, label) VALUES
('PRESENT','Present'),('ABSENT','Absent'),('LATE','Late'),('HALF_DAY','Half day'),
('LEAVE','On leave'),('HOLIDAY','Holiday'),('WEEKEND','Weekend'),('NOT_SCHEDULED','Not scheduled'),('ADJUSTED','Adjusted');

INSERT INTO adjustment_types (code, label) VALUES
('ADD_MINUTES','Add worked minutes'),('REMOVE_MINUTES','Remove minutes'),('ADD_OVERTIME','Add overtime'),('REMOVE_OVERTIME','Remove overtime'),('SET_ABSENT','Mark absent');

INSERT INTO adjustment_statuses (code, label) VALUES ('PENDING','Pending'),('APPLIED','Applied'),('REJECTED','Rejected');

INSERT INTO holiday_types (code, label) VALUES ('LEGAL','Legal holiday'),('COMPANY','Company holiday');

INSERT INTO leave_types (code, name, is_paid, default_days_per_year) VALUES
('ANNUAL','Annual leave',1,18),
('SICK','Sick leave',1,NULL),
('MATERNITY','Maternity leave',1,90),
('PATERNITY','Paternity leave',1,NULL),
('EXCEPTIONAL','Exceptional leave',1,NULL),
('UNPAID','Unpaid leave',0,NULL),
('COMPENSATORY','Compensatory rest',1,NULL);

INSERT INTO leave_request_statuses (code, label) VALUES ('PENDING','Pending'),('APPROVED','Approved'),('REJECTED','Rejected'),('CANCELLED','Cancelled');

INSERT INTO payroll_statuses (code, label) VALUES
('DRAFT','Draft'),('COMPUTED','Computed'),('VALIDATED','Validated'),('APPROVED','Approved'),('PAID','Paid'),('CANCELLED','Cancelled');

INSERT INTO terminal_statuses (code, label) VALUES ('ONLINE','Online'),('OFFLINE','Offline'),('MAINTENANCE','Maintenance'),('DISABLED','Disabled');

INSERT INTO audit_actions (code, label) VALUES
('LOGIN','Login'),('LOGOUT','Logout'),('CREATE','Create'),('UPDATE','Update'),('DELETE','Delete'),
('PASSWORD_CHANGE','Password change'),('PAYROLL_RUN','Payroll run'),('PAYROLL_APPROVE','Payroll approval'),
('PAYROLL_PAY','Payroll payment'),('EXPORT','Export'),('STATUS_CHANGE','Status change');

INSERT INTO roles (code, name, description) VALUES
('ADMIN','Administrator','Full access'),
('MANAGER','Manager','Department management'),
('HR','HR Officer','Employees, contracts, leaves'),
('ACCOUNTANT','Accountant','Payroll and legal'),
('USER','Employee','Self-service');

-- Default admin (bcrypt of 'admin000'), default company, default settings, default permissions.
-- Permission codes follow the pattern <module>.<action> (e.g. employee.read, payroll.run, audit.view).
```

---

## 8. Relationships summary

| Relationship | Type | Cardinality |
|---|---|---|
| companies → departments / positions / locations / work_schedules / holidays / payrolls / terminals | one-to-many | 1:N |
| companies → company_settings | one-to-one | 1:1 |
| companies → employees | one-to-many | 1:N |
| departments → positions | one-to-many | 1:N |
| employees → employee_contracts → salary_components | one-to-many ×2 | 1:N:N |
| employees → salary_history / employee_documents / employee_dependents / employee_bank_accounts / employee_emergency_contacts | one-to-many | 1:N |
| employees → attendance_events / attendance_summary / attendance_adjustments | one-to-many | 1:N |
| employees → employee_schedules → work_schedules → work_schedule_lines | many-to-many (via history) | M:N |
| employees → leave_balances / leave_requests / leave_balance_logs | one-to-many | 1:N |
| employees → payroll_items → payroll_item_components | one-to-many ×2 | 1:N:N |
| payrolls → payroll_items | one-to-many (cascade) | 1:N |
| users → roles (via user_roles), roles → permissions (via role_permissions) | many-to-many | M:N |
| users → user_sessions / login_history / password_reset_tokens / notifications / audit_logs | one-to-many | 1:N |
| terminals → attendance_events | one-to-many | 1:N |
| lookup tables → owner tables (via *_id FK, RESTRICT) | reference | 1:N |

---

## 9. Index strategy

- **Every FK** gets an index (implicit in MySQL for the FK, explicit composite for hot queries).
- **Attendance:** `idx_events_employee_time (employee_id, event_time)` serves the daily calculation engine; `uk_summary_employee_date (employee_id, work_date)` guarantees one summary row per day.
- **Payroll:** `uk_payrolls_period` guards "one payroll per period"; `uk_pitem (payroll_id, employee_id)` guards duplicate items.
- **Audit:** `idx_audit_entity (entity_type, entity_id)` for entity history lookup; `idx_audit_created` for time-window queries.
- **Leave:** `idx_leave_dates` for overlap checks (holidays/days between start/end).
- **Unique business keys:** `matricule` (per company), `rfid_uid`, `cin`, `username`, `email`, schedule codes, terminal code/serial.

---

## 10. Spec rule enforcement map

| Spec rule | Enforcement |
|---|---|
| 3.1 No ENUM types | All statuses/types are lookup tables referenced by `*_id` FKs (section 7.12). Zero ENUM or free-string status columns. |
| 3.2 Preserve history | Salary via dated `salary_components` + `salary_history`; contracts as rows; departments/positions via `valid_from/valid_to`; bank accounts dated; legal rates versioned by `year`; schedule assignments dated. No UPDATE of historical rows. |
| 3.3 Payroll never changes | `payroll_attendance_snapshots` freeze the attendance facts used; `payroll_items` + `payroll_item_components` freeze the monetary result; service rejects writes after `PAID`; corrections are new reversal lines. Payslips derive from frozen items. |
| 3.4 Raw vs calculated | `attendance_events` = raw scans (immutable); `attendance_summary` = engine output (one row/day, all minutes). Corrections in `attendance_adjustments`, never in raw rows. |
| Section 4 modules | Tables grouped per module (auth, company, organization, employee, contract, attendance, leave, payroll, legal, audit, terminal, notification). |
| Section 7 first phase | This document = tables, attributes, types, PKs, FKs, relations, constraints, indexes, ER diagram. Code follows only after approval. |

---

## 11. ER diagram

> Lookup/reference tables (`*_statuses`, `*_types`, `roles`, `permissions`, `banks`, ...) are omitted from the diagram for readability; they connect via the `*_id` columns documented in section 7.

```mermaid
erDiagram
    COMPANIES ||--o{ DEPARTMENTS : has
    COMPANIES ||--o{ POSITIONS : has
    COMPANIES ||--o{ LOCATIONS : has
    COMPANIES ||--o{ COMPANY_SETTINGS : has
    COMPANIES ||--o{ EMPLOYEES : employs
    COMPANIES ||--o{ WORK_SCHEDULES : defines
    COMPANIES ||--o{ HOLIDAYS : declares
    COMPANIES ||--o{ TERMINALS : owns
    COMPANIES ||--o{ PAYROLLS : runs
    COMPANIES ||--o{ ATTENDANCE_EVENTS : logs
    COMPANIES ||--o{ ATTENDANCE_SUMMARY : computes

    DEPARTMENTS ||--o{ POSITIONS : groups
    EMPLOYEES }o--o| DEPARTMENTS : assigned_to
    EMPLOYEES }o--o| POSITIONS : holds
    EMPLOYEES }o--o| LOCATIONS : works_at

    EMPLOYEES ||--o{ EMPLOYEE_CONTRACTS : has
    EMPLOYEE_CONTRACTS ||--o{ SALARY_COMPONENTS : contains
    EMPLOYEES ||--o{ SALARY_HISTORY : records
    EMPLOYEES ||--o{ EMPLOYEE_DOCUMENTS : keeps
    EMPLOYEES ||--o{ EMPLOYEE_DEPENDENTS : declares
    EMPLOYEES ||--o{ EMPLOYEE_BANK_ACCOUNTS : owns
    EMPLOYEES ||--o{ EMPLOYEE_EMERGENCY_CONTACTS : declares

    EMPLOYEES ||--o{ ATTENDANCE_EVENTS : scans
    EMPLOYEES ||--o{ ATTENDANCE_SUMMARY : summarized
    EMPLOYEES ||--o{ ATTENDANCE_ADJUSTMENTS : corrects
    TERMINALS ||--o{ ATTENDANCE_EVENTS : produces

    EMPLOYEES ||--o{ EMPLOYEE_SCHEDULES : follows
    WORK_SCHEDULES ||--o{ WORK_SCHEDULE_LINES : contains
    WORK_SCHEDULES ||--o{ EMPLOYEE_SCHEDULES : assigned_via

    EMPLOYEES ||--o{ LEAVE_BALANCES : accrues
    EMPLOYEES ||--o{ LEAVE_REQUESTS : requests
    LEAVE_TYPES ||--o{ LEAVE_BALANCES : quantified
    LEAVE_TYPES ||--o{ LEAVE_REQUESTS : typed_by
    EMPLOYEES ||--o{ LEAVE_BALANCE_LOGS : audits_balance

    PAYROLLS ||--o{ PAYROLL_ITEMS : contains
    PAYROLL_ITEMS ||--o{ PAYROLL_ITEM_COMPONENTS : frozen_lines
    PAYROLL_ITEMS ||--o| PAYSLIPS : produces
    EMPLOYEES ||--o{ PAYROLL_ITEMS : paid_as

    USERS ||--o{ USER_SESSIONS : opens
    USERS ||--o{ LOGIN_HISTORY : appears_in
    USERS ||--o{ PASSWORD_RESET_TOKENS : requests
    USERS ||--o{ NOTIFICATIONS : receives
    USERS ||--o{ AUDIT_LOGS : performs
    USERS }o--o{ ROLES : has
    ROLES }o--o{ PERMISSIONS : grants

    TAX_BRACKETS, CNSS_RATES, CSS_RATES, SMIG_VALUES, FAMILY_ALLOWANCES }o..o| PAYROLLS : referenced_by_year
```

### 11.1 Diagram explanation

- **Company/Organization:** `companies` is the root. `departments`, `positions`, `locations`, `work_schedules`, `holidays`, `terminals`, `payrolls` all belong to a company. Departments group positions; employees are assigned to department/position/location.
- **Employee/Contract:** `employees` is identity-only. Pay-affecting data lives in `employee_contracts` → `salary_components` (dated base salary + primes + deductions) and `salary_history` (before/after trail). Documents, dependents, bank accounts and emergency contacts hang directly off `employees`.
- **Attendance (two levels):** `attendance_events` is the raw, immutable scan feed (one row per IN/OUT from `terminals`). The engine consumes events + `employee_schedules`/`work_schedule_lines` + `holidays` and writes one `attendance_summary` row per employee/day with all minute buckets and status. Manual corrections go to `attendance_adjustments` (approved), which modify the summary, never the raw events.
- **Leave:** `leave_types` (reference), `leave_balances` (annual entitlement/taken/carried/adjusted per type per year), `leave_requests` (workflow with approval), `leave_balance_logs` (every balance delta is traceable).
- **Payroll:** one `payrolls` per company/period with status workflow (DRAFT → … → PAID). Generation first freezes the period's attendance facts into `payroll_attendance_snapshots` (per employee/day, immutable), then `payroll_items` freeze each employee's gross/CNSS/IRPP/CSS/net and hours computed from that snapshot. `payroll_item_components` freeze the exact line items (bonuses, deductions, contributions) that composed the item — payslips reproduce identically forever (rule 3.3).
- **Legal:** `tax_brackets`, `cnss_rates`, `css_rates`, `smig_values`, `family_allowances` are versioned by year; the payroll engine resolves rates for the payroll period's year (rule 3.2).
- **Auth/Audit:** `users` ↔ `roles` ↔ `permissions` (many-to-many join tables). `user_sessions`, `login_history`, `password_reset_tokens`, `notifications`, and `audit_logs` (JSON before/after) hang off `users`. Audit logs record entity changes, login events and payroll actions.

---

## 12. Open decisions to confirm

1. **Multi-company scope:** every business table carries `company_id` (seeded with one default company). OK, or keep single-company (no `company_id`, drop `companies`)? *(Recommend: keep `company_id` — spec lists "companies".)*
2. **Legacy data migration:** old DB is throwaway dev data. Fresh schema + seeds, or a Flyway baseline from current data? *(Recommend: fresh, as previously agreed.)*
3. **Attendance netting:** `hours_netting_enabled` default OFF (per approval). Confirm stays OFF.
4. **Salary editing:** through `salary_components` (dated) via the existing employee form (per approval). Confirm.
5. **Year-versioned legal tables** replace the hardcoded/key-value rates. Confirm seeding with 2026 values.
6. **Family allowances (`family_allowances.amount_per_child`):** placeholder `0` until the regulation figure is provided. Confirm or provide amount.
7. **payslips table:** keep `pdf_path` generation as a later phase (Angular already renders payslips from payroll items). Confirm.
8. **permissions:** define the full permission-code list (`employee.read`, `employee.write`, `payroll.run`, `audit.view`, ...) before generating the role→permission seed. Provide the list or let me propose it.

---

*Next step after approval: Flyway migration V1__ (schema + lookups + seeds), then JPA entities, repositories, services, controllers per module, in the order: auth → company/organization → employee → contract → attendance → leave → payroll → legal → reports → audit → terminal.*
