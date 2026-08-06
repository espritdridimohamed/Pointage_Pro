-- =====================================================================
-- PointagePro - V3 : Tax profile model (pre-payroll)
-- 1) tax_situations lookup + employee_tax_profiles (dated, IRPP inputs)
-- 2) salary_component_types: CNSS/IRPP/CSS taxation flags
-- =====================================================================

-- ---------------------------------------------------------------------
-- Tax situation lookup (IRPP family situation)
-- ---------------------------------------------------------------------

CREATE TABLE tax_situations (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(20) NOT NULL UNIQUE,
    label VARCHAR(50) NOT NULL
);

INSERT INTO tax_situations (code, label) VALUES
('CELIBATAIRE','Single'),
('MARIE','Married'),
('CHEF_DE_FAMILLE','Head of family');

-- ---------------------------------------------------------------------
-- Employee tax profile (dated, one active per employee)
-- ---------------------------------------------------------------------

CREATE TABLE employee_tax_profiles (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    employee_id BIGINT NOT NULL,
    tax_situation_id BIGINT NOT NULL,
    spouse_is_working TINYINT(1) NOT NULL DEFAULT 0,
    number_of_children INT NOT NULL DEFAULT 0,
    number_of_disabled_children INT NOT NULL DEFAULT 0,
    valid_from DATE NOT NULL,
    valid_to DATE NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_tax_profile_employee FOREIGN KEY (employee_id) REFERENCES employees(id) ON DELETE CASCADE,
    CONSTRAINT fk_tax_profile_situation FOREIGN KEY (tax_situation_id) REFERENCES tax_situations(id) ON DELETE RESTRICT,
    UNIQUE KEY uk_tax_profile_employee_date (employee_id, valid_from),
    INDEX idx_tax_profile_employee (employee_id)
);

-- ---------------------------------------------------------------------
-- Salary component types: taxation flags (defaults overridable per
-- salary_component instance in the UI)
-- ---------------------------------------------------------------------

ALTER TABLE salary_component_types
    ADD COLUMN is_subject_to_cnss TINYINT(1) NOT NULL DEFAULT 1,
    ADD COLUMN is_subject_to_irpp TINYINT(1) NOT NULL DEFAULT 1,
    ADD COLUMN is_subject_to_css TINYINT(1) NOT NULL DEFAULT 0;
