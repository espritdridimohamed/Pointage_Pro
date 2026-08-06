-- =====================================================================
-- PointagePro - V16 : relax department-name uniqueness (Module 6 review)
-- The old uk_departments_company_name (company_id, name) blocked reusing a
-- department name after its row was closed (valid_to set). Replace it with:
--   1) active-name uniqueness via a STORED generated column that is NULL when
--      valid_to IS NOT NULL (unique indexes ignore NULLs, so only active rows
--      are constrained; closing a department frees its name for reuse).
--   2) code uniqueness per company across ALL rows (code is the stable
--      identifier; NULL codes remain allowed since NULLs are distinct).
-- =====================================================================

ALTER TABLE departments DROP INDEX uk_departments_company_name;

ALTER TABLE departments
    ADD COLUMN active_name VARCHAR(100)
        GENERATED ALWAYS AS (IF(valid_to IS NULL, name, NULL)) STORED,
    ADD UNIQUE KEY uk_departments_company_active_name (company_id, active_name),
    ADD UNIQUE KEY uk_departments_company_code (company_id, code);
