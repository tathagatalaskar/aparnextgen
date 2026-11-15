-- Production reference data required by self-registration.
-- Safe to run repeatedly; intentionally contains no demo users or passwords.

INSERT INTO tran.organization (organization_id, organization_name)
VALUES (1, 'National Informatics Centre')
ON CONFLICT (organization_id) DO UPDATE
SET organization_name = EXCLUDED.organization_name;

INSERT INTO tran.office (office_id, organization_id, office_name)
VALUES (101, 1, 'NIC Tripura State Centre, Agartala')
ON CONFLICT (office_id) DO UPDATE
SET organization_id = EXCLUDED.organization_id,
    office_name = EXCLUDED.office_name;

INSERT INTO tran.department (department_id, department_name) VALUES
    (10, 'e-Governance Division'),
    (20, 'Software Development Division'),
    (30, 'Network & Infrastructure Division'),
    (40, 'Administration & HR')
ON CONFLICT (department_id) DO UPDATE
SET department_name = EXCLUDED.department_name;
