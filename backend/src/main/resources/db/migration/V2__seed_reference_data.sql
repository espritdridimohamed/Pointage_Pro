-- =====================================================================
-- PointagePro - V2 : Reference data + seeds
-- Lookups (rule 3.1), roles/permissions, default company + settings,
-- default admin, standard work schedule, legal rates 2026 (rule 3.2).
-- =====================================================================

-- ---------------------------------------------------------------------
-- AUTH lookups
-- ---------------------------------------------------------------------

INSERT INTO user_statuses (code, label) VALUES
('ACTIVE','Active'),('DISABLED','Disabled'),('LOCKED','Locked');

INSERT INTO roles (code, name, description) VALUES
('ADMIN','Administrator','Full access'),
('MANAGER','Manager','Department management'),
('HR','HR Officer','Employees, contracts, leaves'),
('ACCOUNTANT','Accountant','Payroll and legal'),
('USER','Employee','Self-service');

INSERT INTO permissions (code, name, description) VALUES
-- auth
('user.read','Read users','View users and profiles'),
('user.write','Manage users','Create, update, disable users'),
('role.read','Read roles','View roles and permissions'),
('role.write','Manage roles','Assign roles and permissions'),
-- company
('company.read','Read company','View company information'),
('company.write','Manage company','Edit company information'),
-- organization
('department.read','Read departments','View departments'),
('department.write','Manage departments','Create and update departments'),
('position.read','Read positions','View positions'),
('position.write','Manage positions','Create and update positions'),
('location.read','Read locations','View locations'),
('location.write','Manage locations','Create and update locations'),
('schedule.read','Read schedules','View work schedules'),
('schedule.write','Manage schedules','Create and update work schedules'),
-- employee
('employee.read','Read employees','View employee records'),
('employee.write','Manage employees','Create and update employees'),
('employee.delete','Delete employees','Delete employee records'),
('document.read','Read documents','View employee documents'),
('document.write','Manage documents','Upload and manage documents'),
-- contract
('contract.read','Read contracts','View contracts and salary components'),
('contract.write','Manage contracts','Create and update contracts and salaries'),
-- attendance
('attendance.read','Read attendance','View attendance data'),
('attendance.write','Record attendance','Manual check-in/out and records'),
('attendance.adjust','Adjust attendance','Approve attendance corrections'),
('attendance.recalculate','Recalculate attendance','Run the attendance engine'),
('holiday.read','Read holidays','View holidays'),
('holiday.write','Manage holidays','Create and update holidays'),
('terminal.read','Read terminals','View terminals'),
('terminal.write','Manage terminals','Enroll and configure terminals'),
-- leave
('leave.read','Read leaves','View leave requests'),
('leave.write','Request leave','Create leave requests'),
('leave.approve','Approve leaves','Approve or reject leave requests'),
-- payroll
('payroll.read','Read payroll','View payroll runs and payslips'),
('payroll.run','Run payroll','Generate payroll'),
('payroll.validate','Validate payroll','Validate computed payroll'),
('payroll.approve','Approve payroll','Approve payroll for payment'),
('payroll.pay','Pay payroll','Mark payroll as paid'),
('payroll.cancel','Cancel payroll','Cancel draft payroll'),
('payslip.read','Read payslips','View and print payslips'),
-- legal
('legal.read','Read legal settings','View legal rates'),
('legal.write','Manage legal settings','Update legal rates'),
-- reports
('report.read','Read reports','View reports'),
('report.export','Export reports','Export PDF/Excel reports'),
-- dashboard
('dashboard.read','Read dashboard','View dashboard'),
-- audit
('audit.read','Read audit logs','View audit trail'),
-- notification
('notification.read','Read notifications','View notifications');

-- ---------------------------------------------------------------------
-- Organization / Company lookups
-- ---------------------------------------------------------------------

INSERT INTO company_statuses (code, label) VALUES
('ACTIVE','Active'),('SUSPENDED','Suspended');

-- ---------------------------------------------------------------------
-- Employee lookups
-- ---------------------------------------------------------------------

INSERT INTO employee_statuses (code, label) VALUES
('ACTIVE','Active'),('SUSPENDED','Suspended'),('ON_LEAVE','On leave'),
('TERMINATED','Terminated'),('RESIGNED','Resigned'),('RETIRED','Retired');

INSERT INTO genders (code, label) VALUES ('M','Male'),('F','Female');

INSERT INTO marital_statuses (code, label) VALUES
('SINGLE','Single'),('MARRIED','Married'),('DIVORCED','Divorced'),('WIDOWED','Widowed');

