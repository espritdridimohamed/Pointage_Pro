# PointagePro — Terminal (ESP32) Communication Rules

**Version:** 2.1
**Scope:** Contract between the ESP32 attendance terminals (`firmware/`) and the backend (`com.pointagepro.terminal`, `attendance_events`).
**Basis:** firmware v2 (`api_client.cpp`, `attendance.cpp`, `storage.cpp`, `rtc_manager.cpp`, `config.h`) and schema `terminals`, `terminal_statuses`, `terminal_logs`, `terminal_firmware_versions`, `attendance_events`.
**Changes in 2.0:** per-device API keys + enrollment (was shared key), collision-proof `external_ref` with persistent scan counter, DB-level dedup, time-drift flagging, heartbeat scheduler, firmware-update registry, terminal logs.
**Changes in 2.1:** future-ready device provisioning design (§10, implementation deferred), `terminal_logs.category` for queryable production diagnostics (§11).

**Module-1 implemented state (2026-08-05) — read this first:** the live `firmware/` still uses the **shared key** (`app.esp32.api-key`, header `X-API-Key`), sends **no `deviceId`** in `POST /esp32/scan`, and the backend derives the terminal serial from `externalRef` (`ESP32-001-00000045` → serial `ESP32-001`). Per-device keys + `/esp32/enroll` remain §10 design (module 6). The rest of this document's rules (replay dedup, offline buffering, IN/OUT on server) are implemented and verified.

---

## 1. Transport

- **HTTP/JSON** over the local network (LAN), base URL from device provisioning.
- Every request carries header **`X-API-Key`** = the **device's own** secret, issued at enrollment (never a shared factory key).
- Request timeout **5 s**. No TLS in v1 (LAN + per-device key); TLS recommended for WAN/cloud.

## 2. Endpoints (backend must implement)

### 2.1 `POST /api/v1/esp32/enroll` — first boot / factory reset
Request body:
```json
{ "serialNumber": "ESP32-001", "activationCode": "A8K2D9", "macAddress": "A4:CF:12:F5:11:22", "firmwareVersion": "1.0.0", "model": "PointagePro-1.8" }
```
Response:
```json
{ "success": true, "apiKey": "<random-secret>", "terminalCode": "TERM-01", "serverTime": "2026-08-05T08:00:00" }
```
Rules:
- Backend finds the pre-registered `terminals` row by `serial_number`, compares the activation code hash, generates a **random per-device API key**, stores only its **hash** (`api_key_hash`), sets `enrolled_at`, returns the key **once**.
- Device persists the key in **NVS flash** and uses it from then on as `X-API-Key`.
- Wrong/expired code or unknown serial → `success:false`; device stays in "not enrolled" state.
- Re-enrollment (factory reset) uses a **new activation code** issued by the admin UI.

### 2.2 `POST /api/v1/esp32/scan`
Request body:
```json
{ "rfidUid": "84:F5:E1:06", "externalRef": "ESP32-001-00000045", "timestamp": "2026-08-05T08:02:00" }
```
- `externalRef` is **always** sent (live and replay). Format: `{serialNumber}-{scanCounter:08d}` — e.g. `ESP32-001-00000045` (§4.1).
- `timestamp` present on replay (device-authoritative); absent on live scan (server time).
- Unknown `deviceId`/bad key → `success:false`.

Response:
```json
{ "success": true, "action": "IN", "employeeName": "Ali Ben Ali", "matricule": "E001", "message": "Bonjour Ali", "time": "08:02" }
```
- The **server** decides IN vs OUT; the device never decides. Rule (implemented, module 1, 2026-08-05): **alternation against the employee's most recent stored event** — a type-less punch is `OUT` when the last event is `IN`, else `IN`. This handles night shifts (IN 22:00 → OUT 06:00 of the next day); a pure "per calendar day" toggle would misclassify the 06:00 checkout. Detail: ATTENDANCE_API_CONTRACT §2.1 / ATTENDANCE_BUSINESS_RULES §2.8.
- `time` is `HH:mm` (24 h) of the stored event.
- Unknown UID → `success:false`; device marks the log synced (skip) so it never retries an unenrollable card.

### 2.3 `POST /api/v1/esp32/heartbeat`
Request body:
```json
{ "deviceId": "ESP32-001", "ipAddress": "172.20.10.8", "rssi": -52, "firmwareVersion": "1.0.0", "freeMemory": 210000, "uptimeSeconds": 86400 }
```
- Sent every **60 s**. Server updates `last_heartbeat_at`, `last_ip`, `firmware_version`.

## 3. Online scan flow
1. Card presented → UID read (MFRC522, 300 ms debounce + anti-collision).
2. Device consumes the next scan counter (§4.1) and sends `POST /esp32/scan` with `rfidUid` + `externalRef` (no timestamp).
3. Server stores `attendance_events` (`source='TERMINAL'`, `event_time` = server time, `external_ref` = the one received), toggles IN/OUT, returns action.
4. Device shows success/error; LED + buzzer feedback. Nothing is stored on the device online.

