# PROJECT_MAP.md

## RFID Employee Attendance Management System

---

### [PROJECT]

- **Name:** PointagePro — RFID Employee Attendance Management
- **Type:** Attendance Management System
- **Scope:** Employee management, RFID attendance tracking, leave management, payroll summary, reports, statistics
- **NOT:** ERP, Accounting, Inventory, Finance

---

### [TECH_STACK]

| Layer       | Technology                    | Version    |
|-------------|-------------------------------|------------|
| Frontend    | Angular                       | 19.2.x     |
| UI Library  | Angular Material              | 19.2.x     |
| Backend     | Spring Boot                   | 4.1.0      |
| Security    | Spring Security + JWT         | 7.x        |
| ORM         | Spring Data JPA               | 3.x        |
| Database    | MySQL (LTS)                   | 8.4        |
| Java        | Java                          | 17+        |
| ESP32       | Arduino ESP32 Core            | 3.3.10     |
| RFID Lib    | MFRC522 (miguelbalboa/rfid)   | 1.4.12     |
| JSON (ESP)  | ArduinoJson                   | 7.x        |

---

### [SYSTEM_FLOW]

```
┌─────────┐      ┌──────────┐      ┌──────────────┐      ┌─────────┐
│  RFID    │ SPI  │  ESP32   │ HTTP │  Spring Boot │ SQL  │  MySQL  │
│  Card    │─────▶│  RC522   │─────▶│  REST API    │─────▶│  DB     │
└─────────┘      └──────────┘      └──────────────┘      └─────────┘
```

1. Employee taps RFID card on ESP32 terminal
2. ESP32 reads UID via SPI (RC522)
3. ESP32 POSTs RFID UID to Spring Boot REST API
4. Backend looks up employee, determines check-in/check-out
5. Backend calculates worked hours, late, overtime
6. Backend returns response (employee name, action, status)
7. ESP32 displays result on TFT + LED/buzzer feedback
8. Admin views all data on Angular web application

---

### [DATABASE]

| Table              | Purpose                        | Key Relationships      |
|--------------------|--------------------------------|------------------------|
| users              | System users (admin, HR)       | —                      |
| employees          | Employee records + RFID UID    | —                      |
| attendance         | Daily check-in/out records     | → employees            |
| leave_requests     | Leave applications             | → employees, → users   |
| leave_allocations  | Annual leave allocation tracking| → employees            |
| company_settings   | Configurable key-value pairs   | —                      |
| payrolls           | Monthly payroll records        | —                      |
| payroll_items      | Per-employee payroll details   | → payrolls, → employees|
| payroll_attendance_snapshots | Frozen per-day attendance facts per payroll run | → payrolls, → employees |
| notifications      | In-app notifications           | → users                |

#### users

| Column     | Type                 | Constraints                     |
|------------|----------------------|---------------------------------|
| id         | BIGINT               | PK, AUTO_INCREMENT              |
| username   | VARCHAR(50)          | UNIQUE, NOT NULL                |
| password   | VARCHAR(255)         | NOT NULL                        |
| full_name  | VARCHAR(100)         | NOT NULL                        |
| email      | VARCHAR(100)         | UNIQUE                          |
| role       | ENUM('ADMIN','HR','MANAGER') | NOT NULL, DEFAULT 'ADMIN' |
| enabled    | BOOLEAN              | DEFAULT TRUE                    |
| created_at | DATETIME             | DEFAULT CURRENT_TIMESTAMP       |
| updated_at | DATETIME             | ON UPDATE CURRENT_TIMESTAMP     |

#### employees

| Column       | Type                     | Constraints              |
|--------------|--------------------------|--------------------------|
| id           | BIGINT                   | PK, AUTO_INCREMENT       |
| matricule    | VARCHAR(20)              | UNIQUE, NOT NULL         |
| first_name   | VARCHAR(50)              | NOT NULL                 |
| last_name    | VARCHAR(50)              | NOT NULL                 |
| phone        | VARCHAR(20)              |                          |
| email        | VARCHAR(100)             |                          |
| position     | VARCHAR(100)             |                          |
| base_salary  | DECIMAL(10,2)            |                          |
| rfid_uid     | VARCHAR(30)              | UNIQUE                   |
| hiring_date  | DATE                     |                          |
| status       | ENUM('ACTIVE','INACTIVE','TERMINATED') | DEFAULT 'ACTIVE' |
| created_at   | DATETIME                 | DEFAULT CURRENT_TIMESTAMP |
| updated_at   | DATETIME                 | ON UPDATE CURRENT_TIMESTAMP |

#### attendance

