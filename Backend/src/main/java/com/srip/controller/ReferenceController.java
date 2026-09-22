package com.srip.controller;

import com.srip.dto.admin.AdminDtos.ExamView;
import com.srip.dto.admin.AdminDtos.StudentView;
import com.srip.dto.admin.AdminDtos.SubjectView;
import com.srip.dto.admin.AdminDtos.TopicView;
import com.srip.service.AccessGuard;
import com.srip.service.SchoolAdminService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Reference data every client needs in order to build a request - exam ids,
 * subject codes, topic names.
 *
 * <p>The exam and subject lists are open to any authenticated caller: they
 * contain no personal data, and a student cannot ask for a report without an
 * exam id. Student rosters are staff-only, since a class list is personal data
 * even without marks attached - hence the method-level checks rather than one
 * blanket URL rule.
 */
@RestController
@RequestMapping("/api/reference")
public class ReferenceController {

    private final SchoolAdminService schoolAdmin;
    private final AccessGuard accessGuard;

    public ReferenceController(SchoolAdminService schoolAdmin, AccessGuard accessGuard) {
        this.schoolAdmin = schoolAdmin;
        this.accessGuard = accessGuard;
    }

    @GetMapping("/exams")
    public List<ExamView> exams() {
        return schoolAdmin.allExams();
    }

    @GetMapping("/subjects")
    public List<SubjectView> subjects() {
        return schoolAdmin.allSubjects();
    }

    @GetMapping("/subjects/{subjectCode}/topics")
    public List<TopicView> topics(@PathVariable String subjectCode) {
        return schoolAdmin.topicsOfSubject(subjectCode);
    }

    @GetMapping("/students")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
    public List<StudentView> students() {
        return schoolAdmin.allStudents();
    }

    @GetMapping("/classes/{className}/students")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
    public List<StudentView> studentsInClass(@PathVariable String className) {
        return schoolAdmin.studentsInClass(className);
    }

    /** The children linked to the calling parent account. */
    @GetMapping("/my-children")
    @PreAuthorize("hasRole('PARENT')")
    public List<StudentView> myChildren() {
        return schoolAdmin.childrenOf(accessGuard.currentUserId());
    }
}
