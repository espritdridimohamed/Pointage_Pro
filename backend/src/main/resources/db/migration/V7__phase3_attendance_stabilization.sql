-- =====================================================================
-- PointagePro - V7 : Phase 3 attendance stabilization
-- 1) Seed DEDUCTION_RETARD salary component type so the DB is fully
--    stable before attendance services start (confirmed decision:
--    lateness deducted separately from absence).
-- =====================================================================

INSERT INTO salary_component_types (code, label, category, is_subject_to_cnss,
                                    is_subject_to_irpp, is_subject_to_css)
VALUES ('DEDUCTION_RETARD', 'Late arrival deduction', 'DEDUCTION', 0, 0, 0);