| Column         | Type            | Constraints                    |
|----------------|-----------------|--------------------------------|
| id             | BIGINT          | PK, AUTO_INCREMENT             |
| employee_id    | BIGINT          | FK → employees.id, NOT NULL    |
| date           | DATE            | NOT NULL                       |
| check_in       | DATETIME        |                                |
| check_out      | DATETIME        |                                |
| worked_hours   | DECIMAL(5,2)    | DEFAULT 0                      |
| late_minutes   | INT             | DEFAULT 0                      |
| overtime_hours | DECIMAL(5,2)    | DEFAULT 0                      |
| status         | ENUM('PRESENT','ABSENT','LATE','HALF_DAY','LEAVE','HOLIDAY') | DEFAULT 'PRESENT' |
| created_at     | DATETIME        | DEFAULT CURRENT_TIMESTAMP      |
| updated_at     | DATETIME        | ON UPDATE CURRENT_TIMESTAMP    |
|                |                 | UNIQUE(employee_id, date)      |

#### leave_requests

| Column       | Type            | Constraints                    |
|--------------|-----------------|--------------------------------|
| id           | BIGINT          | PK, AUTO_INCREMENT             |
| employee_id  | BIGINT          | FK → employees.id, NOT NULL    |
| leave_type   | VARCHAR(20)     | NOT NULL                       |
| start_date   | DATE            | NOT NULL                       |
| end_date     | DATE            | NOT NULL                       |
| reason       | TEXT            |                                |
| status       | VARCHAR(20)     | DEFAULT 'PENDING'              |
| approved_by  | BIGINT          | FK → users.id, NULLABLE        |
| created_at   | DATETIME        | DEFAULT CURRENT_TIMESTAMP      |
| updated_at   | DATETIME        | ON UPDATE CURRENT_TIMESTAMP |

#### leave_allocations

| Column       | Type            | Constraints                    |
|--------------|-----------------|--------------------------------|
| id           | BIGINT          | PK, AUTO_INCREMENT             |
| employee_id  | BIGINT          | FK → employees.id, NOT NULL    |
| year         | INT             | NOT NULL                       |
| leave_type   | VARCHAR(20)     | NOT NULL                       |
| allocated    | INT             | DEFAULT 0                      |
| used         | INT             | DEFAULT 0                      |
| created_at   | DATETIME        | DEFAULT CURRENT_TIMESTAMP      |
| updated_at   | DATETIME        | ON UPDATE CURRENT_TIMESTAMP    |
|              |                 | UNIQUE(employee_id, year, leave_type) |

#### company_settings

| Column       | Type            | Constraints              |
|--------------|-----------------|--------------------------|
| id           | BIGINT          | PK, AUTO_INCREMENT       |
| setting_key  | VARCHAR(100)    | UNIQUE, NOT NULL         |
| setting_value| VARCHAR(255)    | NOT NULL                 |
| description  | VARCHAR(255)    |                          |
| created_at   | DATETIME        | DEFAULT CURRENT_TIMESTAMP |
| updated_at   | DATETIME        | ON UPDATE CURRENT_TIMESTAMP |

**Default Settings:**

| Key                    | Value     | Description                |
|------------------------|-----------|----------------------------|
| company_name           | SepabAgro | Company name               |
| work_start_time        | 08:00     | Daily work start           |
| work_end_time          | 17:00     | Daily work end             |
| late_grace_minutes     | 15        | Minutes after start = late |
| overtime_threshold_hours| 8        | Hours before overtime      |
| work_days_per_week     | 6         | Working days per week      |
| overtime_rate          | 1.5       | Multiplier for overtime    |

---

### [MODULES]

| #  | Module          | Backend Package        | Frontend Folder                    | Description                         |
|----|-----------------|------------------------|------------------------------------|-------------------------------------|
| 1  | Auth            | com.pointagepro.auth   | core/auth, pages/login             | Login, JWT, user sessions           |
| 2  | Employee        | com.pointagepro.employee | features/employees               | CRUD, RFID assignment, search       |
| 3  | Attendance      | com.pointagepro.attendance | features/attendance             | Check-in/out, manual entry, status  |
| 4  | Leave           | com.pointagepro.leave  | features/leaves                    | Request, approve/reject, allocation-based carry-over balance |
| 5  | Payroll         | com.pointagepro.payroll | features/payroll                 | Monthly salary summary              |
| 6  | Reports         | com.pointagepro.reports | features/reports                 | Attendance summaries                |
| 7  | Statistics      | com.pointagepro.statistics | features/statistics            | Charts, trends                      |
| 8  | Dashboard       | com.pointagepro.dashboard | features/dashboard               | Today's overview, metrics           |
| 9  | Settings        | com.pointagepro.settings | features/settings               | Company config                      |
| 10 | Users           | (reuses auth module)   | features/users                    | User management                     |
| 11 | ESP32           | com.pointagepro.esp32  | — (firmware/src/)                 | RFID scan endpoint, heartbeat       |

