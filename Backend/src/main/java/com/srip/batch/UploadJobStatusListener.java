package com.srip.batch;

import com.srip.domain.UploadJob;
import com.srip.repository.UploadErrorRepository;
import com.srip.repository.UploadJobRepository;
import com.srip.service.ClassInsightService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionListener;
import org.springframework.batch.core.StepExecution;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Keeps the {@code upload_jobs} row in step with the batch execution.
 *
 * <p>This is what makes the upload endpoint's fire-and-poll contract work: the
 * HTTP call returns a job id immediately, and this listener is the only thing
 * that moves that row from PENDING to a terminal state with real counts.
 *
 * <p>It also invalidates the class-level analytics caches on completion.
 * Without that, a freshly imported exam would keep serving the ranking table
 * computed from the previous marks - a silent wrong answer, which is worse than
 * a slow one.
 */
@Component
public class UploadJobStatusListener implements JobExecutionListener {

    private static final Logger log = LoggerFactory.getLogger(UploadJobStatusListener.class);

    static final String PARAM_UPLOAD_JOB_ID = "uploadJobId";

    private final UploadJobRepository uploadJobs;
    private final UploadErrorRepository uploadErrors;
    private final ClassInsightService classInsights;

    public UploadJobStatusListener(UploadJobRepository uploadJobs,
                                   UploadErrorRepository uploadErrors,
                                   ClassInsightService classInsights) {
        this.uploadJobs = uploadJobs;
        this.uploadErrors = uploadErrors;
        this.classInsights = classInsights;
    }

    @Override
    @Transactional
    public void beforeJob(JobExecution jobExecution) {
        uploadJobId(jobExecution).flatMap(uploadJobs::findById).ifPresent(job -> {
            job.setStatus(UploadJob.Status.RUNNING);
            job.setJobExecutionId(jobExecution.getId());
            uploadJobs.save(job);
        });
    }

    @Override
    @Transactional
    public void afterJob(JobExecution jobExecution) {
        uploadJobId(jobExecution).flatMap(uploadJobs::findById).ifPresent(job -> {
            int written = 0;
            int rowsSeen = 0;
            for (StepExecution step : jobExecution.getStepExecutions()) {
                written += (int) step.getWriteCount();
                // A row that fails to parse is never "read", so it has to be
                // added back in for the total to match the file's row count.
                rowsSeen += (int) step.getReadCount() + (int) step.getReadSkipCount();
            }

            // The error table is the authority on rejects: it counts read-time
            // parse failures as well as processor rejections.
            int invalid = (int) uploadErrors.countByUploadJobId(job.getId());

            job.setValidRecords(written);
            job.setInvalidRecords(invalid);
            job.setTotalRecords(rowsSeen);
            job.setCompletedAt(Instant.now());
            job.setStatus(terminalStatus(jobExecution, invalid));

            if (jobExecution.getStatus() == BatchStatus.FAILED) {
                job.setFailureMessage(jobExecution.getAllFailureExceptions().stream()
                        .findFirst()
                        .map(Throwable::toString)
                        .orElse("Job failed without a reported exception"));
            }

            uploadJobs.save(job);

            log.info("Upload job {} finished: status={} rows={} written={} rejected={}",
                    job.getId(), job.getStatus(), rowsSeen, written, invalid);
        });

        if (jobExecution.getStatus() != BatchStatus.FAILED) {
            classInsights.invalidateCaches();
        }
    }

    private UploadJob.Status terminalStatus(JobExecution jobExecution, int invalid) {
        if (jobExecution.getStatus() == BatchStatus.FAILED) {
            return UploadJob.Status.FAILED;
        }
        return invalid > 0 ? UploadJob.Status.COMPLETED_WITH_ERRORS : UploadJob.Status.COMPLETED;
    }

    private java.util.Optional<Long> uploadJobId(JobExecution jobExecution) {
        return java.util.Optional.ofNullable(
                jobExecution.getJobParameters().getLong(PARAM_UPLOAD_JOB_ID));
    }
}
