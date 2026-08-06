-- PointagePro - V9 : immutable attendance-facts snapshot per payroll run
--
-- Payroll must never read attendance_summary (the mutable working table, rewritten
-- wholesale on every recompute). At payroll generation the payroll module copies each
-- day's facts verbatim into this table (freeze step) and computes payroll_items /
-- payroll_item_components ONLY from it. Later attendance recomputes never touch a
-- frozen run; corrections go to the next period or an explicit audited reopening.

CREATE TABLE payroll_attendance_snapshots (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    payroll_id BIGINT NOT NULL,
    company_id BIGINT NOT NULL,
    employee_id BIGINT NOT NULL,
    work_date DATE NOT NULL,
    day_type_id BIGINT NULL,
    schedule_id BIGINT NULL,
    status_id BIGINT NULL,
    first_in TIME NULL,
    last_out TIME NULL,
    worked_minutes INT NOT NULL DEFAULT 0,
    late_minutes INT NOT NULL DEFAULT 0,
    early_exit_minutes INT NOT NULL DEFAULT 0,
    missing_minutes INT NOT NULL DEFAULT 0,
    overtime_minutes INT NOT NULL DEFAULT 0,
    netted_work_minutes INT NOT NULL DEFAULT 0,
    adjustment_minutes INT NOT NULL DEFAULT 0,
    is_weekend TINYINT(1) NOT NULL DEFAULT 0,
    is_holiday TINYINT(1) NOT NULL DEFAULT 0,
    source_summary_id BIGINT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_pas_payroll FOREIGN KEY (payroll_id) REFERENCES payrolls(id) ON DELETE RESTRICT,
    CONSTRAINT fk_pas_company FOREIGN KEY (company_id) REFERENCES companies(id) ON DELETE RESTRICT,
    CONSTRAINT fk_pas_employee FOREIGN KEY (employee_id) REFERENCES employees(id) ON DELETE RESTRICT,
    CONSTRAINT fk_pas_day_type FOREIGN KEY (day_type_id) REFERENCES day_types(id) ON DELETE RESTRICT,
    CONSTRAINT fk_pas_schedule FOREIGN KEY (schedule_id) REFERENCES work_schedules(id) ON DELETE RESTRICT,
    CONSTRAINT fk_pas_status FOREIGN KEY (status_id) REFERENCES attendance_statuses(id) ON DELETE RESTRICT,
    CONSTRAINT fk_pas_source_summary FOREIGN KEY (source_summary_id) REFERENCES attendance_summary(id) ON DELETE SET NULL,
    UNIQUE KEY uk_pas (payroll_id, employee_id, work_date),
    INDEX idx_pas_payroll (payroll_id),
    INDEX idx_pas_employee_date (employee_id, work_date)
);
