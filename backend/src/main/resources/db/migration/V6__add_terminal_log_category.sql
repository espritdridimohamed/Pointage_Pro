-- =====================================================================
-- PointagePro - V6 : terminal_logs category for diagnostics
-- Queryable category (CONNECTION/QUEUE/REPLAY/HEARTBEAT/SD/RFID/BOOT/
-- MEMORY/NVS/ENROLLMENT/SYSTEM) so production issues are filterable.
-- =====================================================================

ALTER TABLE terminal_logs
    ADD COLUMN category VARCHAR(30) NOT NULL DEFAULT 'SYSTEM' AFTER terminal_id;
