package com.srip.controller;

import com.srip.dto.result.ResultDtos.UploadAccepted;
import com.srip.dto.result.ResultDtos.UploadJobView;
import com.srip.service.ResultUploadService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * CSV result ingestion. Restricted to TEACHER and ADMIN in {@code SecurityConfig}.
 *
 * <p>The upload returns 202 Accepted with a job id, not the finished import.
 * A class-wide file can take longer than a reasonable HTTP timeout, so the
 * client polls {@code GET /api/uploads/{id}} for progress and the per-line
 * error report.
 */
@RestController
@RequestMapping("/api/uploads")
public class UploadController {

    private final ResultUploadService uploadService;

    public UploadController(ResultUploadService uploadService) {
        this.uploadService = uploadService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<UploadAccepted> upload(@RequestParam("file") MultipartFile file) {
        UploadAccepted accepted = uploadService.accept(file);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(accepted);
    }

    @GetMapping("/{uploadJobId}")
    public UploadJobView status(@PathVariable Long uploadJobId) {
        return uploadService.status(uploadJobId);
    }

    @GetMapping
    public List<UploadJobView> history() {
        return uploadService.history();
    }
}
