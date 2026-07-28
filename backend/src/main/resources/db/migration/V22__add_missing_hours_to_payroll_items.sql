ALTER TABLE payroll_items
    ADD COLUMN missing_hours DECIMAL(7,2) DEFAULT 0 AFTER absence_deduction,
    ADD COLUMN missing_hours_deduction DECIMAL(10,2) DEFAULT 0 AFTER missing_hours,
    ADD COLUMN absence_hours DECIMAL(7,2) DEFAULT 0 AFTER missing_hours_deduction;
