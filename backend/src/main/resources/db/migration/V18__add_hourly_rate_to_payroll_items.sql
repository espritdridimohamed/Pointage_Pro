ALTER TABLE payroll_items
  ADD COLUMN hourly_rate DECIMAL(10,4) NULL AFTER total_overtime_minutes;
