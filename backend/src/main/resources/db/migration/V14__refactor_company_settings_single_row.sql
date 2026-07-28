DROP TABLE IF EXISTS company_settings;

CREATE TABLE company_settings (
    id INT NOT NULL DEFAULT 1,

    -- Entreprise
    company_name VARCHAR(100) DEFAULT 'Sepab Agro',
    company_sector VARCHAR(100) DEFAULT 'Agroalimentaire',
    company_address VARCHAR(255) DEFAULT 'Zone Industrielle, Sfax 3000',
    company_email VARCHAR(100) DEFAULT 'contact@sepab.tn',
    company_phone VARCHAR(20) DEFAULT '',
    company_logo MEDIUMTEXT,

    -- Horaires de travail
    work_start_time VARCHAR(5) DEFAULT '08:00',
    work_end_time VARCHAR(5) DEFAULT '17:00',
    work_days_per_week INT DEFAULT 6,
    work_days VARCHAR(50) DEFAULT 'LUN,MAR,MER,JEU,VEN,SAM',
    late_grace_minutes INT DEFAULT 15,
    monthly_work_hours DECIMAL(5,2) DEFAULT 151.67,

    -- Heures supplementaires
    overtime_rate DECIMAL(3,1) DEFAULT 1.5,
    overtime_threshold_hours DECIMAL(4,2) DEFAULT 8.00,

    -- Paie
    currency VARCHAR(10) DEFAULT 'DT',
    pay_day INT DEFAULT 28,

    -- CNSS & Assurance
    cnss_rate DECIMAL(5,2) DEFAULT 11.26,
    cnss_employer_rate DECIMAL(5,2) DEFAULT 16.57,
    cnss_ceiling DECIMAL(10,3) DEFAULT 5173.085,
    assurance_rate DECIMAL(5,3) DEFAULT 0.761,

    -- IR (Impot sur le Revenu)
    ir_tranche1 DECIMAL(10,2) DEFAULT 5000,
    ir_rate1 DECIMAL(4,1) DEFAULT 0,
    ir_tranche2 DECIMAL(10,2) DEFAULT 20000,
    ir_rate2 DECIMAL(4,1) DEFAULT 26,
    ir_tranche3 DECIMAL(10,2) DEFAULT 30000,
    ir_rate3 DECIMAL(4,1) DEFAULT 28,
    ir_tranche4 DECIMAL(10,2) DEFAULT 50000,
    ir_rate4 DECIMAL(4,1) DEFAULT 32,
    ir_tranche5 DECIMAL(10,2) DEFAULT 999999,
    ir_rate5 DECIMAL(4,1) DEFAULT 35,
    ir_abatement DECIMAL(10,2) DEFAULT 1080,

    -- Conges
    conge_annuel_days INT DEFAULT 22,
    conge_maladie_days INT DEFAULT 30,
    conge_maternite_days INT DEFAULT 90,
    conge_paternite_days INT DEFAULT 5,

    -- Preferences
    language VARCHAR(5) DEFAULT 'fr',
    theme VARCHAR(20) DEFAULT 'light',

    -- Timestamps
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT pk_company_settings PRIMARY KEY (id)
);

INSERT INTO company_settings (id) VALUES (1);
