package com.srip.controller;

import com.srip.dto.admin.AdminDtos.LinkCreated;
import com.srip.dto.admin.AdminDtos.StudentView;
import com.srip.dto.admin.AdminDtos.TeacherSubjectRequest;
import com.srip.dto.result.ResultDtos.UploadAccepted;
import com.srip.dto.result.ResultDtos.UploadJobView;
import com.srip.service.ResultUploadService;
import com.srip.service.SchoolAdminService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * Administrative operations. ADMIN only, enforced by the URL rule on
 * {@code /api/admin/**}.
 */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final SchoolAdminService schoolAdmin;
    private final ResultUploadService uploadService;

    public AdminController(SchoolAdminService schoolAdmin, ResultUploadService uploadService) {
        this.schoolAdmin = schoolAdmin;
        this.uploadService = uploadService;
    }

    /**
     * Uploads a result CSV.
     *
     * <p>Returns 202 Accepted with a job id, not the finished import: a
     * class-wide file takes longer than a reasonable HTTP timeout once the
     * analytics and feedback steps are counted. Poll
     * {@code GET /api/admin/upload/{id}} for progress and the per-line error
     * report.
     */
    @PostMapping(path = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<UploadAccepted> upload(@RequestParam("file") MultipartFile file) {
        UploadAccepted accepted = uploadService.accept(file);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(accepted);
    }

    @GetMapping("/upload/{uploadJobId}")
    public UploadJobView uploadStatus(@PathVariable Long uploadJobId) {
        return uploadService.status(uploadJobId);
    }

    @GetMapping("/upload")
    public List<UploadJobView> uploadHistory() {
        return uploadService.history();
    }

    @GetMapping("/students")
    public List<StudentView> students() {
        return schoolAdmin.allStudents();
    }

    @PostMapping("/teacher-subjects")
    public ResponseEntity<LinkCreated> assignTeacherSubject(
            @Valid @RequestBody TeacherSubjectRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(schoolAdmin.assignTeacherSubject(request));
    }
}
