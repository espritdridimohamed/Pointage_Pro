ALTER TABLE employees
  ADD COLUMN weekly_schedule TEXT AFTER rfid_uid,
  ADD COLUMN annual_leave_days INT NULL AFTER weekly_schedule,
  ADD COLUMN maternity_leave_days INT NULL AFTER annual_leave_days,
  ADD COLUMN paternity_leave_days INT NULL AFTER maternity_leave_days;
