-- ═══════════════════════════════════════════════════════════════════════
-- SEED DATA  (all login passwords = 'Password@123')
-- ═══════════════════════════════════════════════════════════════════════

-- ── Employee master roll (the directory) ──
INSERT INTO tran.employee_details
    (employee_id, organization_id, office_id, department_id, emp_ini_name, emp_first_name, emp_mid_name, emp_last_name, gender, date_of_birth, mobile_number, email, designation, joining_date, is_active) VALUES
    (81848, 1, 101, 40, 'Smt', 'Anita',     NULL,      'Sharma',   'F', '1979-05-14', '9436120011', 'authority@nic.in', 'Deputy Director (HR)', '2005-06-01', TRUE),
    (81850, 1, 101, 20, 'Shri','Rajesh',    'Kumar',   'Debbarma', 'M', '1982-11-02', '9436120022', NULL,               'Director',             '2008-03-15', TRUE),
    (81851, 1, 101, 20, 'Shri','Tathagata', NULL,      'Laskar',   'M', '2004-08-21', '9436120033', 'employee@nic.in',  'Scientist-B',          '2024-07-15', TRUE),
    (81853, 1, 101, 10, 'Smt', 'Priya',     NULL,      'Menon',    'F', '1990-03-30', '9436120044', 'priya@nic.in',     'Scientist-B',          '2016-08-01', TRUE),
    (81860, 1, 101, 30, 'Shri','Sunil',     'Chandra', 'Nath',     'M', '1988-07-19', '9436120055', 'sunil@nic.in',     'Scientist-C',          '2012-01-10', TRUE),
    (81862, 1, 101, 10, 'Smt', 'Rebecca',   NULL,      'Chakraborty','F','1992-12-05', '9436120066', NULL,               'Scientist-B',          '2018-11-20', TRUE);

-- ── Login accounts (1 authority + employees) ──
-- Authority = Anita Sharma (Dy. Director, HR)
INSERT INTO tran.user_account (employee_id, email, password_hash, role) VALUES
    (81848, 'authority@nic.in', '$2b$10$vttwGAI.2vQgKVYUYyVlcOb6o.hHedni66GTcN3fKgDXPNq0FiWHS', 'AUTHORITY'),
    (81851, 'employee@nic.in',  '$2b$10$vttwGAI.2vQgKVYUYyVlcOb6o.hHedni66GTcN3fKgDXPNq0FiWHS', 'EMPLOYEE'),
    (81853, 'priya@nic.in',     '$2b$10$vttwGAI.2vQgKVYUYyVlcOb6o.hHedni66GTcN3fKgDXPNq0FiWHS', 'EMPLOYEE'),
    (81860, 'sunil@nic.in',     '$2b$10$vttwGAI.2vQgKVYUYyVlcOb6o.hHedni66GTcN3fKgDXPNq0FiWHS', 'EMPLOYEE');

-- ── A sample already-submitted ACR for Priya (so the authority has something to review) ──
INSERT INTO tran.employee_acr_status
    (employee_id, organization_id, office_id, department_id, emp_ini_name, emp_first_name, emp_mid_name, emp_last_name,
     assessment_year, is_acr_submitted, submitted_at)
VALUES
    (81853, 1, 101, 10, 'Smt', 'Priya', NULL, 'Menon', '2025-2026', TRUE, NOW());

INSERT INTO tran.employee_acr_details
    (employee_id, organisation_id, office_id, department_id, assessment_year, target, achievements, sequence_order) VALUES
    (81853, 1, 101, 10, '2025-2026', 'Migrate citizen services portal to new stack', 'Completed migration with zero downtime', 1),
    (81853, 1, 101, 10, '2025-2026', 'Mentor two junior developers',                 'Both cleared internal certification',     2);

-- ── Current ACR cycle (due in 7 days from seed time → reminders are demoable) ──
INSERT INTO tran.acr_cycle (assessment_year, due_date, is_open)
VALUES ('2025-2026', CURRENT_DATE + INTERVAL '7 days', TRUE);

-- ── A sample announcement for the employee (so the bell shows something on day one) ──
INSERT INTO tran.notification (recipient_employee_id, sender_employee_id, type, title, message)
VALUES (81851, 81848, 'ANNOUNCEMENT', 'Welcome to the APAR Portal',
        'Please complete your Annual Performance Assessment Report for the 2025-2026 cycle before the due date.');
