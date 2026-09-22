package com.srip.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** One rejected CSV row, kept so the uploader can correct the source file. */
@Entity
@Table(name = "upload_errors")
public class UploadError {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "upload_job_id", nullable = false)
    private Long uploadJobId;

    @Column(name = "line_number", nullable = false)
    private int lineNumber;

    @Column(name = "raw_line", length = 1000)
    private String rawLine;

    @Column(nullable = false, length = 1000)
    private String message;

    protected UploadError() {
        // required by JPA
    }

    public UploadError(Long uploadJobId, int lineNumber, String rawLine, String message) {
        this.uploadJobId = uploadJobId;
        this.lineNumber = lineNumber;
        this.rawLine = truncate(rawLine, 1000);
        this.message = truncate(message, 1000);
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }

    public Long getId() {
        return id;
    }

    public Long getUploadJobId() {
        return uploadJobId;
    }

    public int getLineNumber() {
        return lineNumber;
    }

    public String getRawLine() {
        return rawLine;
    }

    public String getMessage() {
        return message;
    }
}