---

### [API]

**Base URL:** `http://localhost:8080/api/v1`

#### Authentication

| Method | Endpoint          | Description        | Auth     |
|--------|-------------------|--------------------|----------|
| POST   | /auth/login       | Login              | None     |
| POST   | /auth/refresh     | Refresh JWT token  | JWT      |

#### Employees

| Method | Endpoint              | Description              | Auth          |
|--------|-----------------------|--------------------------|---------------|
| GET    | /employees            | List (paginated/searchable) | JWT        |
| GET    | /employees/{id}       | Get by ID                | JWT           |
| POST   | /employees            | Create                   | JWT (ADMIN/HR) |
| PUT    | /employees/{id}       | Update                   | JWT (ADMIN/HR) |
| DELETE | /employees/{id}       | Soft-delete              | JWT (ADMIN)   |

#### Attendance

| Method | Endpoint                           | Description           | Auth          |
|--------|------------------------------------|-----------------------|---------------|
| GET    | /attendance                        | List (paginated)      | JWT           |
| GET    | /attendance/{id}                   | Get by ID             | JWT           |
| POST   | /attendance                        | Manual entry          | JWT (ADMIN/HR) |
| PUT    | /attendance/{id}                   | Edit record           | JWT (ADMIN/HR) |
| GET    | /attendance/today                  | Today's summary       | JWT           |
| GET    | /attendance/employee/{employeeId}  | By employee           | JWT           |

#### Leave Requests

| Method | Endpoint                         | Description          | Auth          |
|--------|----------------------------------|----------------------|---------------|
| GET    | /leaves                          | List all             | JWT           |
| GET    | /leaves/{id}                     | Get by ID            | JWT           |
| GET    | /leaves/employee/{employeeId}    | By employee          | JWT           |
| GET    | /leaves/balance/{employeeId}     | Leave balance per type | JWT         |
| GET    | /leaves/stats                    | Pending count        | JWT           |
| POST   | /leaves                          | Create               | JWT           |
| PUT    | /leaves/{id}/approve             | Approve              | JWT (ADMIN/HR) |
| PUT    | /leaves/{id}/refuse              | Refuse               | JWT (ADMIN/HR) |
| DELETE | /leaves/{id}                     | Delete               | JWT (ADMIN)   |

#### Payroll

| Method | Endpoint                                | Description      | Auth              |
|--------|-----------------------------------------|------------------|-------------------|
| GET    | /payroll?month=&year=                   | Monthly summary  | JWT (ADMIN/MGR)   |
| GET    | /payroll/employee/{employeeId}?month=&year= | Employee detail | JWT (ADMIN/MGR) |

#### Reports

| Method | Endpoint                                | Description      | Auth   |
|--------|-----------------------------------------|------------------|--------|
| GET    | /reports/attendance-summary?startDate=&endDate= | Summary | JWT    |
| GET    | /reports/employee/{employeeId}?startDate=&endDate= | Employee report | JWT |

#### Statistics

| Method | Endpoint                          | Description         | Auth   |
|--------|-----------------------------------|---------------------|--------|
| GET    | /statistics/monthly?month=&year=  | Monthly stats       | JWT    |
| GET    | /statistics/employee/{employeeId}?startDate=&endDate= | Employee stats | JWT |

#### Dashboard

| Method | Endpoint                | Description           | Auth   |
|--------|-------------------------|-----------------------|--------|
| GET    | /dashboard/stats        | Today's key metrics   | JWT    |
| GET    | /dashboard/today-attendance | Today's attendance | JWT    |

#### Settings

| Method | Endpoint  | Description    | Auth        |
|--------|-----------|----------------|-------------|
| GET    | /settings | Get all        | JWT (ADMIN) |
| PUT    | /settings | Batch update   | JWT (ADMIN) |

#### Users

| Method | Endpoint        | Description | Auth        |
|--------|-----------------|-------------|-------------|
| GET    | /users          | List all    | JWT (ADMIN) |
| POST   | /users          | Create      | JWT (ADMIN) |
| PUT    | /users/{id}     | Update      | JWT (ADMIN) |
| DELETE | /users/{id}     | Disable     | JWT (ADMIN) |

#### ESP32

| Method | Endpoint        | Description  | Auth     |
|--------|-----------------|--------------|----------|
| POST   | /esp32/scan     | RFID scan    | API Key  |
| POST   | /esp32/heartbeat| Keep-alive   | API Key  |

**ESP32 /scan Response (success):**
```json
{
  "success": true,
  "action": "CHECK_IN",
  "employeeName": "Ahmed Benali",
  "matricule": "EMP-001",
  "message": "Welcome Ahmed",
  "time": "08:02:15"
}
```

