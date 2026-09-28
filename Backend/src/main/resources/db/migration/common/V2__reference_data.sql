-- ---------------------------------------------------------------------------
-- V2  Reference data: roles, the curriculum, the exam calendar, a demo class,
--     and the learning-resource library.
--
--     Portable ANSI SQL - this file is shared by both vendors, so it must run
--     unchanged on MySQL and H2. Plain INSERTs only, no DDL.
--
--     Login accounts are deliberately NOT seeded here: a password hash must be
--     produced by the application PasswordEncoder, so DemoDataInitializer
--     creates the three role logins on first start and links them to these rows.
--
--     Subject and exam codes are the slug of their name (Social Science ->
--     SOCIAL-SCIENCE) because that is how the CSV importer resolves a name to a
--     row. Seeding a code that does not match its slug would make the importer
--     create a duplicate.
-- ---------------------------------------------------------------------------

INSERT INTO roles (name, description) VALUES
    ('ADMIN',   'Uploads result files and manages accounts'),
    ('TEACHER', 'Sees class analytics and per-category student lists'),
    ('STUDENT', 'Sees their own result, feedback and study resources');

-- Curriculum -----------------------------------------------------------------

INSERT INTO subjects (code, name, class_name) VALUES
    ('MATHEMATICS',      'Mathematics',      '10'),
    ('SCIENCE',          'Science',          '10'),
    ('ENGLISH',          'English',          '10'),
    ('SOCIAL-SCIENCE',   'Social Science',   '10'),
    ('COMPUTER-SCIENCE', 'Computer Science', '10');

INSERT INTO topics (subject_id, chapter_name, name)
SELECT s.id, t.chapter_name, t.name FROM subjects s
CROSS JOIN (
    SELECT 'Algebra'      AS chapter_name, 'Quadratic Equations' AS name UNION ALL
    SELECT 'Algebra',           'Polynomials'                             UNION ALL
    SELECT 'Geometry',          'Triangles'                               UNION ALL
    SELECT 'Geometry',          'Circles'                                 UNION ALL
    SELECT 'Trigonometry',      'Trigonometry'                            UNION ALL
    SELECT 'Statistics',        'Statistics'
) t
WHERE s.code = 'MATHEMATICS';

INSERT INTO topics (subject_id, chapter_name, name)
SELECT s.id, t.chapter_name, t.name FROM subjects s
CROSS JOIN (
    SELECT 'Physics'   AS chapter_name, 'Motion' AS name UNION ALL
    SELECT 'Physics',        'Light'                      UNION ALL
    SELECT 'Physics',        'Electricity'                UNION ALL
    SELECT 'Chemistry',      'Chemical Reactions'         UNION ALL
    SELECT 'Chemistry',      'Acids and Bases'            UNION ALL
    SELECT 'Biology',        'Life Processes'
) t
WHERE s.code = 'SCIENCE';

INSERT INTO topics (subject_id, chapter_name, name)
SELECT s.id, t.chapter_name, t.name FROM subjects s
CROSS JOIN (
    SELECT 'Reading'  AS chapter_name, 'Reading Comprehension' AS name UNION ALL
    SELECT 'Grammar',       'Grammar'                                   UNION ALL
    SELECT 'Writing',       'Writing Skills'                            UNION ALL
    SELECT 'Literature',    'Literature'
) t
WHERE s.code = 'ENGLISH';

INSERT INTO topics (subject_id, chapter_name, name)
SELECT s.id, t.chapter_name, t.name FROM subjects s
CROSS JOIN (
    SELECT 'History'   AS chapter_name, 'Nationalism' AS name UNION ALL
    SELECT 'Geography',      'Resources'                       UNION ALL
    SELECT 'Civics',         'Democracy'                       UNION ALL
    SELECT 'Economics',      'Development'
) t
WHERE s.code = 'SOCIAL-SCIENCE';

INSERT INTO topics (subject_id, chapter_name, name)
SELECT s.id, t.chapter_name, t.name FROM subjects s
CROSS JOIN (
    SELECT 'Programming' AS chapter_name, 'Programming Basics' AS name UNION ALL
    SELECT 'Programming',      'Data Structures'                        UNION ALL
    SELECT 'Databases',        'Databases'                              UNION ALL
    SELECT 'Networking',       'Networking'
) t
WHERE s.code = 'COMPUTER-SCIENCE';

-- Exam calendar --------------------------------------------------------------
-- Seeded with real dates so trends are ordered correctly. An exam_name the
-- importer has never seen is created on the fly, dated the day of the upload.

