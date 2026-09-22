package com.srip.dto.admin;

import jakarta.validation.constraints.NotBlank;

import java.time.LocalDate;

/** Reference-data views and the admin link requests. */
public final class AdminDtos {

    private AdminDtos() {
    }

    public record StudentView(
            Long id,
            String admissionNo,
            String fullName,
            String className,
            String section,
            String academicYear,
            boolean hasLogin
    ) {
    }

    public record ExamView(
            Long id,
            String code,
            String name,
            String term,
            LocalDate examDate,
            String className,
            String academicYear
    ) {
    }

    public record SubjectView(Long id, String code, String name, String className) {
    }

    public record TopicView(Long id, String name, String subjectCode) {
    }

    /** Grants a parent login read access to one child. */
    public record ParentLinkRequest(
            @NotBlank String parentUsername,
            @NotBlank String admissionNo,
            String relationship
    ) {
    }

    /** Records which class and subject a teacher is responsible for. */
    public record TeacherSubjectRequest(
            @NotBlank String staffNo,
            @NotBlank String subjectCode,
            @NotBlank String className
    ) {
    }

    public record LinkCreated(Long id, String message) {
    }
}
