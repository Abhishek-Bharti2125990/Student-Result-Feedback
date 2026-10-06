package com.srip.service;

import com.srip.domain.Role;
import com.srip.domain.Student;
import com.srip.domain.Teacher;
import com.srip.domain.UserAccount;
import com.srip.dto.admin.UserAdminDtos.CreateUserRequest;
import com.srip.dto.admin.UserAdminDtos.UpdateUserRequest;
import com.srip.dto.admin.UserAdminDtos.UserView;
import com.srip.exception.ApiExceptions;
import com.srip.repository.RefreshTokenRepository;
import com.srip.repository.StudentRepository;
import com.srip.repository.TeacherRepository;
import com.srip.repository.TeacherSubjectRepository;
import com.srip.repository.UploadJobRepository;
import com.srip.repository.UserAccountRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Account administration: list, create, edit, activate/deactivate and delete.
 *
 * <p>Three rules shape everything here.
 *
 * <p><b>A user is two records.</b> A STUDENT login owns a row in
 * {@code students} and a TEACHER login one in {@code teachers}, and marks,
 * analytics and subject assignments hang off those - not off the login. So
 * creating a user creates its companion record, and deleting one has to decide
 * what happens to that companion rather than letting a foreign key decide.
 *
 * <p><b>Deactivating is the safe operation; deleting is not.</b> The JWT filter
 * re-reads {@code enabled} on every request, so disabling an account ends its
 * sessions within one request even though the access token it holds is still
 * cryptographically valid. That makes deactivation the right answer to "this
 * person has left", and deletion something to refuse when it would take history
 * with it.
 *
 * <p><b>An admin cannot switch off or delete their own account.</b> That single
 * rule is also what keeps the platform from losing its last administrator: this
 * endpoint is ADMIN-only and the caller is necessarily active, so any
 * <em>other</em> active admin they could target is the second of at least two.
 * Refusing the self case therefore makes an admin-less platform unreachable,
 * without a separate "is this the last one" count that could never fire.
 */
@Service
public class UserAdminService {

    private static final Logger log = LoggerFactory.getLogger(UserAdminService.class);

    /** Matches the minimum the create DTO enforces, so a reset cannot weaken it. */
    private static final int MIN_PASSWORD_LENGTH = 8;

    private final UserAccountRepository users;
    private final StudentRepository students;
    private final TeacherRepository teachers;
    private final TeacherSubjectRepository teacherSubjects;
    private final RefreshTokenRepository refreshTokens;
    private final UploadJobRepository uploadJobs;
    private final PasswordEncoder passwordEncoder;
    private final AccessGuard accessGuard;

    public UserAdminService(UserAccountRepository users,
                            StudentRepository students,
                            TeacherRepository teachers,
                            TeacherSubjectRepository teacherSubjects,
                            RefreshTokenRepository refreshTokens,
                            UploadJobRepository uploadJobs,
                            PasswordEncoder passwordEncoder,
                            AccessGuard accessGuard) {
        this.users = users;
        this.students = students;
        this.teachers = teachers;
        this.teacherSubjects = teacherSubjects;
        this.refreshTokens = refreshTokens;
        this.uploadJobs = uploadJobs;
        this.passwordEncoder = passwordEncoder;
        this.accessGuard = accessGuard;
    }

    // -- Reads ---------------------------------------------------------------