INSERT INTO document_types (code, label) VALUES
('CIN','CIN'),('PASSPORT','Passport'),('CONTRACT','Contract'),('DIPLOMA','Diploma'),
('CERTIFICATE','Certificate'),('MEDICAL','Medical certificate'),('OTHER','Other');

INSERT INTO dependent_relationships (code, label) VALUES
('SPOUSE','Spouse'),('CHILD','Child'),('PARENT','Parent'),('OTHER','Other');

INSERT INTO banks (code, name) VALUES
('BIAT','BIAT'),('BNA','BNA'),('ATB','ATB'),('UIB','UIB'),('BH','BH'),
('STB','STB'),('AMEN','Amen Bank'),('ZITOUNA','Zitouna Bank'),('ALBARAKA','Al Baraka Bank'),
('BT','Banque de Tunisie');

-- ---------------------------------------------------------------------
-- Contract lookups
-- ---------------------------------------------------------------------

INSERT INTO contract_types (code, label) VALUES
('CDI','CDI'),('CDD','CDD'),('INTERN','Internship'),('FREELANCE','Freelance'),
('PART_TIME','Part-time'),('APPRENTICE','Apprentice');

INSERT INTO contract_statuses (code, label) VALUES
('ACTIVE','Active'),('EXPIRED','Expired'),('TERMINATED','Terminated'),('SUSPENDED','Suspended');

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

-- ---------------------------------------------------------------------
-- Attendance lookups
-- ---------------------------------------------------------------------

INSERT INTO event_types (code, label) VALUES ('IN','Check-in'),('OUT','Check-out');

INSERT INTO day_types (code, label) VALUES
('WORKDAY','Workday'),('WEEKEND','Weekend'),('HOLIDAY','Holiday'),('COMPENSATORY','Compensatory rest');

INSERT INTO attendance_statuses (code, label) VALUES
('PRESENT','Present'),('ABSENT','Absent'),('LATE','Late'),('HALF_DAY','Half day'),
('LEAVE','On leave'),('HOLIDAY','Holiday'),('WEEKEND','Weekend'),
('NOT_SCHEDULED','Not scheduled'),('ADJUSTED','Adjusted');

INSERT INTO adjustment_types (code, label) VALUES
('ADD_MINUTES','Add worked minutes'),('REMOVE_MINUTES','Remove minutes'),
('ADD_OVERTIME','Add overtime'),('REMOVE_OVERTIME','Remove overtime'),('SET_ABSENT','Mark absent');

INSERT INTO adjustment_statuses (code, label) VALUES
('PENDING','Pending'),('APPLIED','Applied'),('REJECTED','Rejected');

INSERT INTO holiday_types (code, label) VALUES
('LEGAL','Legal holiday'),('COMPANY','Company holiday');

-- ---------------------------------------------------------------------
-- Leave lookups
-- ---------------------------------------------------------------------

INSERT INTO leave_types (code, name, is_paid, default_days_per_year) VALUES
('ANNUAL','Annual leave',1,18),
('SICK','Sick leave',1,NULL),
('MATERNITY','Maternity leave',1,90),
('PATERNITY','Paternity leave',1,NULL),
('EXCEPTIONAL','Exceptional leave',1,NULL),
('UNPAID','Unpaid leave',0,NULL),
('COMPENSATORY','Compensatory rest',1,NULL);

INSERT INTO leave_request_statuses (code, label) VALUES
('PENDING','Pending'),('APPROVED','Approved'),('REJECTED','Rejected'),('CANCELLED','Cancelled');

-- ---------------------------------------------------------------------
-- Payroll / Terminal / Audit lookups
-- ---------------------------------------------------------------------

INSERT INTO payroll_statuses (code, label) VALUES
('DRAFT','Draft'),('COMPUTED','Computed'),('VALIDATED','Validated'),
('APPROVED','Approved'),('PAID','Paid'),('CANCELLED','Cancelled');

INSERT INTO terminal_statuses (code, label) VALUES
('ONLINE','Online'),('OFFLINE','Offline'),('MAINTENANCE','Maintenance'),('DISABLED','Disabled');

INSERT INTO audit_actions (code, label) VALUES
('LOGIN','Login'),('LOGOUT','Logout'),('CREATE','Create'),('UPDATE','Update'),('DELETE','Delete'),
('PASSWORD_CHANGE','Password change'),('PAYROLL_RUN','Payroll run'),
('PAYROLL_APPROVE','Payroll approval'),('PAYROLL_PAY','Payroll payment'),
('EXPORT','Export'),('STATUS_CHANGE','Status change');

