-- =====================================================================
-- PointagePro - V14 : optimistic locking + audit classification for the
-- leave module (module 4, LEAVE_BUSINESS_RULES.md §11). Guarantees
-- "decide once" on leave status transitions and race-safe balance
-- debit/refund rows; tags leave_balance_logs rows by operation so the
-- balance audit trail is queryable; adds an overlap index backing the
-- PENDING+APPROVED overlap guard; grants leave.write to MANAGER (manager
-- self-service, business rules §4).
-- =====================================================================

ALTER TABLE leave_requests ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE leave_balances ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

ALTER TABLE leave_balance_logs ADD COLUMN operation VARCHAR(20) NOT NULL DEFAULT 'APPROVAL';

CREATE INDEX idx_leave_requests_overlap ON leave_requests (employee_id, status_id, start_date, end_date);

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p ON p.code = 'leave.write' WHERE r.code = 'MANAGER';
