-- Drop congé columns from company_settings
ALTER TABLE company_settings
  DROP COLUMN conge_annuel_days,
  DROP COLUMN conge_maladie_days,
  DROP COLUMN conge_maternite_days,
  DROP COLUMN conge_paternite_days;

-- Create leave_allocations table
CREATE TABLE leave_allocations (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  employee_id BIGINT NOT NULL,
  year INT NOT NULL,
  leave_type VARCHAR(50) NOT NULL,
  allocated INT NOT NULL DEFAULT 0,
  used INT NOT NULL DEFAULT 0,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_allocation (employee_id, year, leave_type),
  FOREIGN KEY (employee_id) REFERENCES employees(id) ON DELETE CASCADE
);

-- Initialize current year allocations for existing employees
-- Congé Annuel
INSERT INTO leave_allocations (employee_id, year, leave_type, allocated, used)
SELECT e.id, YEAR(CURDATE()), 'Congé Annuel',
  COALESCE(e.annual_leave_days, 22),
  COALESCE((
    SELECT SUM(DATEDIFF(lr.end_date, lr.start_date) + 1)
    FROM leave_requests lr
    WHERE lr.employee_id = e.id AND lr.leave_type = 'Congé Annuel'
      AND lr.status IN ('APPROVED', 'PENDING')
  ), 0)
FROM employees e
WHERE e.status != 'INACTIF';

-- Congé Maternité
INSERT INTO leave_allocations (employee_id, year, leave_type, allocated, used)
SELECT e.id, YEAR(CURDATE()), 'Congé Maternité',
  COALESCE(e.maternity_leave_days, 90),
  COALESCE((
    SELECT SUM(DATEDIFF(lr.end_date, lr.start_date) + 1)
    FROM leave_requests lr
    WHERE lr.employee_id = e.id AND lr.leave_type = 'Congé Maternité'
      AND lr.status IN ('APPROVED', 'PENDING')
  ), 0)
FROM employees e
WHERE e.status != 'INACTIF'
ON DUPLICATE KEY UPDATE allocated = VALUES(allocated);

-- Congé Paternité
INSERT INTO leave_allocations (employee_id, year, leave_type, allocated, used)
SELECT e.id, YEAR(CURDATE()), 'Congé Paternité',
  COALESCE(e.paternity_leave_days, 5),
  COALESCE((
    SELECT SUM(DATEDIFF(lr.end_date, lr.start_date) + 1)
    FROM leave_requests lr
    WHERE lr.employee_id = e.id AND lr.leave_type = 'Congé Paternité'
      AND lr.status IN ('APPROVED', 'PENDING')
  ), 0)
FROM employees e
WHERE e.status != 'INACTIF'
ON DUPLICATE KEY UPDATE allocated = VALUES(allocated);
