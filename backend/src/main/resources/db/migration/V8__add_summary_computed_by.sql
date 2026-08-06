-- =====================================================================
-- PointagePro - V8 : attendance_summary audit - who triggered recompute
-- (confirmed review point 12). The engine sets computed_by_user_id on
-- every recompute; NULL when triggered by the system (e.g. scheduled).
-- =====================================================================

ALTER TABLE attendance_summary
    ADD COLUMN computed_by_user_id BIGINT NULL AFTER recompute_reason;

ALTER TABLE attendance_summary
    ADD CONSTRAINT fk_summary_computed_by
        FOREIGN KEY (computed_by_user_id) REFERENCES users(id) ON DELETE SET NULL;
