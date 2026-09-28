package com.srip.controller;

import com.srip.dto.admin.AdminDtos.ExamView;
import com.srip.dto.admin.AdminDtos.StudentView;
import com.srip.dto.admin.AdminDtos.SubjectView;
import com.srip.dto.admin.AdminDtos.TopicView;
import com.srip.dto.dashboard.DashboardDtos.ResourceSuggestion;
import com.srip.service.SchoolAdminService;
import com.srip.service.StudyResourceService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Reference data every client needs in order to build a request - exam ids,
 * subject codes, topic names, the study-resource library.
 *
 * <p>The exam, subject and resource lists are open to any authenticated caller:
 * they contain no personal data, and a student cannot ask for a report without
 * an exam id. Student rosters are staff-only, since a class list is personal
 * data even without marks attached - hence the method-level checks rather than
 * one blanket URL rule.
 */
@RestController
@RequestMapping("/api/reference")
public class ReferenceController {

    private final SchoolAdminService schoolAdmin;
    private final StudyResourceService studyResources;

    public ReferenceController(SchoolAdminService schoolAdmin, StudyResourceService studyResources) {
        this.schoolAdmin = schoolAdmin;
        this.studyResources = studyResources;
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

    /**
     * The whole study-resource library, or one subject's slice of it.
     *
     * @param subject optional subject name, e.g. {@code Mathematics}
     */
    @GetMapping("/study-resources")
    public List<ResourceSuggestion> studyResources(@RequestParam(required = false) String subject) {
        return subject == null || subject.isBlank()
                ? studyResources.all()
                : studyResources.forSubject(subject);
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
}