## 4. Offline mode (internet / server down)

### 4.1 Scan counter (collision-proof `external_ref`)
- Device keeps a **monotonic `uint32_t` scan counter** persisted in **NVS flash** (ESP32-native persistent storage, the successor to EEPROM).
- Consumed **once per scan** (online or offline); survives reboots; never reused.
- `externalRef = {serialNumber}-{counter:08d}` → `ESP32-001-00000045`. Two scans in the same second can never collide (unique counter), and the ref is stable across replays (the same counter is stored in the offline log entry).
- NVS endurance: ~100 k write cycles, wear-leveled; one write per scan is acceptable for a card terminal; a diagnostic warning is logged near the limit.

### 4.2 Local buffering
- Offline scans are appended to the SD card log as JSON lines:
  `{"uid":"84:F5:E1:06","ts":"2026-08-05T08:02:00","ref":"ESP32-001-00000045","synced":false}`
- **Cap:** at most **2 pending scans per UID** (`canLog`) — rejects card spam; further scans show "Deja pointe".
- Offline scans carry the **device RTC timestamp** (DS3231 battery-backed).

### 4.3 Reconnection and replay
1. On reconnect the device waits 3 s, then **NTP-syncs the RTC**.
2. Replays pending logs **in FIFO order** via `POST /esp32/scan` **with the stored `timestamp` and `externalRef`** (events keep their real recorded time).
3. Server is **idempotent on `(terminal_id, external_ref)`** — enforced at the DB level by `UNIQUE KEY uk_events_terminal_ref`. A replayed scan that already exists returns the original result; no duplicate is ever inserted.
4. On `success` → mark `synced:true`. On definitive rejection (unknown UID, duplicate) → mark synced (skip). On timeout/offline → retry **3 times, 2 s apart**, then stop and leave pending.
5. `clearSynced()` drops `synced:true` lines; pending lines remain for the next reconnect.

### 4.4 Data integrity
- A scan is **never dropped** on the device: it either reaches the server or stays pending on SD.
- SD failure → device refuses offline scans (error) rather than recording nothing silently.

## 5. Duplicate prevention (three layers)

| Layer | Mechanism | Guarantee |
|---|---|---|
| Device | 300 ms debounce + monotonic counter + 2-pending-per-UID cap | same-card spam and same-instant collisions impossible |
| Application | idempotency on `(terminal_id, external_ref)` | replay returns existing result |
| **Database** | `UNIQUE KEY uk_events_terminal_ref (terminal_id, external_ref)` | **even a buggy client cannot double-insert** |

The 60 s same-type window (attendance engine) is *separate* logic — a legitimate second IN after 60 s becomes the day's new first/last IN.

## 6. Time synchronization and drift
- **DS3231 RTC** keeps time; **NTP sync** on every (re)connect, before replay. Timezone **Africa/Tunis** (fixed, no DST).
- Trust model:
  - Live online scan → `event_time` = **server time**.
  - Offline replay → `event_time` = **device-recorded timestamp** (the physical moment is the truth).
- **Drift guard:** if `|device_timestamp − server_now| > 24 h`, the server does **not** reject the event — it stores it and sets **`attendance_events.time_warning = 1`** (surfaced in UI/reports as a suspicious scan). Prevents a tampered RTC from silently creating fake overtime.
- RTC drift itself (~≤1 min/month) is corrected by NTP on reconnect and does not trigger the warning.

## 7. Heartbeat & terminal status (scheduled)
- Heartbeat every 60 s while online; none while offline.
- **Server scheduler** (`@Scheduled(fixedRate = 60000)` → `checkTerminals()`):
  - every minute, `terminals` whose `last_heartbeat_at < now − 180 s` are marked `OFFLINE` (status_id);
  - a new heartbeat restores `ONLINE`;
  - `MAINTENANCE` / `DISABLED` are manual overrides, never auto-cleared.
- Scheduler also emits `terminal_logs` rows for status transitions and low-memory warnings.