INSERT INTO exams (code, name, term, exam_date, class_name, academic_year) VALUES
    ('UNIT-TEST-1', 'Unit Test 1', 'TERM_1', DATE '2025-07-15', '10', '2025-2026'),
    ('MIDTERM',     'Midterm',     'TERM_1', DATE '2025-09-20', '10', '2025-2026'),
    ('UNIT-TEST-2', 'Unit Test 2', 'TERM_2', DATE '2025-11-10', '10', '2025-2026'),
    ('FINAL-EXAM',  'Final Exam',  'TERM_2', DATE '2026-03-05', '10', '2025-2026');

-- Demo class -----------------------------------------------------------------
-- admission_no is the student_id column of the CSV. A student_id not listed
-- here is created by the importer from the name, class and section in the file.

INSERT INTO students (admission_no, full_name, class_name, section, academic_year, created_at) VALUES
    ('1001', 'Ayushman Sharma', '10', 'A', '2025-2026', CURRENT_TIMESTAMP),
    ('1002', 'Diya Patel',      '10', 'A', '2025-2026', CURRENT_TIMESTAMP),
    ('1003', 'Kabir Nair',      '10', 'A', '2025-2026', CURRENT_TIMESTAMP),
    ('1004', 'Ishita Rao',      '10', 'A', '2025-2026', CURRENT_TIMESTAMP),
    ('1005', 'Aman Verma',      '10', 'A', '2025-2026', CURRENT_TIMESTAMP),
    ('1006', 'Meera Krishnan',  '10', 'A', '2025-2026', CURRENT_TIMESTAMP),
    ('1007', 'Arjun Reddy',     '10', 'A', '2025-2026', CURRENT_TIMESTAMP),
    ('1008', 'Sara Fernandes',  '10', 'A', '2025-2026', CURRENT_TIMESTAMP);

-- Learning resources --------------------------------------------------------
-- One book and one video per topic that a student is likely to be weak in.
-- The recommendation endpoints and the AI prompt both read from this table, so
-- feedback can only ever recommend a resource the school has actually vetted.

