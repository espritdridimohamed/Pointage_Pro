-- =====================================================================
-- PointagePro - V12 : optimistic locking for the adjustment workflow
-- (module 3, business rules §5.2). Guarantees "decide once": concurrent
-- approve/reject/cancel attempts fail with an optimistic-lock conflict
-- instead of double-applying a decision.
-- =====================================================================

ALTER TABLE attendance_adjustments ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE approvals ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
