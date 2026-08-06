-- PointagePro - V10 : snapshot the schedule values actually used by each day
--
-- payroll_attendance_snapshots is the frozen fact-basis of a payroll run; it must be
-- self-contained. The freeze step copies the resolved work-schedule line's values
-- (scheduled_start_time / scheduled_end_time / scheduled_break_minutes) so the run
-- remains reproducible even if the schedule definition changes later. schedule_id is
-- kept for provenance only.

ALTER TABLE payroll_attendance_snapshots
    ADD COLUMN scheduled_start_time TIME NULL AFTER schedule_id,
    ADD COLUMN scheduled_end_time TIME NULL AFTER scheduled_start_time,
    ADD COLUMN scheduled_break_minutes INT NOT NULL DEFAULT 0 AFTER scheduled_end_time;
