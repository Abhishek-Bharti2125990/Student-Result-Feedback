package com.srip.service;

import com.srip.config.BatchConfig;
import com.srip.config.UploadProperties;
import com.srip.domain.UploadJob;
import com.srip.dto.result.ResultDtos.UploadAccepted;
import com.srip.dto.result.ResultDtos.UploadErrorView;
import com.srip.dto.result.ResultDtos.UploadJobView;
import com.srip.exception.ApiExceptions;
import com.srip.repository.UploadErrorRepository;
import com.srip.repository.UploadJobRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Accepts a result CSV, stages it on disk, and launches the import job.
 *
 * <p>The file is written to disk before the job starts rather than streamed
 * from the request. That decouples the two: the HTTP call returns as soon as
 * the bytes are safe, and a failed or interrupted job can be re-run against the
 * same file without asking the uploader to send it again.
 *
 * <p>Cheap structural checks - empty file, wrong extension, no data rows - run
 * here so the caller gets a synchronous 4xx with a clear reason. Per-row
 * validation belongs in the batch job, where each failure can be attributed to
 * a line.
 */
@Service
public class ResultUploadService {

    private static final Logger log = LoggerFactory.getLogger(ResultUploadService.class);
    private static final long MAX_ERRORS_RETURNED = 200;

    private final UploadProperties properties;
    private final UploadJobRepository uploadJobs;
    private final UploadErrorRepository uploadErrors;
    private final JobLauncher jobLauncher;
    private final Job resultImportJob;
    private final AccessGuard accessGuard;

    public ResultUploadService(UploadProperties properties,
                               UploadJobRepository uploadJobs,
                               UploadErrorRepository uploadErrors,
                               @Qualifier("asyncJobLauncher") JobLauncher jobLauncher,
                               Job resultImportJob,
                               AccessGuard accessGuard) {
        this.properties = properties;
        this.uploadJobs = uploadJobs;
        this.uploadErrors = uploadErrors;
        this.jobLauncher = jobLauncher;
        this.resultImportJob = resultImportJob;
        this.accessGuard = accessGuard;
    }

    /**
     * Deliberately not {@code @Transactional}.
     *
     * <p>Spring Batch's {@code JobRepository} refuses to run inside a caller's
     * transaction, because the job execution must be committed and visible
     * before the job thread starts reading it. Each repository call below
     * commits on its own, which is exactly what is needed here: the
     * {@code upload_jobs} row has to be durable before the job is launched.
     */
    public UploadAccepted accept(MultipartFile file) {
        String originalName = requireCsv(file);
        Path stored = stage(file, originalName);
        int dataRows = countDataRows(stored);

        if (dataRows == 0) {
            deleteQuietly(stored);
            throw new ApiExceptions.CsvValidationException(
                    "The file contains a header but no data rows");
        }

        UploadJob job = uploadJobs.save(new UploadJob(
                originalName, stored.toAbsolutePath().toString(), accessGuard.currentUserId()));

        launch(job, stored);

        return new UploadAccepted(job.getId(), originalName, dataRows, job.getStatus(),
                "Accepted for processing. Poll /api/uploads/" + job.getId() + " for progress.");
    }

    @Transactional(readOnly = true)
    public UploadJobView status(Long uploadJobId) {
        UploadJob job = uploadJobs.findById(uploadJobId)
                .orElseThrow(() -> ApiExceptions.NotFoundException.of("Upload job", uploadJobId));

        List<UploadErrorView> errors = uploadErrors.findByUploadJobIdOrderByLineNumberAsc(uploadJobId).stream()
                .limit(MAX_ERRORS_RETURNED)
                .map(error -> new UploadErrorView(error.getLineNumber(), error.getRawLine(), error.getMessage()))
                .toList();

        return new UploadJobView(
                job.getId(),
                job.getOriginalFilename(),
                job.getStatus(),
                job.getTotalRecords(),
                job.getValidRecords(),
                job.getInvalidRecords(),
                job.getJobExecutionId(),
                job.getFailureMessage(),
                job.getCreatedAt(),
                job.getCompletedAt(),
                errors);
    }

    @Transactional(readOnly = true)
    public List<UploadJobView> history() {
        return uploadJobs.findAllByOrderByCreatedAtDesc().stream()
                .map(job -> new UploadJobView(
                        job.getId(),
                        job.getOriginalFilename(),
                        job.getStatus(),
                        job.getTotalRecords(),
                        job.getValidRecords(),
                        job.getInvalidRecords(),
                        job.getJobExecutionId(),
                        job.getFailureMessage(),
                        job.getCreatedAt(),
                        job.getCompletedAt(),
                        List.of()))
                .toList();
    }

    private void launch(UploadJob job, Path stored) {
        JobParameters parameters = new JobParametersBuilder()
                .addLong("uploadJobId", job.getId())
                .addString("filePath", stored.toAbsolutePath().toString())
                // Spring Batch identifies a job instance by its identifying
                // parameters. Without a unique value, re-uploading the same
                // file would be rejected as an already-complete instance.
                .addLong("requestedAt", Instant.now().toEpochMilli())
                .toJobParameters();

        try {
            jobLauncher.run(resultImportJob, parameters);
        } catch (Exception e) {
            job.setStatus(UploadJob.Status.FAILED);
            job.setFailureMessage("Could not start the import job: " + e.getMessage());
            job.setCompletedAt(Instant.now());
            uploadJobs.save(job);
            log.error("Failed to launch {} for upload job {}", BatchConfig.JOB_NAME, job.getId(), e);
            throw new ApiExceptions.BadRequestException(
                    "Could not start the import job: " + e.getMessage());
        }
    }

    private String requireCsv(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ApiExceptions.CsvValidationException("No file was uploaded");
        }
        String name = file.getOriginalFilename();
        if (name == null || name.isBlank()) {
            throw new ApiExceptions.CsvValidationException("The uploaded file has no name");
        }
        if (!name.toLowerCase().endsWith(".csv")) {
            throw new ApiExceptions.CsvValidationException("Only .csv files are accepted, received: " + name);
        }
        return name;
    }

    private Path stage(MultipartFile file, String originalName) {
        try {
            Path directory = Path.of(properties.directory());
            Files.createDirectories(directory);

            // The stored name is generated, never taken from the upload: an
            // attacker-supplied name could otherwise traverse the directory.
            Path target = directory.resolve("%d-%s.csv".formatted(
                    Instant.now().toEpochMilli(), UUID.randomUUID()));

            try (InputStream in = file.getInputStream()) {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }
            log.info("Staged upload '{}' at {}", originalName, target);
            return target;
        } catch (IOException e) {
            throw new ApiExceptions.CsvValidationException(
                    "Could not store the uploaded file: " + e.getMessage());
        }
    }

    /** @return the number of non-blank lines after the header */
    private int countDataRows(Path path) {
        try (var lines = Files.lines(path, StandardCharsets.UTF_8)) {
            return (int) lines.skip(1).filter(line -> !line.isBlank()).count();
        } catch (IOException e) {
            deleteQuietly(path);
            throw new ApiExceptions.CsvValidationException(
                    "Could not read the uploaded file as UTF-8 text: " + e.getMessage());
        }
    }

    private void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            log.warn("Could not delete staged file {}", path, e);
        }
    }
}