**ESP32 /scan Response (unknown card):**
```json
{
  "success": false,
  "message": "Unknown card"
}
```

---

### [HARDWARE]

| Component           | Model / Type         | Interface | Purpose              |
|---------------------|----------------------|-----------|----------------------|
| Microcontroller     | ESP32 DevKit V1      | —         | Main controller      |
| RFID Reader         | RC522                | SPI       | Read RFID card UID   |
| RFID Cards          | MIFARE Classic 1K    | 13.56MHz  | Employee badges      |
| Display             | 1.8" ST7735 TFT     | SPI       | 128x160 color display|
| RTC Module          | DS3231               | I2C       | Real-time clock      |
| LED Module          | WS2812B 8-LED strip  | Digital   | Status feedback      |
| Buzzer              | Active buzzer        | GPIO 25   | Audio feedback       |
| SD Card Module      | MicroSD SPI          | SPI       | Offline log storage  |
| Button              | Momentary push       | GPIO 26   | Manual trigger       |
| Power Supply        | 5V 2A               | —         | Power                |

#### Wiring

| ESP32 Pin    | RC522 Pin  | ST7735 Pin | DS3231 Pin | SD Card Pin | Other          |
|--------------|------------|------------|------------|-------------|----------------|
| 3.3V         | 3.3V       | 3.3V       | —          | —           |                |
| 5V           | —          | 5V(VCC)    | 5V         | 5V(VCC)     | WS2812B VCC    |
| GND          | GND        | GND        | GND        | GND         | All GND        |
| GPIO 27      | SDA (CS)   | —          | —          | —           | RC522 SDA(SS)  |
| GPIO 14      | —          | —          | —          | —           | RC522 RST      |
| GPIO 4       | —          | CS         | —          | —           |              |
| GPIO 13      | —          | RST        | —          | —           |              |
| GPIO 32      | —          | DC         | —          | —           |              |
| GPIO 18      | SCK        | SCK        | —          | SCK         | SPI shared     |
| GPIO 23      | MOSI       | SDA(MOSI)  | —          | MOSI        | SPI shared     |
| GPIO 19      | MISO       | —          | —          | MISO        | SPI shared     |
| GPIO 21      | —          | —          | SDA        | —           | I2C            |
| GPIO 22      | —          | —          | SCL        | —           | I2C            |
| GPIO 16      | —          | —          | —          | —           | WS2812B DIN    |
| GPIO 25      | —          | —          | —          | —           | Buzzer         |
| GPIO 26      | —          | —          | —          | —           | Button (pullup)|
| GPIO 15      | —          | —          | —          | CS          |                |

---

### [ARCHITECTURE]

```
┌─────────────────────────────────────────────────────────────┐
│                     CLIENTS                                 │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐     │
│  │   Angular    │  │   ESP32      │  │  (Future:    │     │
│  │   Web App    │  │   Terminal   │  │   Mobile)    │     │
│  └──────┬───────┘  └──────┬───────┘  └──────┬───────┘     │
│         │ JWT             │ API Key          │             │
└─────────┼─────────────────┼──────────────────┼─────────────┘
          │                 │                  │
┌─────────▼─────────────────▼──────────────────▼─────────────┐
│                   REST API LAYER                            │
│  ┌─────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐      │
│  │  Auth   │ │ Employee │ │Attendance│ │  Leave   │      │
│  │ Module  │ │  Module  │ │  Module  │ │  Module  │      │
│  └─────────┘ └──────────┘ └──────────┘ └──────────┘      │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐     │
│  │ Payroll  │ │ Reports  │ │Statistics│ │Dashboard │     │
│  │  Module  │ │  Module  │ │  Module  │ │  Module  │     │
│  └──────────┘ └──────────┘ └──────────┘ └──────────┘     │
│  ┌──────────┐ ┌──────────┐                                 │
│  │Settings  │ │  ESP32   │                                 │
│  │  Module  │ │  Module  │                                 │
│  └──────────┘ └──────────┘                                 │
└────────────────────────┬────────────────────────────────────┘
                         │ JPA / JDBC
┌────────────────────────▼────────────────────────────────────┐
│                     MySQL 8.4 LTS                           │
│  users | employees | attendance | leave_requests | settings │
└─────────────────────────────────────────────────────────────┘
```

#### Frontend Folder Structure

```
frontend/src/app/
├── core/              # Auth, models (employee.model, leave.model), guards, interceptors
├── shared/            # Reusable components, pipes, services, pdf-export.util
├── layout/            # Main layout, auth layout
├── pages/             # Login, not-found
└── features/          # Feature modules (one folder per module)
    ├── dashboard/
    ├── employees/     # Linked to API via EmployeeService
    ├── attendance/
    ├── leaves/        # Linked to API via LeaveService
    ├── payroll/
    ├── reports/
    ├── statistics/
    ├── settings/
    ├── profile/
    └── users/
```

