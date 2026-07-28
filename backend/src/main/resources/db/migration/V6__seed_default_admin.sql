INSERT INTO users (username, password, full_name, email, role, enabled)
VALUES ('admin', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'Administrator', 'admin@pointagepro.com', 'ADMIN', TRUE);

INSERT INTO company_settings (setting_key, setting_value, description) VALUES
('company_name', 'SepabAgro', 'Company name'),
('work_start_time', '08:00', 'Daily work start time'),
('work_end_time', '17:00', 'Daily work end time'),
('late_grace_minutes', '15', 'Minutes after work start before marking as late'),
('overtime_threshold_hours', '8', 'Hours worked before overtime applies'),
('work_days_per_week', '6', 'Number of working days per week'),
('overtime_rate', '1.5', 'Multiplier for overtime hours');
