ALTER TABLE employees
    ADD COLUMN prime_transport DECIMAL(10,2) DEFAULT 0.00,
    ADD COLUMN prime_performance DECIMAL(10,2) DEFAULT 0.00,
    ADD COLUMN prime_other DECIMAL(10,2) DEFAULT 0.00;
