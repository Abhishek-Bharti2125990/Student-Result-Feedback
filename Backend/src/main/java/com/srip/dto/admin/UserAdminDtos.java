package com.srip.dto.admin;

import com.srip.domain.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/** Admin user management: the list row and the create, edit and status requests. */
public final class UserAdminDtos {

    private UserAdminDtos() {
    }

    /**
     * One account with its companion record flattened in.
     *
     * <p>A user is never just a login here: a STUDENT owns a row in
     * {@code students} and a TEACHER one in {@code teachers}, and the admin
     * screen has to show both halves. Flattening them into one view keeps the
     * list to a single request rather than one per row.
     */
    public record UserView(
            Long id,
            String username,
            String email,
            String fullName,
            Role role,
            boolean enabled,
            Instant createdAt,
            Instant updatedAt,

            /** STUDENT only: the student record this login owns. */
            Long studentId,
            String admissionNo,
            String className,
            String section,
            String academicYear,

            /** TEACHER only: the teacher record created with the login. */
            Long teacherId,
            String staffNo,
            String department,

            /** Subject/class assignments the teacher holds, for the delete warning. */
            Integer subjectAssignments,

            /** Imports this account started. Non-zero makes the account undeletable. */
            Integer uploadJobs
    ) {
    }

    /**
     * Creates an account and, for a student or teacher, the companion record.
     *
     * <p>Which of the optional fields are required depends on {@code role}; the
     * service rejects a request that omits one it needs rather than quietly
     * creating a half-formed record. For a STUDENT, {@code admissionNo} that
     * already exists <em>links</em> to that child's record - results are loaded
     * by admission number long before logins exist - and one that does not is
     * created, which is when {@code className} and {@code academicYear} are
     * needed.
     */
    public record CreateUserRequest(
            @NotBlank @Size(min = 3, max = 64) String username,
            @NotBlank @Email @Size(max = 160) String email,
            @NotBlank @Size(min = 8, max = 72) String password,
            @NotBlank @Size(max = 120) String fullName,
            @NotNull Role role,

            /** Defaults to active when absent. */
            Boolean enabled,

            @Size(max = 32) String admissionNo,
            @Size(max = 16) String className,
            @Size(max = 8) String section,
            @Size(max = 16) String academicYear,

            @Size(max = 32) String staffNo,
            @Size(max = 64) String department
    ) {
    }

    /**
     * Edits an account. The role is deliberately absent: moving an account
     * between roles would orphan the companion record it already owns - a
     * teacher's subject assignments, or a student's marks - so a role change is
     * a delete and a create, not an edit.
     *
     * <p>{@code password} is optional. Blank or absent leaves the current
     * password alone; a value replaces it and signs the user out everywhere.
     */
    public record UpdateUserRequest(
            @NotBlank @Size(min = 3, max = 64) String username,
            @NotBlank @Email @Size(max = 160) String email,
            @NotBlank @Size(max = 120) String fullName,
            @Size(max = 72) String password,

            @Size(max = 16) String className,
            @Size(max = 8) String section,
            @Size(max = 16) String academicYear,

            @Size(max = 32) String staffNo,
            @Size(max = 64) String department
    ) {
    }

    public record UserStatusRequest(@NotNull Boolean enabled) {
    }
}