INSERT INTO study_resources (subject, topic, resource_type, title, url) VALUES
    ('Mathematics', 'Quadratic Equations', 'BOOK',  'RD Sharma Class 10 - Quadratic Equations', NULL),
    ('Mathematics', 'Quadratic Equations', 'VIDEO', 'Quadratic Equations in One Shot', 'https://www.youtube.com/results?search_query=quadratic+equations+class+10'),
    ('Mathematics', 'Polynomials',         'BOOK',  'NCERT Class 10 Maths - Chapter 2 Polynomials', NULL),
    ('Mathematics', 'Polynomials',         'VIDEO', 'Polynomials Explained', 'https://www.youtube.com/results?search_query=polynomials+class+10'),
    ('Mathematics', 'Triangles',           'BOOK',  'NCERT Class 10 Maths - Chapter 6 Triangles', NULL),
    ('Mathematics', 'Triangles',           'VIDEO', 'Similar Triangles Made Simple', 'https://www.youtube.com/results?search_query=triangles+class+10'),
    ('Mathematics', 'Circles',             'BOOK',  'NCERT Class 10 Maths - Chapter 10 Circles', NULL),
    ('Mathematics', 'Circles',             'VIDEO', 'Circles and Tangents', 'https://www.youtube.com/results?search_query=circles+class+10'),
    ('Mathematics', 'Trigonometry',        'BOOK',  'RD Sharma Class 10 - Introduction to Trigonometry', NULL),
    ('Mathematics', 'Trigonometry',        'VIDEO', 'Trigonometry from Scratch', 'https://www.youtube.com/results?search_query=trigonometry+class+10'),
    ('Mathematics', 'Statistics',          'BOOK',  'NCERT Class 10 Maths - Chapter 13 Statistics', NULL),
    ('Mathematics', 'Statistics',          'VIDEO', 'Mean, Median and Mode', 'https://www.youtube.com/results?search_query=statistics+class+10'),

    ('Science', 'Motion',             'BOOK',  'NCERT Class 10 Science - Motion and Force', NULL),
    ('Science', 'Motion',             'VIDEO', 'Laws of Motion for Class 10', 'https://www.youtube.com/results?search_query=motion+class+10'),
    ('Science', 'Light',              'BOOK',  'NCERT Class 10 Science - Light: Reflection and Refraction', NULL),
    ('Science', 'Light',              'VIDEO', 'Light: Reflection and Refraction', 'https://www.youtube.com/results?search_query=light+reflection+refraction+class+10'),
    ('Science', 'Electricity',        'BOOK',  'NCERT Class 10 Science - Chapter 12 Electricity', NULL),
    ('Science', 'Electricity',        'VIDEO', 'Electricity in One Shot', 'https://www.youtube.com/results?search_query=electricity+class+10'),
    ('Science', 'Chemical Reactions', 'BOOK',  'NCERT Class 10 Science - Chemical Reactions and Equations', NULL),
    ('Science', 'Chemical Reactions', 'VIDEO', 'Balancing Chemical Equations', 'https://www.youtube.com/results?search_query=chemical+reactions+class+10'),
    ('Science', 'Acids and Bases',    'BOOK',  'NCERT Class 10 Science - Acids, Bases and Salts', NULL),
    ('Science', 'Acids and Bases',    'VIDEO', 'Acids, Bases and Salts', 'https://www.youtube.com/results?search_query=acids+bases+salts+class+10'),
    ('Science', 'Life Processes',     'BOOK',  'NCERT Class 10 Science - Chapter 6 Life Processes', NULL),
    ('Science', 'Life Processes',     'VIDEO', 'Life Processes Full Chapter', 'https://www.youtube.com/results?search_query=life+processes+class+10'),

    ('English', 'Reading Comprehension', 'BOOK',  'Wren & Martin - Comprehension Practice', NULL),
    ('English', 'Reading Comprehension', 'VIDEO', 'How to Attempt Unseen Passages', 'https://www.youtube.com/results?search_query=reading+comprehension+class+10'),
    ('English', 'Grammar',               'BOOK',  'Wren & Martin - High School English Grammar', NULL),
    ('English', 'Grammar',               'VIDEO', 'Tenses and Editing Questions', 'https://www.youtube.com/results?search_query=english+grammar+class+10'),
    ('English', 'Writing Skills',        'BOOK',  'CBSE Class 10 English - Writing Skills Workbook', NULL),
    ('English', 'Writing Skills',        'VIDEO', 'Letter and Analytical Paragraph Writing', 'https://www.youtube.com/results?search_query=writing+skills+class+10'),
    ('English', 'Literature',            'BOOK',  'First Flight & Footprints Without Feet - Guide', NULL),
    ('English', 'Literature',            'VIDEO', 'First Flight Chapter Summaries', 'https://www.youtube.com/results?search_query=first+flight+class+10'),

    ('Social Science', 'Nationalism', 'BOOK',  'NCERT India and the Contemporary World II', NULL),
    ('Social Science', 'Nationalism', 'VIDEO', 'Nationalism in India', 'https://www.youtube.com/results?search_query=nationalism+in+india+class+10'),
    ('Social Science', 'Resources',   'BOOK',  'NCERT Contemporary India II - Resources and Development', NULL),
    ('Social Science', 'Resources',   'VIDEO', 'Resources and Development', 'https://www.youtube.com/results?search_query=resources+and+development+class+10'),
    ('Social Science', 'Democracy',   'BOOK',  'NCERT Democratic Politics II', NULL),
    ('Social Science', 'Democracy',   'VIDEO', 'Power Sharing and Federalism', 'https://www.youtube.com/results?search_query=democratic+politics+class+10'),
    ('Social Science', 'Development', 'BOOK',  'NCERT Understanding Economic Development', NULL),
    ('Social Science', 'Development', 'VIDEO', 'Development and Sectors of the Economy', 'https://www.youtube.com/results?search_query=economics+class+10'),

    ('Computer Science', 'Programming Basics', 'BOOK',  'Sumita Arora - Computer Applications Class 10', NULL),
    ('Computer Science', 'Programming Basics', 'VIDEO', 'Programming Fundamentals', 'https://www.youtube.com/results?search_query=programming+basics+class+10'),
    ('Computer Science', 'Data Structures',    'BOOK',  'Sumita Arora - Arrays and Lists', NULL),
    ('Computer Science', 'Data Structures',    'VIDEO', 'Arrays and Lists Explained', 'https://www.youtube.com/results?search_query=arrays+and+lists+basics'),
    ('Computer Science', 'Databases',          'BOOK',  'Sumita Arora - Database Concepts and SQL', NULL),
    ('Computer Science', 'Databases',          'VIDEO', 'SQL Basics for Beginners', 'https://www.youtube.com/results?search_query=sql+basics+for+beginners'),
    ('Computer Science', 'Networking',         'BOOK',  'Sumita Arora - Computer Networks', NULL),
    ('Computer Science', 'Networking',         'VIDEO', 'Computer Networks Basics', 'https://www.youtube.com/results?search_query=computer+networks+basics');