## 8. Security
- **Per-device API key** issued at enrollment; stored server-side only as a **hash** (`terminals.api_key_hash`). A leaked key compromises exactly one terminal and is rotated by re-enrollment — it does **not** compromise the fleet (v1's shared `X-API-Key` is removed in favor of this).
- Activation codes stored hashed (`activation_code_hash`), single-use per enrollment.
- `/esp32/*` rejects requests with unknown/bad keys; rate-limited per device.
- **Known debt (fix in firmware hardening):** `config.h` still hardcodes Wi-Fi SSID/password; these move to provisioning/NVS. No secrets are committed to the repo in the final firmware.
- Payload size/JSON: scan/heartbeat bodies are small; responses parsed with `ArduinoJson`.

## 9. Firmware update management
- `terminal_firmware_versions` registry: `version`, `released_date`, `is_mandatory`, `download_url`, `notes`.
- Backend exposes the current target version (e.g. in the heartbeat response); device compares to its `firmware_version` and shows an update prompt (mandatory versions block idle operation until updated).
- Update delivery is **out of band** (admin flashes / OTA tool); the registry exists to track rollout, not to stream binaries.

## 10. Device provisioning & credential storage (future-ready design)

Implementation status: **designed, not yet built** — part of the firmware hardening backlog. The schema (`terminals.api_key_hash`, enrollment endpoint §2.1) already supports it; the firmware still uses compile-time `config.h` credentials (WiFi SSID/password, base URL).

### 10.1 Principles
- **No secrets compiled into firmware.** No `#define WIFI_SSID` / `WIFI_PASSWORD` / `API_KEY`. The released binary is treated as public.
- **Credentials live in NVS flash** (the same store as the scan counter, §4.1): WiFi SSID/password, base URL, and the per-device `apiKey` issued at enrollment.
- **First-boot state = unprovisioned.** The device boots with no credentials and no working network.

### 10.2 Provisioning flow (target)
1. **Admin** pre-registers the terminal in the UI: `serial_number`, model, and a generated **activation code** (stored hashed). WiFi SSID/PSK + base URL may also be seeded via the UI.
2. **Physical setup** (two supported paths):
   - *Setup AP (recommended):* device starts a captive portal on first boot (SSID `PointagePro-Setup-XXXX`); the technician enters WiFi credentials + activation code on a phone; the device validates the code, joins the network, then enrolls.
   - *Serial console:* same inputs over UART.
3. **Enrollment** (`POST /api/v1/esp32/enroll`, §2.1): validates the activation code and returns the per-device `apiKey`.
4. **Persist to NVS:** WiFi credentials + base URL + apiKey. Subsequent boots skip provisioning and go straight to online mode.
5. **Factory reset** (button hold or re-provision) wipes NVS credentials → the device returns to setup mode with a fresh activation code.

### 10.3 NVS layout (reserved namespaces)
| Namespace | Keys |
|---|---|
| `wifi` | `ssid`, `password` |
| `net` | `baseUrl` |
| `dev` | `apiKey`, `terminalCode`, `enrolledAt` |
| `cnt` | `scanSeq` (existing, §4.1) |
| `prov` | `done` (provisioning flag) |

### 10.4 Security notes
- NVS is not encrypted at rest on the ESP32 by default; a stolen device must be wiped before redeployment (NVS erase + re-enrollment rotates the key).
- The raw activation code and API key are never written to the SD log and are never re-sent after enrollment.

## 11. Terminal diagnostics
- `terminal_logs` (`terminal_id`, `category`, `level` = INFO/WARN/ERROR, `message`, `created_at`).
- **`category`** (`VARCHAR(30)`, queryable) groups events so production issues are filterable: `CONNECTION`, `QUEUE`, `REPLAY`, `HEARTBEAT`, `SD`, `RFID`, `BOOT`, `MEMORY`, `NVS`, `ENROLLMENT`, `SYSTEM`.
- Events the device must push (piggybacked on the heartbeat, or a dedicated diagnostics endpoint):
  - **CONNECTION** — WiFi association failures, HTTP connect timeouts, HTTP non-2xx on `/esp32/*`.
  - **QUEUE** — offline log append, queue size reaching the 2-pending-per-UID cap (rejections), SD full/failure.
  - **REPLAY** — result of each replay batch: `n_synced`, `n_skipped`, `n_failed`; retry exhaustion (3×2 s) leaving items pending.
  - **HEARTBEAT** — heartbeat HTTP failures; server `OFFLINE` transitions are logged server-side.
  - **SD** — init failure, write/read errors, `clearSynced()` runs.
  - **RFID** — read errors / anti-collision failures.
  - **BOOT** — ESP32 restart reason (crash recovery) on next boot.
  - **MEMORY** — low free heap warnings.
  - **NVS** — counter near the endurance limit / write failure.
  - **ENROLLMENT** — enroll success, wrong code, repeated attempts.
- Server side also logs status transitions (ONLINE/OFFLINE) and enrollment attempts.
- These are debugging aids — never used for payroll inputs.

## 12. Open decisions to confirm
1. **Scan counter storage:** NVS (ESP32-native, recommended) vs raw EEPROM library. Confirm NVS.
2. **`external_ref` width:** `{serialNumber}-{counter:08d}` fits `VARCHAR(50)`; counter wraps after 2^32 scans (never in practice). Confirm format.
3. **Drift threshold:** 24 h for `time_warning`. Confirm.
4. **Enrollment UX:** admin pre-creates terminal + activation code in UI (recommended) vs self-service device-initiated creation. Confirm pre-registration.
5. **Offline heartbeat:** none sent while offline; `OFFLINE` after 180 s. Confirm.
6. **Firmware OTA:** registry + out-of-band flashing in v1 (no OTA streaming). Confirm.
7. **TLS:** deferred to WAN deployment. Confirm LAN-only for v1.
8. **Setup path:** captive portal (recommended) vs serial console for the provisioning flow in §10.2.
