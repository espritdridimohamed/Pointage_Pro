-- =====================================================================
-- PointagePro - V1 : Full schema (design plan approved)
-- 48 tables grouped by module, created in FK dependency order.
-- Lookup tables first (no FKs), then parent -> child.
-- =====================================================================

-- ---------------------------------------------------------------------
-- LOOKUP / REFERENCE TABLES (no FKs) - spec rule 3.1 (no ENUM types)
-- ---------------------------------------------------------------------

CREATE TABLE user_statuses (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(20) NOT NULL UNIQUE,
    label VARCHAR(50) NOT NULL,
    is_active TINYINT(1) NOT NULL DEFAULT 1,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

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

CREATE TABLE company_statuses (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(20) NOT NULL UNIQUE,
    label VARCHAR(50) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

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

CREATE TABLE document_types (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(20) NOT NULL UNIQUE,
    label VARCHAR(50) NOT NULL
);

CREATE TABLE dependent_relationships (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(20) NOT NULL UNIQUE,
    label VARCHAR(30) NOT NULL
);

CREATE TABLE banks (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(20) NOT NULL UNIQUE,
    name VARCHAR(100) NOT NULL
);

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

CREATE TABLE salary_component_types (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(30) NOT NULL UNIQUE,
    label VARCHAR(100) NOT NULL,
    category VARCHAR(20) NOT NULL DEFAULT 'BONUS'
);

CREATE TABLE event_types (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(10) NOT NULL UNIQUE,
    label VARCHAR(30) NOT NULL
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

CREATE TABLE holiday_types (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(20) NOT NULL UNIQUE,
    label VARCHAR(30) NOT NULL
);

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

CREATE TABLE payroll_statuses (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(20) NOT NULL UNIQUE,
    label VARCHAR(30) NOT NULL
);

CREATE TABLE terminal_statuses (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(20) NOT NULL UNIQUE,
    label VARCHAR(30) NOT NULL
);

CREATE TABLE audit_actions (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(30) NOT NULL UNIQUE,
    label VARCHAR(60) NOT NULL
);

-- ---------------------------------------------------------------------
-- COMPANY / ORGANIZATION MODULE
-- ---------------------------------------------------------------------

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

-- departments.manager_employee_id FK to employees added via ALTER below
-- (circular reference with employees.department_id).
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

-- ---------------------------------------------------------------------
-- EMPLOYEE MODULE (identity only - no salary/schedule on this row)
-- ---------------------------------------------------------------------

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

ALTER TABLE departments
    ADD CONSTRAINT fk_departments_manager FOREIGN KEY (manager_employee_id) REFERENCES employees(id) ON DELETE RESTRICT;

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

-- ---------------------------------------------------------------------
-- CONTRACT MODULE (history-safe: dated salary components)
-- ---------------------------------------------------------------------

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

-- ---------------------------------------------------------------------
-- AUTH MODULE
-- ---------------------------------------------------------------------

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

-- ---------------------------------------------------------------------
-- TERMINAL MODULE
-- ---------------------------------------------------------------------

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
    enrolled_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_terminals_company FOREIGN KEY (company_id) REFERENCES companies(id) ON DELETE RESTRICT,
    CONSTRAINT fk_terminals_location FOREIGN KEY (location_id) REFERENCES locations(id) ON DELETE RESTRICT,
    CONSTRAINT fk_terminals_status FOREIGN KEY (status_id) REFERENCES terminal_statuses(id) ON DELETE RESTRICT,
    INDEX idx_terminals_company (company_id),
    INDEX idx_terminals_status (status_id)
);

-- ---------------------------------------------------------------------
-- ATTENDANCE MODULE (raw events + calculated summary - rule 3.4)
-- ---------------------------------------------------------------------

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
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_events_company FOREIGN KEY (company_id) REFERENCES companies(id) ON DELETE RESTRICT,
    CONSTRAINT fk_events_employee FOREIGN KEY (employee_id) REFERENCES employees(id) ON DELETE RESTRICT,
    CONSTRAINT fk_events_terminal FOREIGN KEY (terminal_id) REFERENCES terminals(id) ON DELETE RESTRICT,
    CONSTRAINT fk_events_type FOREIGN KEY (event_type_id) REFERENCES event_types(id) ON DELETE RESTRICT,
    INDEX idx_events_employee_time (employee_id, event_time),
    INDEX idx_events_terminal_time (terminal_id, event_time),
    INDEX idx_events_time (event_time)
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
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_summary_company FOREIGN KEY (company_id) REFERENCES companies(id) ON DELETE RESTRICT,
    CONSTRAINT fk_summary_employee FOREIGN KEY (employee_id) REFERENCES employees(id) ON DELETE RESTRICT,
    CONSTRAINT fk_summary_day_type FOREIGN KEY (day_type_id) REFERENCES day_types(id) ON DELETE RESTRICT,
    CONSTRAINT fk_summary_schedule FOREIGN KEY (schedule_id) REFERENCES work_schedules(id) ON DELETE RESTRICT,
    CONSTRAINT fk_summary_status FOREIGN KEY (status_id) REFERENCES attendance_statuses(id) ON DELETE RESTRICT,
    UNIQUE KEY uk_summary_employee_date (employee_id, work_date),
    INDEX idx_summary_company_date (company_id, work_date),
    INDEX idx_summary_status (status_id)
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

-- ---------------------------------------------------------------------
-- LEAVE MODULE
-- ---------------------------------------------------------------------

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

-- ---------------------------------------------------------------------
-- PAYROLL MODULE (frozen snapshot - rule 3.3)
-- ---------------------------------------------------------------------

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

-- ---------------------------------------------------------------------
-- LEGAL MODULE (versioned by year - rule 3.2)
-- ---------------------------------------------------------------------

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

-- ---------------------------------------------------------------------
-- AUDIT MODULE
-- ---------------------------------------------------------------------

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
