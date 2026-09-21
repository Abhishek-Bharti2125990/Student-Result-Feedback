package com.srip.controller;

import com.srip.dto.admin.AdminDtos.LinkCreated;
import com.srip.dto.admin.AdminDtos.ParentLinkRequest;
import com.srip.dto.admin.AdminDtos.StudentView;
import com.srip.dto.admin.AdminDtos.TeacherSubjectRequest;
import com.srip.service.SchoolAdminService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Administrative operations. ADMIN only, enforced by the URL rule on
 * {@code /api/admin/**}.
 *
 * <p>The parent link is the only thing standing between a parent and another
 * family's results, so creating one is deliberately an admin action rather than
 * something a parent can self-serve.
 */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final SchoolAdminService schoolAdmin;

    public AdminController(SchoolAdminService schoolAdmin) {
        this.schoolAdmin = schoolAdmin;
    }

    @GetMapping("/students")
    public List<StudentView> students() {
        return schoolAdmin.allStudents();
    }

    @PostMapping("/parent-links")
    public ResponseEntity<LinkCreated> linkParent(@Valid @RequestBody ParentLinkRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(schoolAdmin.linkParentToStudent(request));
    }

    @PostMapping("/teacher-subjects")
    public ResponseEntity<LinkCreated> assignTeacherSubject(
            @Valid @RequestBody TeacherSubjectRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(schoolAdmin.assignTeacherSubject(request));
    }
}