    /**
     * Every account, grouped by role.
     *
     * <p>The companion records are fetched in two batch queries rather than one
     * per row: a school has a few hundred logins, and a per-row lookup would
     * turn one screen into a few hundred selects.
     *
     * @param role  restrict to one role, or null for all
     * @param query case-insensitive substring of username, e-mail or full name
     */
    @Transactional(readOnly = true)
    public List<UserView> list(Role role, String query) {
        List<UserAccount> accounts = users.findAllByOrderByRoleAscUsernameAsc();
        if (role != null) {
            accounts = accounts.stream().filter(user -> user.getRole() == role).toList();
        }

        String needle = (query == null) ? "" : query.trim().toLowerCase();
        if (!needle.isEmpty()) {
            accounts = accounts.stream().filter(user -> matches(user, needle)).toList();
        }
        if (accounts.isEmpty()) {
            return List.of();
        }

        List<Long> ids = accounts.stream().map(UserAccount::getId).toList();
        Map<Long, Student> studentsByUser = students.findByUserIdIn(ids).stream()
                .collect(Collectors.toMap(student -> student.getUser().getId(), Function.identity()));
        Map<Long, Teacher> teachersByUser = teachers.findByUserIdIn(ids).stream()
                .collect(Collectors.toMap(teacher -> teacher.getUser().getId(), Function.identity()));

        // One count query per teacher, which is acceptable because there are far
        // fewer teachers than students - and the assignment count is what warns
        // an admin that deleting this account also drops its classes.
        Map<Long, Integer> assignmentCounts = new HashMap<>();
        teachersByUser.values().forEach(teacher ->
                assignmentCounts.put(teacher.getId(), (int) teacherSubjects.countByTeacherId(teacher.getId())));

        return accounts.stream()
                .map(user -> toView(
                        user,
                        studentsByUser.get(user.getId()),
                        teachersByUser.get(user.getId()),
                        assignmentCounts))
                .toList();
    }

    @Transactional(readOnly = true)
    public UserView get(Long userId) {
        UserAccount user = require(userId);
        return toView(user, studentOf(user), teacherOf(user), Map.of());
    }

    // -- Create --------------------------------------------------------------

    /**
     * Creates an account and the record its role implies.
     *
     * <p>For a STUDENT, an {@code admissionNo} that already exists links to that
     * child rather than creating a second record: results are imported by
     * admission number long before logins exist, and a duplicate record would
     * split one child's history in two. An unknown admission number creates the
     * record, which is the only case where the class and academic year matter.
     */
    @Transactional
    public UserView create(CreateUserRequest request) {
        String username = request.username().trim();
        String email = request.email().trim();

        if (users.existsByUsername(username)) {
            throw new ApiExceptions.ConflictException("Username already taken: " + username);
        }
        if (users.existsByEmail(email)) {
            throw new ApiExceptions.ConflictException("Email already registered: " + email);
        }

        UserAccount user = new UserAccount(
                username,
                email,
                passwordEncoder.encode(request.password()),
                request.fullName().trim(),
                request.role());

        // Active unless asked otherwise, so an account can be prepared before
        // the person it belongs to starts.
        if (Boolean.FALSE.equals(request.enabled())) {
            user.setEnabled(false);
        }
        user = users.save(user);

        Student student = null;
        Teacher teacher = null;
        switch (request.role()) {
            case STUDENT -> student = attachStudent(user, request);
            case TEACHER -> teacher = createTeacher(user, request);
            case ADMIN -> {
                // An administrator needs no companion record.
            }
        }

        log.info("Admin created {} account {}", user.getRole(), user.getUsername());
        return toView(user, student, teacher, Map.of());
    }

    // -- Edit ----------------------------------------------------------------

    /**
     * Edits an account and its companion record.
     *
     * <p>The role is not editable. Moving an account between roles would strand
     * whatever the old role owned - a teacher's subject assignments, a student's
     * marks - so a role change is a delete and a create, which is a decision the
     * administrator should have to make deliberately.
     */
    @Transactional
    public UserView update(Long userId, UpdateUserRequest request) {
        UserAccount user = require(userId);

        String username = request.username().trim();
        String email = request.email().trim();
        if (users.existsByUsernameAndIdNot(username, userId)) {
            throw new ApiExceptions.ConflictException("Username already taken: " + username);
        }
        if (users.existsByEmailAndIdNot(email, userId)) {
            throw new ApiExceptions.ConflictException("Email already registered: " + email);
        }

        user.setUsername(username);
        user.setEmail(email);
        user.setFullName(request.fullName().trim());

        boolean passwordChanged = false;
        String password = request.password();
        if (password != null && !password.isBlank()) {
            if (password.length() < MIN_PASSWORD_LENGTH) {
                throw new ApiExceptions.BadRequestException(
                        "A new password must be at least " + MIN_PASSWORD_LENGTH + " characters");
            }
            user.setPasswordHash(passwordEncoder.encode(password));
            passwordChanged = true;
        }
        users.save(user);

        if (passwordChanged) {
            // Whoever holds the old password must not keep a live session.
            refreshTokens.revokeAllForUser(userId);
            log.info("Admin reset the password for {}; all sessions revoked", user.getUsername());
        }

        Student student = studentOf(user);
        Teacher teacher = teacherOf(user);

        if (student != null) {
            // Kept in step deliberately: the student record's own copy of the
            // name is what class lists and the CSV import match against.
            student.setFullName(user.getFullName());
            if (hasText(request.className())) {
                student.setClassName(request.className().trim());
            }
            student.setSection(hasText(request.section()) ? request.section().trim() : null);
            if (hasText(request.academicYear())) {
                student.setAcademicYear(request.academicYear().trim());
            }
            students.save(student);
        }

        if (teacher != null) {
            teacher.setFullName(user.getFullName());
            if (hasText(request.staffNo())) {
                String staffNo = request.staffNo().trim();
                teachers.findByStaffNo(staffNo)
                        .filter(other -> !other.getId().equals(teacher.getId()))
                        .ifPresent(other -> {
                            throw new ApiExceptions.ConflictException(
                                    "Staff number already in use: " + staffNo);
                        });
                teacher.setStaffNo(staffNo);
            }
            teacher.setDepartment(hasText(request.department()) ? request.department().trim() : null);
            teachers.save(teacher);
        }

        return toView(user, student, teacher, Map.of());
    }

