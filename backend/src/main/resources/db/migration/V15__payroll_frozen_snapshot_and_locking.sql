-- =====================================================================
-- PointagePro - V15 : Payroll module 5 (frozen snapshot + locking)
-- 1) payroll_attendance_snapshots.is_paid_leave - the freeze step resolves,
--    at run time, whether each LEAVE-status day was covered by a PAID leave
--    type (leave_types.is_paid) and persists it, so the snapshot stays
--    self-contained and unpaid-leave days are deducted.
-- 2) payrolls.version - optimistic locking on lifecycle transitions
--    (module 3/4 precedent: V12/V14), mapped to 409 on concurrent edits.
-- 3) CSS flag alignment: base salary and overtime are subject to the 2026
--    CSS levy; the V3 default seeded them 0.
-- =====================================================================

ALTER TABLE payroll_attendance_snapshots
    ADD COLUMN is_paid_leave TINYINT(1) NOT NULL DEFAULT 0 AFTER status_id;

ALTER TABLE payrolls
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

UPDATE salary_component_types
   SET is_subject_to_css = 1
 WHERE code IN ('BASE_SALARY', 'HEURES_SUP');
