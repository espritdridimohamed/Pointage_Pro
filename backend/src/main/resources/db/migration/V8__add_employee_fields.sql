ALTER TABLE employees
    ADD COLUMN department VARCHAR(50) AFTER position,
    ADD COLUMN contract_type VARCHAR(30) AFTER department,
    ADD COLUMN photo VARCHAR(255) AFTER contract_type,
    ADD COLUMN birth_date DATE AFTER photo,
    ADD COLUMN cin VARCHAR(20) AFTER birth_date,
    ADD COLUMN address VARCHAR(255) AFTER cin;

UPDATE employees SET status = 'ACTIF' WHERE status = 'ACTIVE';
UPDATE employees SET status = 'INACTIF' WHERE status = 'INACTIVE';
