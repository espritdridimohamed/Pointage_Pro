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
├── config/            # SecurityConfig, CorsConfig
├── security/          # JWT provider, filter, user details
├── shared/            # ApiResponse, exceptions, utilities
├── auth/              # AuthController, AuthService, User entity
├── employee/          # EmployeeController, Service, Repository, Entity, DTOs
├── attendance/        # AttendanceController, Service, Repository, Entity, DTOs
├── leave/             # LeaveRequestController, Service, Repository, Entity, DTOs (COMPLETE)
├── payroll/           # PayrollController, Service, DTOs
├── reports/           # ReportController, Service
├── statistics/        # StatisticsController, Service
├── dashboard/         # DashboardController, Service, DTOs
├── settings/          # SettingsController, Service, Repository, Entity
├── esp32/             # Esp32Controller, Service, DTOs
└── logging/           # AsyncLogger (SLF4J + Logback)
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
- ESP32 backend: `Esp32Controller` + DTOs created, API key auth via `X-API-Key` header
- Notification system (COMPLETE)
- 2FA + Sessions + Login History + Email + Notification Preferences (COMPLETE)
- RTC timezone fix (NTP time still -1h from real Tunisia time)

**DB Migrations:** V1–V24 all applied
- Fingerprint authentication
- Face recognition
- Door access control
- Mobile application
- Push notifications
- Multi-branch support
- API documentation (Swagger/OpenAPI)

**DB Migrations:** V1–V19 all applied

### [VERSION_NOTES]

- **Angular:** Using 19.2.x instead of 22.x because system Node.js is v18 (Angular 22 requires Node 22+). Upgrade Node to v22+ to use Angular 22.
- **Spring Boot:** Using 3.4.5 (latest stable with full ecosystem support).