#### Backend Folder Structure

```
backend/src/main/java/com/pointagepro/
├── config/            # SecurityConfig (JWT + /esp32/** permitAll + 401 entry point), CorsConfig
├── security/          # JWT provider, filter, user details
├── shared/            # ApiResponse, PageResponse, exceptions, utilities
├── auth/              # User entity, CurrentUserService (tenant from users.employee_id)
├── employee/          # Employee entity, EmployeeRepository
├── attendance/        # engine + services + controller/DTOs (modules 1 + 2 DONE)
│   ├── engine/        # DayCalculator pure engine (COMPLETE)
│   ├── entity/        # AttendanceEvent, AttendanceSummary, EventType, ...
│   ├── repository/    # AttendanceEventRepository, AttendanceSummaryRepository (3 entity-graph finders)
│   ├── service/       # AttendanceEngineService, AttendanceEventService, AttendanceSummaryService
│   ├── controller/    # AttendanceEventController (/attendance/events), AttendanceSummaryController (/attendance/summaries, /recompute)
│   └── dto/           # TerminalScanRequest/Response, AttendanceEventRequest/Response, AttendanceSummaryResponse, RecomputeStats, RecomputeRequest/AllRequest
├── leave/             # LeaveRequestRepository + entities + LeaveService/Controller/DTOs (module 4 DONE)
├── payroll/           # Payroll/PayrollItem/Payslip/snapshot entities + repositories + PayrollService/Controller + PayrollCalculator (module 5 DONE)
├── legal/             # CNSS/CSS/tax-bracket/SMIG/allowance rate repositories (2026 legal tables)
├── terminal/          # Terminal + TerminalStatus entities, repos, TerminalService (scan gating)
├── esp32/             # Esp32ScanController (/esp32/scan), Esp32ApiKeyService (X-API-Key)
└── (reports / statistics / dashboard / settings / logging: planned)
```

#### ESP32 Firmware Structure

```
firmware/
├── platformio.ini        # ESP32 board config, all library deps
└── src/
    ├── main.cpp           # Setup + main loop
    ├── config.h           # WiFi, API, pin definitions, colors, timing
    ├── wifi_manager.h/cpp # WiFi connect/reconnect/offline detection
    ├── rfid_reader.h/cpp  # RC522 SPI init + UID reading
    ├── api_client.h/cpp   # HTTP POST /scan + /heartbeat + offline log sync
    ├── display.h/cpp      # ST7735 TFT screens (boot/ready/success/error/offline)
    ├── feedback.h/cpp     # WS2812B LED animations + buzzer patterns
    ├── rtc_manager.h/cpp  # DS3231 RTC + NTP sync
    ├── storage.h/cpp      # SD card offline log (JSON lines)
    └── attendance.h/cpp   # Core orchestrator (boot, scan, button, heartbeat)
```

---

### [LOGGING]

- **Framework:** SLF4J + Logback (async appender)
- **Levels:** INFO, WARN, ERROR only
- **INFO:** API requests, attendance events, auth events
- **WARN:** Late check-ins, unknown RFID, validation failures
- **ERROR:** Unhandled exceptions, DB errors, ESP32 failures
- **No DEBUG/TRACE in production**

---

### [FUTURE_FEATURES]

| Feature                  | Integration Point                                      |
|--------------------------|--------------------------------------------------------|
| Fingerprint auth         | Add biometric_id to employees; ESP32 module pattern    |
| Face recognition         | Same as fingerprint; plug-and-play ESP32 module        |
| Door access control      | Add access_level to employee; ESP32 relay control      |
| Mobile application       | REST API already supports it; add push notification    |
| Push notifications      | Add notification_events table; Spring event system     |
| Multi-branch support     | Add branches table; branch_id FK to relevant tables    |

---

### [MILESTONES]

| #  | Milestone                          | Duration | Success Criteria                                  | Status    |
|----|------------------------------------|----------|---------------------------------------------------|-----------|
| 1  | Foundation & Authentication        | 3 days   | Admin login works, JWT, layout renders             | COMPLETED |
| 2  | Employee Management                | 3 days   | CRUD works, search, pagination, RFID assignment    | COMPLETED |
| 3  | Leave Management (Web)            | 2 days   | List with filters, manual entry, status display    | COMPLETED |
| 4  | Leave Management (Backend)        | 1 day    | Leave CRUD API, balance, approve/refuse, seeds    | COMPLETED |
| 5  | Payroll & Dashboard               | 2 days   | Payroll summary correct, dashboard shows metrics  | COMPLETED |
| 6  | Reports & Statistics              | 2 days   | Reports generate, charts display correctly         | COMPLETED |
| 7  | Settings, Users & Polish          | 2 days   | Settings editable, roles enforced, validation works | COMPLETED |
| 8  | ESP32 Firmware & Attendance Core  | 4 days   | RFID scan → attendance record, feedback on TFT    | COMPLETED |
| 9  | Quick Wins (2FA, Sessions, Email, Prefs) | 1 day | 2FA, sessions, login history, email, notif prefs | COMPLETED |