    // -- Activate / deactivate -----------------------------------------------

    /**
     * Turns an account on or off.
     *
     * <p>Disabling revokes the refresh tokens so no new access token can be
     * minted, and the JWT filter's own {@code enabled} check retires the access
     * token already in the user's hands on its next request. Without the first
     * half the session would survive until the refresh token expired; without
     * the second, until the access token did.
     */
    @Transactional
    public UserView setEnabled(Long userId, boolean enabled) {
        UserAccount user = require(userId);

        if (!enabled) {
            assertNotSelf(userId, "deactivate");
        }

        if (user.isEnabled() != enabled) {
            user.setEnabled(enabled);
            users.save(user);

            if (!enabled) {
                refreshTokens.revokeAllForUser(userId);
            }
            log.info("Admin {} account {}", enabled ? "activated" : "deactivated", user.getUsername());
        }

        return toView(user, studentOf(user), teacherOf(user), Map.of());
    }

    // -- Delete --------------------------------------------------------------

    /**
     * Deletes an account.
     *
     * <p>What happens to the companion record differs by role, and the
     * difference is the point:
     *
     * <ul>
     *   <li><b>STUDENT</b> - the student record is <em>kept</em> and unlinked.
     *       Marks, analytics and AI feedback reference the student, not the
     *       login, so removing the record would erase a child's academic history
     *       because somebody tidied up a login. The record stays and can be
     *       given a new login later.</li>
     *   <li><b>TEACHER</b> - the subject assignments and the teacher record go
     *       with the login. They describe a member of staff who no longer exists
     *       and hold no marks of their own.</li>
     *   <li><b>ADMIN</b> - nothing else to remove.</li>
     * </ul>
     *
     * <p>An account that has imported a results file is refused:
     * {@code upload_jobs.uploaded_by} is a non-null foreign key, so deleting the
     * account would mean deleting the record of who loaded which marks.
     * Deactivation is offered instead.
     */
    @Transactional
    public void delete(Long userId) {
        UserAccount user = require(userId);

        assertNotSelf(userId, "delete");

        long imports = uploadJobs.countByUploadedBy(userId);
        if (imports > 0) {
            throw new ApiExceptions.ConflictException(
                    ("%s has %d result import%s on record and cannot be deleted without losing that "
                            + "history. Deactivate the account instead.")
                            .formatted(user.getUsername(), imports, imports == 1 ? "" : "s"));
        }

        Student student = studentOf(user);
        if (student != null) {
            student.setUser(null);
            students.save(student);
        }

        Teacher teacher = teacherOf(user);
        if (teacher != null) {
            teacherSubjects.deleteByTeacherId(teacher.getId());
            teachers.delete(teacher);
        }

        refreshTokens.deleteAllByUserId(userId);

        // Every row holding a foreign key onto the login has to be gone before
        // the login itself; flush so that ordering is what reaches the database,
        // not whatever order Hibernate would otherwise choose at commit.
        refreshTokens.flush();
        students.flush();
        teachers.flush();
        users.delete(user);

        log.info("Admin deleted {} account {}", user.getRole(), user.getUsername());
    }

