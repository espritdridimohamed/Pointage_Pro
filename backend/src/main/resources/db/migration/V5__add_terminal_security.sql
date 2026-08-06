-- =====================================================================
-- PointagePro - V5 : Terminal security + firmware + diagnostics
-- 1) terminals : per-device API key + enrollment activation code (hashed)
-- 2) attendance_events : time-drift flag + DB-level replay dedup
-- 3) terminal_firmware_versions : release management
-- 4) terminal_logs : device diagnostics
-- =====================================================================

-- ---------------------------------------------------------------------
-- Per-device credentials (hashed). api_key_hash set at enrollment;
-- activation_code_hash used to enroll a factory-reset device.
-- ---------------------------------------------------------------------

ALTER TABLE terminals
    ADD COLUMN api_key_hash VARCHAR(100) NULL,
    ADD COLUMN activation_code_hash VARCHAR(100) NULL;

-- ---------------------------------------------------------------------
-- attendance_events : flag when device timestamp deviates from server
-- time beyond the allowed drift (24h, flagged not rejected), and
-- guarantee at DB level that a terminal never inserts the same scan twice.
-- ---------------------------------------------------------------------

ALTER TABLE attendance_events
    ADD COLUMN time_warning TINYINT(1) NOT NULL DEFAULT 0,
    ADD UNIQUE KEY uk_events_terminal_ref (terminal_id, external_ref);

-- ---------------------------------------------------------------------
-- Firmware release management (check endpoint returns current version)
-- ---------------------------------------------------------------------

CREATE TABLE terminal_firmware_versions (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    version VARCHAR(20) NOT NULL UNIQUE,
    released_date DATE NOT NULL,
    is_mandatory TINYINT(1) NOT NULL DEFAULT 0,
    download_url VARCHAR(255),
    notes VARCHAR(500),
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- ---------------------------------------------------------------------
-- Terminal diagnostics log (SD failure, RFID error, reboot, low memory)
-- ---------------------------------------------------------------------

CREATE TABLE terminal_logs (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    terminal_id BIGINT NOT NULL,
    level VARCHAR(10) NOT NULL,
    message VARCHAR(500) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_tlog_terminal FOREIGN KEY (terminal_id) REFERENCES terminals(id) ON DELETE CASCADE,
    INDEX idx_tlog_terminal_time (terminal_id, created_at)
);