**Total:** ~20 working days

---

### [ORPHANS & PENDING]

**Backend-frontend linking completed:**
- Auth (JWT + interceptor)
- Employee CRUD (API ↔ frontend)
- Leave Requests (CRUD, allocation-based balance, carry-over, approve/refuse/reset, seeds)
- Attendance (check-in/out, summary, CSV→PDF export)
- Payroll (auto-generation, cascade, payslip PDF, status filters)
- Reports (monthly/annual with PDF export)
- Dashboard (stats, rolling 7-day chart)
- Settings (single-row schema, merged Horaires & Paie card)

**Still pending:**
- ESP32 firmware: scaffolded (`firmware/`), awaiting hardware wiring (WS2812B LEDs, button, SD card)
- ESP32 backend scan flow: **DONE (module 1)** — `POST /esp32/scan` with shared-key auth (`X-API-Key`), terminal resolution from `externalRef`, server-side IN/OUT alternation, replay/duplicate dedup, recompute → summary (verified end-to-end 2026-08-05). Still pending: `/esp32/heartbeat` + firmware version check + enrollment (module 6)
- Attendance summaries & recompute API: **DONE (module 2)** — `GET /attendance/summaries`, `/summaries/{id}`, `/summaries/day` + `/today` (compute-on-miss `api:day`/`api:today`), `POST /attendance/recompute` + `/recompute/all` (authority `attendance.recalculate`, reason ≤255, 366-day span cap), tenant 404, entity-graph reads; verified end-to-end 2026-08-05 (68 tests green). **Adjustments API + approval workflow: DONE (module 3)** — `/attendance/adjustments` create/list/`{id}`/`pending`/approve/reject/cancel, frozen-period 409, 1440-cap dry-run 400, immediate apply on empty chain, apply → engine recompute `adjustment:<id>`, optimistic locking, audit trail (V11–V13, 100 tests green, E2E verified 2026-08-05). Payroll snapshot consumption: **DONE (module 5)** — `payroll_attendance_snapshots` frozen per-day facts consumed by `PayrollService.compute`; monthly report binding still pending (frontend)
- **Leave management + approval workflow + balance: DONE (module 4)** — `/leaves` create/list/`{id}`/`pending`/approve/reject/cancel + `/leaves/balance/{employeeId}?year=`, 2-step chain (MANAGER → HR), overlap 409, cross-year per-year balance debit at approval (auto-provision tracked rows) + `leave_balance_logs`, refund on HR/ADMIN cancel of APPROVED + `leave-cancel:{id}` recompute, frozen-month 409, optimistic locking, audit trail, ADMIN universal approver (V14, 138 tests green, live E2E verified 2026-08-06). Still pending: frontend binding, payroll consumption of leave (module 5 partially — `is_paid_leave` snapshot column added in V15, leave-typed day resolution wired)
- Notification system (COMPLETE)
- 2FA + Sessions + Login History + Email + Notification Preferences (COMPLETE)
- RTC timezone fix (NTP time still -1h from real Tunisia time)

**DB Migrations:** V1–V15 all applied
- Fingerprint authentication
- Face recognition
- Door access control
- Mobile application
- Push notifications
- Multi-branch support
- API documentation (Swagger/OpenAPI)

### [VERSION_NOTES]

