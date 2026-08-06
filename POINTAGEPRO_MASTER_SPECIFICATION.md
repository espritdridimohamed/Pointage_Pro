# PointagePro - Master Software Specification

## 1. Project Overview

PointagePro is an enterprise Human Resources (HR), Attendance, and Payroll management system designed for Tunisian companies.

The system manages:

- Employees
- Contracts
- Attendance using RFID terminals
- Working hours calculation
- Leaves
- Holidays
- Overtime
- Salary calculation
- Tunisian payroll rules
- CNSS contributions
- IRPP income tax
- CSS
- Payslips
- Reports
- Audit logs


The goal is to build a professional ERP-level backend, not a simple attendance application.


---

# 2. Technology Stack

Backend:

- Java 17
- Spring Boot 3.x
- Spring Security
- Spring Data JPA / Hibernate
- Maven


Database:

- MySQL 8+
- Flyway migrations


Frontend:

- Angular (existing project)
- Keep API compatibility whenever possible


Embedded:

- ESP32 RFID attendance terminals
- REST API communication


---

# 3. Main Architecture Rules


## 3.1 Database rules

IMPORTANT:

Do NOT use database ENUM types.

Example:

BAD:

employee.status ENUM('ACTIVE','LEFT')


GOOD:

employee.status_id

connected to:

employee_statuses table


All changing values must use lookup/reference tables.


Examples:

- employee statuses
- contract types
- leave types
- payroll statuses
- document types
- attendance statuses



---

## 3.2 Historical data rule

The system must preserve history.

Never overwrite important historical information.


Examples:

Salary change:

BAD:

Update employee salary from 1500 to 1800


GOOD:

Create salary history record:

1500
valid until June 2026

1800
starting July 2026



Same principle applies to:

- contracts
- departments
- positions
- bank accounts
- tax situation
- legal rates



---

## 3.3 Payroll rule

A generated and paid payroll must never change.


Payroll must keep a snapshot of:

- the attendance facts used (worked / overtime / late / absence minutes per day, frozen into `payroll_attendance_snapshots` at generation time)
- salary
- bonuses
- deductions
- CNSS
- IRPP
- CSS
- net salary


Old payslips must always remain identical.


---

## 3.4 Attendance rule

Attendance has two levels:


RAW DATA:

What physically happened.

Example:

Employee scanned RFID at:

08:03


Stored in:

attendance_events


CALCULATED DATA:

Meaning of attendance:

- late
- worked minutes
- missing minutes
- overtime


Stored in:

attendance_summary



Never mix raw scans and calculations.



---

# 4. Main Modules


The system will contain these modules:


## Authentication

Responsible for:

- users
- roles
- permissions
- security


---

## Organization

Responsible for:

- companies
- departments
- positions
- locations


---

## Employee Management

Responsible for:

- employee identity
- documents
- dependents
- bank accounts
- emergency contacts


---

## Contract Management

Responsible for:

- contracts
- salary history
- salary components


---

## Attendance Management

Responsible for:

- RFID scans
- attendance records
- working hours engine
- overtime calculation


---

## Leave Management

Responsible for:

- vacations
- sick leave
- maternity leave
- holidays


---

## Payroll Management

Responsible for:

- payroll generation
- payslips
- deductions
- bonuses


---

## Tunisian Legal Module

Responsible for:

- CNSS rates
- IRPP brackets
- CSS
- SMIG
- family deductions


---

## Audit Module

Responsible for:

- tracking modifications
- salary changes
- payroll actions
- security events



---

# 5. Development Rules


Before generating code:


The AI assistant MUST:

1. Analyze architecture
2. Identify missing relations
3. Identify possible database problems
4. Propose improvements
5. Wait for approval


Do NOT immediately generate code.


---

# 6. Backend Architecture


Use modular Spring Boot architecture:


com.pointagepro

auth
company
organization
employee
contract
attendance
leave
payroll
legal
reports
audit
terminal
shared

Each module follows: 
controller
service
repository
entity
dto
mapper
exception

Rules:

- Do not expose entities directly
- Use DTOs
- Validate inputs
- Use transactions where required
- Write clean services
- Add unit tests for business logic



---

# 7. First Development Phase


The first task is NOT coding.


The first task is:

Create the complete database design.


Deliver:

1. Complete list of tables
2. All attributes
3. Data types
4. Primary keys
5. Foreign keys
6. Relations
7. Constraints
8. Indexes
9. ER diagram


After approval:

Generate:

- Flyway migrations
- JPA entities
- Repositories
- Services
- Controllers



---

# 8. Quality Requirements


The final system must be:

- Secure
- Auditable
- Maintainable
- Scalable
- Compatible with Tunisian payroll rules
- Ready for production usage


END OF SPECIFICATION