    // -- Helpers -------------------------------------------------------------

    private UserAccount require(Long userId) {
        return users.findById(userId)
                .orElseThrow(() -> ApiExceptions.NotFoundException.of("User", userId));
    }

    private Student studentOf(UserAccount user) {
        return user.getRole() == Role.STUDENT
                ? students.findByUserId(user.getId()).orElse(null)
                : null;
    }

    private Teacher teacherOf(UserAccount user) {
        return user.getRole() == Role.TEACHER
                ? teachers.findByUserId(user.getId()).orElse(null)
                : null;
    }

    /** Links to an existing student by admission number, or creates the record. */
    private Student attachStudent(UserAccount user, CreateUserRequest request) {
        if (!hasText(request.admissionNo())) {
            throw new ApiExceptions.BadRequestException(
                    "admissionNo is required when creating a STUDENT account");
        }
        String admissionNo = request.admissionNo().trim();

        Optional<Student> existing = students.findByAdmissionNo(admissionNo);
        if (existing.isPresent()) {
            Student student = existing.get();
            if (student.getUser() != null) {
                throw new ApiExceptions.ConflictException(
                        "Student " + admissionNo + " already has a login");
            }
            student.setUser(user);
            return students.save(student);
        }

        if (!hasText(request.className()) || !hasText(request.academicYear())) {
            throw new ApiExceptions.BadRequestException(
                    "No student record exists for admission number " + admissionNo
                            + ", so className and academicYear are required to create one");
        }

        Student created = new Student(
                admissionNo,
                user.getFullName(),
                request.className().trim(),
                hasText(request.section()) ? request.section().trim() : null,
                request.academicYear().trim());
        created.setUser(user);
        return students.save(created);
    }

    private Teacher createTeacher(UserAccount user, CreateUserRequest request) {
        if (!hasText(request.staffNo())) {
            throw new ApiExceptions.BadRequestException(
                    "staffNo is required when creating a TEACHER account");
        }
        String staffNo = request.staffNo().trim();
        if (teachers.findByStaffNo(staffNo).isPresent()) {
            throw new ApiExceptions.ConflictException("Staff number already in use: " + staffNo);
        }
        return teachers.save(new Teacher(
                user,
                staffNo,
                user.getFullName(),
                hasText(request.department()) ? request.department().trim() : null));
    }

    /**
     * An admin acting destructively on their own account is almost always a
     * mistake, it locks them out of the screen they are standing on - and
     * refusing it is what guarantees the platform always has one working
     * administrator.
     */
    private void assertNotSelf(Long userId, String action) {
        if (userId.equals(accessGuard.currentUserId())) {
            throw new ApiExceptions.BadRequestException(
                    "You cannot " + action + " your own account");
        }
    }

    private boolean matches(UserAccount user, String needle) {
        return user.getUsername().toLowerCase().contains(needle)
                || user.getEmail().toLowerCase().contains(needle)
                || user.getFullName().toLowerCase().contains(needle);
    }

    private UserView toView(UserAccount user,
                            Student student,
                            Teacher teacher,
                            Map<Long, Integer> assignmentCounts) {
        Integer assignments = null;
        if (teacher != null) {
            Integer counted = assignmentCounts.get(teacher.getId());
            assignments = (counted != null) ? counted : (int) teacherSubjects.countByTeacherId(teacher.getId());
        }

        return new UserView(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getFullName(),
                user.getRole(),
                user.isEnabled(),
                user.getCreatedAt(),
                user.getUpdatedAt(),
                student != null ? student.getId() : null,
                student != null ? student.getAdmissionNo() : null,
                student != null ? student.getClassName() : null,
                student != null ? student.getSection() : null,
                student != null ? student.getAcademicYear() : null,
                teacher != null ? teacher.getId() : null,
                teacher != null ? teacher.getStaffNo() : null,
                teacher != null ? teacher.getDepartment() : null,
                assignments,
                (int) uploadJobs.countByUploadedBy(user.getId()));
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