- **Module 5 FROZEN 2026-08-06 — full verification passed:** `mvn clean verify` BUILD SUCCESS (183 tests, 0 failures/errors/skipped); fresh startup clean (Flyway V1–V15 `pointagepro` up to date, 3 benign warnings: Hibernate 5.5.5 dialect note, explicit-dialect hint, open-in-view); Payroll E2E 71/71 checks; cross-module regression probes Attendance 33/33, Adjustments 38/38, Leave 69/69 (all against MySQL); DB integrity verified (runs/items/components/snapshots/payslips/audit/orphans consistent; totals rounding note: independently-rounded stored parts can sum to gross ±0.01, e.g. 1500.01 vs 1500.00 — expected, net uses unrounded intermediates); all E2E data cleaned, DB restored to post-migration state (admin only, hash `$2b$10$8kLglwtaF9prVKsB51iQtuCyVVFBdXZDW3iF02TY3yaoqZmVHxkyy`). Tagged `module-5`. Runtime quirk: only fixed context-path mappings (`/payrolls`, `/payslips`) resolve; pre-fix double-prefixed `/api/v1/...` URLs 404.
- **Module 5 (payroll runs + payslips) 2026-08-06:** `/payrolls` (context-path `/api/v1` applies globally, so controllers map at `/payrolls`/`/payslips`) — `POST` create (`payroll.run`; 201, idempotent reopen of same DRAFT/COMPUTED/CANCELLED period returns the existing run; period in the past or future allowed at create), `POST /{id}/compute` (2026 legal math: CNSS 9.68%/16.57%, IRPP progressive brackets via `legal.tax_brackets`, CSS 0.5%/1.0%, SMIG 524.954 floor checks; `payroll_attendance_snapshots` frozen per-day facts incl. `is_paid_leave`; workdays = attendance rows, weekends at 0 min; French messages, totals `gross/cnss/irpp/css/deductions/net`, `employeeCount`, warnings e.g. family allowance 0.00/child; future period → 409 "futur"), `POST /{id}/validate` (`payroll.validate`), `POST /{id}/approve` (`payroll.approve`; generates payslips `PP-YYYYMM-NNN`), `POST /{id}/pay` (body `bankTransferRef`, writes `paid_at`, audit PAYROLL_PAY), `POST /{id}/cancel` (only DRAFT/COMPUTED; CANCELLED → artifacts cleared, period re-creatable → reopened DRAFT), `GET /{id}`, `GET /{id}/items`, `GET /{id}/payslips`, `GET /payslips/{id}` (`payslip.read`). Frozen states VALIDATED/APPROVED/PAID are immutable → 409 on compute/validate/approve/pay/cancel/create; frozen month also blocks `attendance.adjustments` + leave transitions (shared `PayrollAttendanceSnapshotRepository.isMonthFrozen`). `@Version` optimistic locking on `Payroll` (race → 409). New: `payroll` package (entity/repository/service/controller/dto), `legal` package (rates/brackets repositories), `PayrollCalculator` engine with rounding rules; migration **V15** (`payrolls.version`, `payroll_attendance_snapshots.is_paid_leave`, `salary_component_types` css flags, indexes). 183 tests green; live E2E (71 checks) verified against MySQL (create/compute/validate/approve/pay, payslip PP-202607-001 net 1207.71 from 1500.00 base, frozen 409s, future-period 409, cancel→reopen, permission 403s, cross-module frozen guard, DB integrity). **Fixed during live E2E:** controllers declared `/api/v1/payrolls` on top of context-path `/api/v1` → effective `/api/v1/api/v1/...` (Spring threw `No static resource payrolls`) → mappings reduced to `/payrolls`/`/payslips`.
- **Module 4 (leave management + approval + balance) 2026-08-06:** `/leaves` — `POST` create (`leave.write`; MANAGER granted it in V14), `GET` list (filters employeeId/statusCode/from/to), `GET /{id}`, `GET /pending` (only requests whose current pending step the caller can decide), `POST /{id}/approve|reject` (optional comment), `POST /{id}/cancel` (`leave.approve` **or** `leave.write` — the `leave.write` path is the requester withdrawal of their own `PENDING` request; service denies everyone else), `GET /balance/{employeeId}?year=`. Chain materialized at creation (`approvals`, `LEAVE`): step 1 MANAGER = requester's department manager (skipped when none / requester is the manager / no active account), step 2 HR (always present, never auto-decided — an HR requester needs another HR). Requester never decides own steps; ADMIN is a universal approver (except own requests). `daysRequested` server-computed (weekdays − company holidays). Overlap 409 (PENDING+APPROVED); span >366 days 400; no working days → 400; no balance check at create. Approve: frozen-month 409 → per-step approval → on last step, same-transaction per-year balance debit (tracked types = `default_days_per_year IS NOT NULL`; auto-provisions missing rows with the type default + audit; `leave_balance_logs` row per year, ref `LEAVE:<id>`) + engine recompute `leave:<id>`; insufficient balance → dry-run 400 and the request stays PENDING. Reject: `REJECTED` + `rejectedReason`, all remaining steps rejected, never debits. Cancel: creator-while-PENDING or HR/ADMIN (PENDING or APPROVED); APPROVED-cancel → frozen guard 409 first, then same-transaction refund + recompute `leave-cancel:<id>`. Terminal states immutable → 409. `@Version` optimistic locking on `LeaveRequest` + `LeaveBalance`. Migration **V14** (`version` columns, `leave_balance_logs.operation`, overlap index `idx_leave_requests_overlap`, MANAGER `leave.write`). New: `leave` package (entity/repository/service/controller/dto), shared `ApprovalAuthority` + shared `ApprovalStepResponse` (extracted from attendance, applied to both workflows — ADMIN universal approver/creator aligned on adjustments too). 138 tests green; live E2E (21 scenarios) verified against MySQL. **Fixed during live E2E:** cancel endpoint only required `leave.approve`, blocking requester withdrawal (USER lacks that authority) → relaxed to `leave.approve or leave.write`.
- **Module 3 (attendance adjustments + approval workflow) 2026-08-05:** `/attendance/adjustments` — `POST` create (`attendance.adjust`), `GET` list (filters employeeId/statusCode/from/to), `GET /{id}`, `GET /pending` (only steps the caller can decide), `POST /{id}/approve|reject` (optional comment), `POST /{id}/cancel` (creator-while-PENDING or HR; reason required). Chain materialized at creation (`approvals`, `ATTENDANCE_ADJUST`): step 1 MANAGER = target's department manager (skipped when none / target is manager / creator is manager), step 2 HR (auto-decided by the creator when the creator is HR); creator and target can never decide a step; empty chain → created directly `APPLIED` in the same transaction. Last approval → `APPLIED` + same-transaction single-day engine recompute with `recompute_reason = "adjustment:<id>"`; `summary_id` attached by the engine, never by the client. Frozen month (`payrolls` VALIDATED/APPROVED/PAID) → 409; 1440-min daily cap → dry-run 400 at approve (engine clamp stays the backstop); terminal states (`APPLIED`/`REJECTED`/`CANCELLED`) immutable → 409. `@Version` optimistic locking on `AttendanceAdjustment` + `Approval` (race → 409); every transition audit-logged via new `com.pointagepro.audit` package. Migrations **V11** (`CANCELLED` statuses), **V12** (`version` columns), **V13** (`attendance_adjustments.work_date`). New: `AttendanceAdjustmentService`, `AttendanceAdjustmentController`, adjustment DTOs, `AuditService`/`AuditLog`, `ConflictException`. Fixed during live E2E: native `isMonthFrozen` Boolean-cast crash (MySQL returns Integer for `CASE WHEN…THEN TRUE`) → count + default method; cap dry-run never fired (engine clamps `workedMinutes`) → new `DayResult.rawWorkedMinutes` pre-clamp value checked in the service. 100 tests green; full E2E verified against MySQL (create PENDING, cross-dept + creator 403, manager approve → APPLIED + recompute, terminal 409, over-cap 400 stays PENDING, reject, cancel, frozen 409, audit rows).
- **Module 2 (attendance API — summaries, day status, recompute) 2026-08-05:** `GET /attendance/summaries` (list, employeeId required, from/to optional 30d-back → today), `GET /attendance/summaries/{id}` (tenant 404), `GET /attendance/summaries/day` + `/today` (compute-on-miss with reasons `api:day`/`api:today`), `POST /attendance/recompute` (per-employee, reason ≤255 default `api:recompute`) + `POST /attendance/recompute/all` (company-wide, returns `RecomputeStats`); reads gated by `attendance.read`, recomputes by `attendance.recalculate` (seeded on HR). New: `AttendanceSummaryService`, `AttendanceSummaryController`, DTOs (`AttendanceSummaryResponse` with `@JsonFormat HH:mm`, `RecomputeStats`, `RecomputeRequest/AllRequest`), 3 entity-graph repository finders (inline `attributePaths` array). Fixed two live bugs: Hibernate `Unable to locate Attribute` (attributePaths was one String, not String[]) and `entityEntry is null` NPE during post-write entity-graph reads (detached `computedBy` user → resolved via `UserRepository.getReferenceById` in the engine transaction). 68 tests green; full E2E verified against MySQL (list, single, day compute-on-miss, today, per-employee recompute, recompute/all, 400 validation).
- **Module 1 (attendance API — event intake) 2026-08-05:** `ATTENDANCE_API_CONTRACT.md` created; `/esp32/scan` (flat firmware-shaped response, X-API-Key auth, terminal resolution from `externalRef`, type-less punch alternation IN/OUT, 60 s type-agnostic duplicate window, externalRef replay dedup, company-consistency check); `/attendance/events` POST (`attendance.write`) + GET (`attendance.read`) with JWT + method security (`@EnableMethodSecurity`); `SecurityConfig` gained a 401 JSON entry point for unauthenticated staff requests. New packages: `terminal` (entity/repo/service), `esp32` (controller/key service), `attendance.controller`, `attendance.dto`. 44 tests green; boot clean (Flyway V1–V10, Hibernate validate); device flow verified against MySQL (IN→OUT→replay, summary computed PRESENT/480).
- **Angular:** Using 19.2.x instead of 22.x because system Node.js is v18 (Angular 22 requires Node 22+). Upgrade Node to v22+ to use Angular 22.
- **Spring Boot:** Using 3.4.5 (latest stable with full ecosystem support).
