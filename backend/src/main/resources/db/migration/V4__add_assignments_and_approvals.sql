-- =====================================================================
-- PointagePro - V4 : Employee assignments history + approval workflow
-- 1) employee_assignments : dated department/position/location history
--    (spec 3.2) - employees.* columns stay the current snapshot
-- 2) approvals : multi-step approval workflow for leave_requests and
--    attendance_adjustments (Manager -> HR)
-- =====================================================================

-- ---------------------------------------------------------------------
-- Employee assignments (dated, one current row valid_to IS NULL)
-- ---------------------------------------------------------------------

CREATE TABLE employee_assignments (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    employee_id BIGINT NOT NULL,
    department_id BIGINT NULL,
    position_id BIGINT NULL,
    location_id BIGINT NULL,
    valid_from DATE NOT NULL,
    valid_to DATE NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_assign_employee FOREIGN KEY (employee_id) REFERENCES employees(id) ON DELETE RESTRICT,
    CONSTRAINT fk_assign_department FOREIGN KEY (department_id) REFERENCES departments(id) ON DELETE RESTRICT,
    CONSTRAINT fk_assign_position FOREIGN KEY (position_id) REFERENCES positions(id) ON DELETE RESTRICT,
    CONSTRAINT fk_assign_location FOREIGN KEY (location_id) REFERENCES locations(id) ON DELETE RESTRICT,
    UNIQUE KEY uk_assign_employee_date (employee_id, valid_from),
    INDEX idx_assign_employee (employee_id),
    INDEX idx_assign_department (department_id),
    INDEX idx_assign_position (position_id)
);

-- ---------------------------------------------------------------------
-- Approval workflow
-- ---------------------------------------------------------------------

CREATE TABLE approval_statuses (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(20) NOT NULL UNIQUE,
    label VARCHAR(30) NOT NULL
);

INSERT INTO approval_statuses (code, label) VALUES
('PENDING','Pending'),('APPROVED','Approved'),('REJECTED','Rejected');

CREATE TABLE approvals (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    request_type VARCHAR(20) NOT NULL,
    request_id BIGINT NOT NULL,
    step_order INT NOT NULL,
    approver_role VARCHAR(20) NOT NULL,
    approver_id BIGINT NULL,
    status_id BIGINT NOT NULL DEFAULT 1,
    comment VARCHAR(500),
    decided_at DATETIME,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_approval_status FOREIGN KEY (status_id) REFERENCES approval_statuses(id) ON DELETE RESTRICT,
    CONSTRAINT fk_approval_approver FOREIGN KEY (approver_id) REFERENCES users(id) ON DELETE SET NULL,
    UNIQUE KEY uk_approval_request_step (request_type, request_id, step_order),
    INDEX idx_approval_request (request_type, request_id),
    INDEX idx_approval_status (status_id)
);
