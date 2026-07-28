-- Clean up existing payroll for July 2026
DELETE FROM payroll_items WHERE payroll_id = 5;
DELETE FROM payrolls WHERE id = 5;

-- Clean up any existing attendance for employee 10 in July 2026
DELETE FROM attendance WHERE employee_id = 10 AND date BETWEEN '2026-07-01' AND '2026-07-30';

-- ============================================================
-- Employee 10: Mohamed Dridi - 30 days of attendance
-- Salary: 1500 DT, Schedule: LUN-FRI 08-17, SAM 08-13, DIM 08-12
-- Monthly hours: 234h, Hourly rate: 6.41 DT/h, Minute rate: 0.1068 DT/min
-- ============================================================

-- Week 1: Jul 1-5 (all on time)
INSERT INTO attendance (employee_id, date, check_in, check_out, worked_hours, late_minutes, overtime_hours, status, created_at, updated_at) VALUES
(10, '2026-07-01', '2026-07-01 08:00:00', '2026-07-01 17:00:00', 9.00, 0, 0.00, 'PRESENT', NOW(), NOW()),
(10, '2026-07-02', '2026-07-02 08:00:00', '2026-07-02 17:00:00', 9.00, 0, 0.00, 'PRESENT', NOW(), NOW()),
(10, '2026-07-03', '2026-07-03 08:00:00', '2026-07-03 17:00:00', 9.00, 0, 0.00, 'PRESENT', NOW(), NOW()),
(10, '2026-07-04', '2026-07-04 08:00:00', '2026-07-04 13:00:00', 5.00, 0, 0.00, 'PRESENT', NOW(), NOW()),
(10, '2026-07-05', '2026-07-05 08:00:00', '2026-07-05 12:00:00', 4.00, 0, 0.00, 'PRESENT', NOW(), NOW());

-- Week 2: Jul 6-12 (Jul 12 = ABSENT)
INSERT INTO attendance (employee_id, date, check_in, check_out, worked_hours, late_minutes, overtime_hours, status, created_at, updated_at) VALUES
(10, '2026-07-06', '2026-07-06 08:00:00', '2026-07-06 17:00:00', 9.00, 0, 0.00, 'PRESENT', NOW(), NOW()),
(10, '2026-07-07', '2026-07-07 08:00:00', '2026-07-07 17:00:00', 9.00, 0, 0.00, 'PRESENT', NOW(), NOW()),
(10, '2026-07-08', '2026-07-08 08:00:00', '2026-07-08 17:00:00', 9.00, 0, 0.00, 'PRESENT', NOW(), NOW()),
(10, '2026-07-09', '2026-07-09 08:00:00', '2026-07-09 17:00:00', 9.00, 0, 0.00, 'PRESENT', NOW(), NOW()),
(10, '2026-07-10', '2026-07-10 08:00:00', '2026-07-10 17:00:00', 9.00, 0, 0.00, 'PRESENT', NOW(), NOW()),
(10, '2026-07-11', '2026-07-11 08:00:00', '2026-07-11 13:00:00', 5.00, 0, 0.00, 'PRESENT', NOW(), NOW()),
(10, '2026-07-12', NULL, NULL, 0.00, 0, 0.00, 'ABSENT', NOW(), NOW());

-- Week 3: Jul 13-19 (Jul 19 = WITHIN TOLERANCE: 08:10 on DIM)
INSERT INTO attendance (employee_id, date, check_in, check_out, worked_hours, late_minutes, overtime_hours, status, created_at, updated_at) VALUES
(10, '2026-07-13', '2026-07-13 08:00:00', '2026-07-13 17:00:00', 9.00, 0, 0.00, 'PRESENT', NOW(), NOW()),
(10, '2026-07-14', '2026-07-14 08:00:00', '2026-07-14 17:00:00', 9.00, 0, 0.00, 'PRESENT', NOW(), NOW()),
(10, '2026-07-15', '2026-07-15 08:00:00', '2026-07-15 17:00:00', 9.00, 0, 0.00, 'PRESENT', NOW(), NOW()),
(10, '2026-07-16', '2026-07-16 08:00:00', '2026-07-16 17:00:00', 9.00, 0, 0.00, 'PRESENT', NOW(), NOW()),
(10, '2026-07-17', '2026-07-17 08:00:00', '2026-07-17 17:00:00', 9.00, 0, 0.00, 'PRESENT', NOW(), NOW()),
(10, '2026-07-18', '2026-07-18 08:00:00', '2026-07-18 13:00:00', 5.00, 0, 0.00, 'PRESENT', NOW(), NOW()),
(10, '2026-07-19', '2026-07-19 08:10:00', '2026-07-19 12:00:00', 4.00, 0, 0.00, 'PRESENT', NOW(), NOW());

-- Week 4: Jul 20-26 (Jul 22 = OT 30min, Jul 26 = AFTER TOLERANCE on DIM)
INSERT INTO attendance (employee_id, date, check_in, check_out, worked_hours, late_minutes, overtime_hours, status, created_at, updated_at) VALUES
(10, '2026-07-20', '2026-07-20 08:00:00', '2026-07-20 17:00:00', 9.00, 0, 0.00, 'PRESENT', NOW(), NOW()),
(10, '2026-07-21', '2026-07-21 08:00:00', '2026-07-21 17:00:00', 9.00, 0, 0.00, 'PRESENT', NOW(), NOW()),
(10, '2026-07-22', '2026-07-22 08:00:00', '2026-07-22 17:30:00', 9.50, 0, 0.50, 'PRESENT', NOW(), NOW()),
(10, '2026-07-23', '2026-07-23 08:00:00', '2026-07-23 17:00:00', 9.00, 0, 0.00, 'PRESENT', NOW(), NOW()),
(10, '2026-07-24', '2026-07-24 08:00:00', '2026-07-24 17:00:00', 9.00, 0, 0.00, 'PRESENT', NOW(), NOW()),
(10, '2026-07-25', '2026-07-25 08:00:00', '2026-07-25 13:00:00', 5.00, 0, 0.00, 'PRESENT', NOW(), NOW()),
(10, '2026-07-26', '2026-07-26 08:30:00', '2026-07-26 12:00:00', 3.50, 30, 0.00, 'PRESENT', NOW(), NOW());

-- Week 5: Jul 27-30 (Jul 28 = AFTER TOLERANCE on MAR, Jul 29 = OT 40min)
INSERT INTO attendance (employee_id, date, check_in, check_out, worked_hours, late_minutes, overtime_hours, status, created_at, updated_at) VALUES
(10, '2026-07-27', '2026-07-27 08:00:00', '2026-07-27 17:00:00', 9.00, 0, 0.00, 'PRESENT', NOW(), NOW()),
(10, '2026-07-28', '2026-07-28 08:25:00', '2026-07-28 17:00:00', 8.58, 25, 0.00, 'PRESENT', NOW(), NOW()),
(10, '2026-07-29', '2026-07-29 08:00:00', '2026-07-29 17:40:00', 9.67, 0, 0.67, 'PRESENT', NOW(), NOW()),
(10, '2026-07-30', '2026-07-30 08:00:00', '2026-07-30 17:00:00', 9.00, 0, 0.00, 'PRESENT', NOW(), NOW());
