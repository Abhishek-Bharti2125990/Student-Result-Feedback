package com.srip.config;

import com.srip.domain.ParentStudent;
import com.srip.domain.Role;
import com.srip.domain.Student;
import com.srip.domain.Subject;
import com.srip.domain.Teacher;
import com.srip.domain.TeacherSubject;
import com.srip.domain.UserAccount;
import com.srip.repository.ParentStudentRepository;
import com.srip.repository.StudentRepository;
import com.srip.repository.SubjectRepository;
import com.srip.repository.TeacherRepository;
import com.srip.repository.TeacherSubjectRepository;
import com.srip.repository.UserAccountRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Creates one login per role on first start.
 *
 * <p>Logins are seeded here rather than in a Flyway migration because a
 * password must be hashed by the application's own {@link PasswordEncoder}. A
 * migration would have to embed a pre-computed BCrypt hash, which cannot be
 * verified by reading the file and breaks the moment the encoder changes.
 *
 * <p>Idempotent: existing accounts are left untouched, so restarting never
 * resets a password that has been changed.
 */
@Component
public class DemoDataInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataInitializer.class);

    private static final String DEMO_ADMISSION_NO = "STU1001";
    private static final String DEMO_STAFF_NO = "T-100";
    private static final String DEMO_CLASS = "10";

    private final DemoProperties properties;
    private final UserAccountRepository users;
    private final StudentRepository students;
    private final TeacherRepository teachers;
    private final SubjectRepository subjects;
    private final TeacherSubjectRepository teacherSubjects;
    private final ParentStudentRepository parentLinks;
    private final PasswordEncoder passwordEncoder;

    public DemoDataInitializer(DemoProperties properties,
                               UserAccountRepository users,
                               StudentRepository students,
                               TeacherRepository teachers,
                               SubjectRepository subjects,
                               TeacherSubjectRepository teacherSubjects,
                               ParentStudentRepository parentLinks,
                               PasswordEncoder passwordEncoder) {
        this.properties = properties;
        this.users = users;
        this.students = students;
        this.teachers = teachers;
        this.subjects = subjects;
        this.teacherSubjects = teacherSubjects;
        this.parentLinks = parentLinks;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!properties.seedUsers()) {
            return;
        }

        boolean created = false;
        created |= ensureAdmin();
        created |= ensureTeacher();
        created |= ensureStudent();
        created |= ensureParent();

        if (created) {
            log.info("""

                    Seeded demo logins (password: {})
                      admin    ADMIN
                      teacher1 TEACHER  (staff {})
                      student1 STUDENT  (admission {})
                      parent1  PARENT   (linked to {})
                    Set app.demo.seed-users=false to stop creating these.
                    """, properties.defaultPassword(), DEMO_STAFF_NO, DEMO_ADMISSION_NO, DEMO_ADMISSION_NO);
        }
    }

    private boolean ensureAdmin() {
        return createIfAbsent("admin", "admin@school.local", "School Administrator", Role.ADMIN)
                .isPresent();
    }

    private boolean ensureTeacher() {
        Optional<UserAccount> account = createIfAbsent(
                "teacher1", "teacher1@school.local", "Priya Menon", Role.TEACHER);
        if (account.isEmpty()) {
            return false;
        }

        Teacher teacher = teachers.save(
                new Teacher(account.get(), DEMO_STAFF_NO, "Priya Menon", "Mathematics"));

        // Give the demo teacher every class-10 subject so the class analytics
        // and teacher feedback endpoints have something to return immediately.
        List<Subject> classSubjects = subjects.findAll().stream()
                .filter(subject -> DEMO_CLASS.equals(subject.getClassName()))
                .toList();
        for (Subject subject : classSubjects) {
            teacherSubjects.save(new TeacherSubject(teacher, subject, DEMO_CLASS));
        }
        return true;
    }

    private boolean ensureStudent() {
        Optional<Student> student = students.findByAdmissionNo(DEMO_ADMISSION_NO);
        if (student.isEmpty()) {
            log.warn("Student {} is missing, so no student login was created", DEMO_ADMISSION_NO);
            return false;
        }

        Optional<UserAccount> account = createIfAbsent(
                "student1", "student1@school.local", student.get().getFullName(), Role.STUDENT);
        if (account.isEmpty()) {
            return false;
        }

        student.get().setUser(account.get());
        students.save(student.get());
        return true;
    }

    private boolean ensureParent() {
        Optional<UserAccount> account = createIfAbsent(
                "parent1", "parent1@school.local", "Rahul Sharma", Role.PARENT);
        if (account.isEmpty()) {
            return false;
        }

        students.findByAdmissionNo(DEMO_ADMISSION_NO).ifPresent(student ->
                parentLinks.save(new ParentStudent(account.get(), student, "FATHER")));
        return true;
    }

    /** @return the new account, or empty when one already existed */
    private Optional<UserAccount> createIfAbsent(String username, String email, String fullName, Role role) {
        if (users.existsByUsername(username)) {
            return Optional.empty();
        }
        return Optional.of(users.save(new UserAccount(
                username, email, passwordEncoder.encode(properties.defaultPassword()), fullName, role)));
    }
}
