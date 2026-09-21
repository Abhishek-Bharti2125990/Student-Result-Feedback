package com.srip.dto.result;

import com.srip.domain.UploadJob;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** Payloads for CSV upload and for reading stored results. */
public final class ResultDtos {

    private ResultDtos() {
    }

    /**
     * Returned immediately by the upload endpoint. Processing continues in the
     * background, so the caller polls {@code /api/uploads/{id}} with this id.
     */
    public record UploadAccepted(
            Long uploadJobId,
            String filename,
            int dataRowCount,
            UploadJob.Status status,
            String message
    ) {
    }

    public record UploadJobView(
            Long id,
            String filename,
            UploadJob.Status status,
            int totalRecords,
            int validRecords,
            int invalidRecords,
            Long jobExecutionId,
            String failureMessage,
            Instant createdAt,
            Instant completedAt,
            List<UploadErrorView> errors
    ) {
    }

    public record UploadErrorView(int lineNumber, String rawLine, String message) {
    }

    public record TopicScoreView(
            String topicName,
            BigDecimal marksObtained,
            BigDecimal maxMarks,
            BigDecimal percentage
    ) {
    }

    public record ExamResultView(
            Long id,
            String examCode,
            String examName,
            String subjectCode,
            String subjectName,
            BigDecimal marksObtained,
            BigDecimal maxMarks,
            BigDecimal percentage,
            String grade,
            boolean attempted,
            String remarks,
            List<TopicScoreView> topicScores
    ) {
    }
}
