-- =====================================================================
-- PointagePro - V11 : CANCELLED terminal state for the adjustment workflow
-- (module 3, business rules §5.6). A PENDING adjustment may be cancelled
-- by its creator or by HR; the pending approval steps transition to
-- CANCELLED as well so they never appear in approval queues.
-- =====================================================================

INSERT INTO adjustment_statuses (code, label) VALUES ('CANCELLED', 'Cancelled');
INSERT INTO approval_statuses (code, label) VALUES ('CANCELLED', 'Cancelled');
