-- ---------------------------------------------------------------------------
-- V2  Reference data: curriculum (subjects, topics), the exam calendar, and a
--     class of students to upload results against.
--
--     Login accounts are deliberately NOT seeded here: a password hash must be
--     produced by the application PasswordEncoder, so DemoDataInitializer
--     creates the four role logins on first start and links them to these rows.
--
--     Foreign keys are resolved with sub-selects rather than hardcoded ids so
--     this migration is independent of identity-column start values.
-- ---------------------------------------------------------------------------

INSERT INTO subjects (code, name, class_name) VALUES
    ('MATH', 'Mathematics',       '10'),
    ('SCI',  'Science',           '10'),
    ('ENG',  'English',           '10'),
    ('SOC',  'Social Science',    '10'),
    ('CS',   'Computer Science',  '10');

INSERT INTO topics (subject_id, name)
SELECT id, t.name FROM subjects
CROSS JOIN (
    SELECT 'Algebra' AS name UNION ALL
    SELECT 'Geometry'        UNION ALL
    SELECT 'Trigonometry'    UNION ALL
    SELECT 'Statistics'
) t
WHERE code = 'MATH';

INSERT INTO topics (subject_id, name)
SELECT id, t.name FROM subjects
CROSS JOIN (
    SELECT 'Physics - Motion' AS name UNION ALL
    SELECT 'Physics - Optics'          UNION ALL
    SELECT 'Chemistry - Reactions'     UNION ALL
    SELECT 'Biology - Life Processes'
) t
WHERE code = 'SCI';

INSERT INTO topics (subject_id, name)
SELECT id, t.name FROM subjects
CROSS JOIN (
    SELECT 'Reading Comprehension' AS name UNION ALL
    SELECT 'Grammar'                       UNION ALL
    SELECT 'Writing Skills'                UNION ALL
    SELECT 'Literature'
) t
WHERE code = 'ENG';

INSERT INTO topics (subject_id, name)
SELECT id, t.name FROM subjects
CROSS JOIN (
    SELECT 'History - Nationalism' AS name UNION ALL
    SELECT 'Geography - Resources'         UNION ALL
    SELECT 'Civics - Democracy'            UNION ALL
    SELECT 'Economics - Development'
) t
WHERE code = 'SOC';

INSERT INTO topics (subject_id, name)
SELECT id, t.name FROM subjects
CROSS JOIN (
    SELECT 'Programming Basics' AS name UNION ALL
    SELECT 'Data Structures'            UNION ALL
    SELECT 'Databases'                  UNION ALL
    SELECT 'Networking'
) t
WHERE code = 'CS';

INSERT INTO exams (code, name, term, exam_date, class_name, academic_year) VALUES
    ('UT1-2025',   'Unit Test 1',  'TERM_1', DATE '2025-07-15', '10', '2025-2026'),
    ('MID-2025',   'Mid Term',     'TERM_1', DATE '2025-09-20', '10', '2025-2026'),
    ('UT2-2025',   'Unit Test 2',  'TERM_2', DATE '2025-11-10', '10', '2025-2026'),
    ('FINAL-2025', 'Final Exam',   'TERM_2', DATE '2026-03-05', '10', '2025-2026');

INSERT INTO students (admission_no, full_name, class_name, section, academic_year, created_at) VALUES
    ('STU1001', 'Aarav Sharma',   '10', 'A', '2025-2026', CURRENT_TIMESTAMP),
    ('STU1002', 'Diya Patel',     '10', 'A', '2025-2026', CURRENT_TIMESTAMP),
    ('STU1003', 'Kabir Nair',     '10', 'A', '2025-2026', CURRENT_TIMESTAMP),
    ('STU1004', 'Ishita Rao',     '10', 'A', '2025-2026', CURRENT_TIMESTAMP),
    ('STU1005', 'Rohan Gupta',    '10', 'A', '2025-2026', CURRENT_TIMESTAMP),
    ('STU1006', 'Meera Krishnan', '10', 'B', '2025-2026', CURRENT_TIMESTAMP),
    ('STU1007', 'Arjun Reddy',    '10', 'B', '2025-2026', CURRENT_TIMESTAMP),
    ('STU1008', 'Sara Fernandes', '10', 'B', '2025-2026', CURRENT_TIMESTAMP);
