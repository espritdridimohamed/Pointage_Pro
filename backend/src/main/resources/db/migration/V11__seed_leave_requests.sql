INSERT INTO leave_requests (employee_id, leave_type, start_date, end_date, reason, status, created_at, updated_at) VALUES
(1, 'Congé Annuel', '2026-07-15', '2026-07-22', 'Vacances familiales', 'APPROVED', NOW(), NOW()),
(2, 'Congé Maladie', '2026-07-10', '2026-07-12', 'Arrêt médical', 'PENDING', NOW(), NOW()),
(3, 'Congé Maternité', '2026-08-01', '2026-10-31', 'Congé maternité', 'APPROVED', NOW(), NOW()),
(4, 'Congé Sans Solde', '2026-07-18', '2026-07-20', 'Raisons personnelles', 'REFUSED', NOW(), NOW()),
(5, 'Congé Annuel', '2026-07-25', '2026-08-01', 'Repos estival', 'PENDING', NOW(), NOW()),
(6, 'Formation', '2026-07-20', '2026-07-22', 'Formation professionnelle', 'PENDING', NOW(), NOW()),
(7, 'Congé Annuel', '2026-08-05', '2026-08-12', 'Vacances', 'PENDING', NOW(), NOW()),
(8, 'Congé Maladie', '2026-06-28', '2026-06-30', 'Grippe', 'APPROVED', NOW(), NOW());
