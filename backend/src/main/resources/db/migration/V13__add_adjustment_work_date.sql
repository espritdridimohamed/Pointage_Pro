-- =====================================================================
-- PointagePro - V13 : attendance_adjustments.work_date
-- (module 3). An adjustment targets one day; while PENDING it has no
-- summary yet, so the day must be stored directly for frozen-period
-- checks, dry-run validation and the recompute-on-apply trigger.
-- =====================================================================

ALTER TABLE attendance_adjustments ADD COLUMN work_date DATE NOT NULL;
