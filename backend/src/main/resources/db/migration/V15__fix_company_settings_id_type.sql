-- Fix: Hibernate expects BIGINT for Java Long, V14 created INT
ALTER TABLE company_settings MODIFY COLUMN id BIGINT NOT NULL DEFAULT 1;
