CREATE TABLE terminal_status (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    device_id VARCHAR(50) NOT NULL,
    device_name VARCHAR(100) DEFAULT 'ESP32 Terminal',
    ip_address VARCHAR(45),
    rssi INT,
    firmware_version VARCHAR(20),
    free_memory INT,
    uptime_seconds BIGINT DEFAULT 0,
    scans_today INT DEFAULT 0,
    last_heartbeat DATETIME,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_terminal_device_id (device_id)
);

CREATE TABLE scan_events (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    device_id VARCHAR(50),
    rfid_uid VARCHAR(50),
    employee_id BIGINT,
    employee_name VARCHAR(200),
    matricule VARCHAR(50),
    action VARCHAR(20),
    scanned_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);