-- ---------------------------------------------------------------------
-- Default company + settings (multi-company ready, one seeded company)
-- ---------------------------------------------------------------------

INSERT INTO companies (code, name, legal_name, currency, status_id) VALUES
('DEFAULT','Default Company','Default Company', 'TND', 1);

INSERT INTO locations (company_id, code, name, address, is_active) VALUES
(1, 'HQ', 'Siège social', NULL, 1);

INSERT INTO company_settings (company_id, fiscal_year_start_month, weekly_working_hours,
                              monthly_working_hours, overtime_enabled, overtime_rate_multiplier,
                              hours_netting_enabled) VALUES
(1, 1, 40.00, 151.67, 1, 1.25, 0);

-- ---------------------------------------------------------------------
-- Default admin user (password: admin000, bcrypt hash)
-- ---------------------------------------------------------------------

INSERT INTO users (username, email, password_hash, full_name, status_id) VALUES
('admin', 'admin@sepabagro.tn',
 '$2b$10$8kLglwtaF9prVKsB51iQtuCyVVFBdXZDW3iF02TY3yaoqZmVHxkyy',
 'Administrator', 1);

INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id FROM users u, roles r WHERE u.username = 'admin' AND r.code = 'ADMIN';

INSERT INTO notification_preferences (user_id) SELECT id FROM users WHERE username = 'admin';

-- ---------------------------------------------------------------------
-- Role -> permission mapping
-- ---------------------------------------------------------------------

-- ADMIN: all permissions
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r CROSS JOIN permissions p WHERE r.code = 'ADMIN';

-- MANAGER
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p ON p.code IN
('dashboard.read','department.read','position.read','schedule.read','employee.read',
 'attendance.read','attendance.adjust','leave.read','leave.approve',
 'report.read','report.export','notification.read')
WHERE r.code = 'MANAGER';

-- HR
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p ON p.code IN
('dashboard.read','department.read','department.write','position.read','position.write',
 'schedule.read','schedule.write','employee.read','employee.write','employee.delete',
 'document.read','document.write','contract.read','contract.write',
 'attendance.read','attendance.write','attendance.adjust','attendance.recalculate',
 'holiday.read','holiday.write','leave.read','leave.write','leave.approve',
 'report.read','report.export','notification.read')
WHERE r.code = 'HR';

-- ACCOUNTANT
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p ON p.code IN
('dashboard.read','employee.read','attendance.read','payroll.read','payroll.run',
 'payroll.validate','payroll.approve','payroll.pay','payroll.cancel','payslip.read',
 'legal.read','report.read','report.export','notification.read')
WHERE r.code = 'ACCOUNTANT';

-- USER
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p ON p.code IN
('dashboard.read','employee.read','attendance.read','leave.read','leave.write','notification.read')
WHERE r.code = 'USER';

-- ---------------------------------------------------------------------
-- Legal rates 2026 (versioned by year - rule 3.2)
-- No schedule is seeded: each employee gets an assigned schedule
-- (employee_schedules); a company default template can be created in
-- Settings for employees without an individual schedule.
-- ---------------------------------------------------------------------

-- IRPP 2026 (8 annual brackets)
INSERT INTO tax_brackets (year, bracket_order, lower_bound, upper_bound, rate_percent) VALUES
(2026, 1, 0, 5000, 0),
(2026, 2, 5000, 20000, 15),
(2026, 3, 20000, 30000, 25),
(2026, 4, 30000, 50000, 30),
(2026, 5, 50000, 60000, 33),
(2026, 6, 60000, 80000, 36),
(2026, 7, 80000, 150000, 38),
(2026, 8, 150000, 999999999, 40);

-- CNSS: salariale 9.68% (no ceiling), patronale 16.57%, allocations familiales 0.55%
INSERT INTO cnss_rates (year, employee_rate, employer_rate, family_allocations_rate, ceiling_amount, active_from) VALUES
(2026, 9.68, 16.57, 0.55, NULL, '2026-01-01');

-- CSS: salariale 0.5% (2027 will be 1%)
INSERT INTO css_rates (year, employee_rate, employer_rate, active_from) VALUES
(2026, 0.50, 1.00, '2026-01-01');

-- SMIG 2026
INSERT INTO smig_values (year, hourly_rate, monthly_rate, weekly_rate, active_from) VALUES
(2026, 3.000, 524.954, 121.144, '2026-01-01');

-- Family allowances: amount per child pending regulation confirmation
INSERT INTO family_allowances (year, max_children, amount_per_child, active_from) VALUES
(2026, 6, 0, '2026-01-01');
