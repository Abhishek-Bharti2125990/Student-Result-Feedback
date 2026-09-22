package com.srip.config;

import com.srip.batch.CsvHeaderValidationTasklet;
import com.srip.batch.ExamResultWriter;
import com.srip.batch.RejectedRowListener;
import com.srip.batch.ResultCsvLineMapper;
import com.srip.batch.ResultCsvRow;
import com.srip.batch.ResultRowProcessor;
import com.srip.batch.RowValidationException;
import com.srip.batch.UploadJobStatusListener;
import com.srip.domain.ExamResult;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.launch.support.TaskExecutorJobLauncher;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.file.FlatFileItemReader;
import org.springframework.batch.item.file.FlatFileParseException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * The CSV import job.
 *
 * <p>Two steps, in order:
 * <ol>
 *   <li>{@code validateCsvHeaderStep} - fails the whole job once if the columns
 *       are wrong, rather than rejecting every row for the same reason.</li>
 *   <li>{@code importResultsStep} - chunked read, validate, write, tolerating
 *       individual bad rows.</li>
 * </ol>
 *
 * <p>The import step is fault tolerant with an effectively unlimited skip
 * budget. That is the right trade for this data: a school file frequently has a
 * handful of unknown admission numbers or a blank mark, and importing the other
 * 890 rows while reporting the 10 failures is far more useful than rejecting
 * the file. Every skip is recorded, so nothing is lost silently.
 */
@Configuration
public class BatchConfig {

    /** Rows per transaction. Large enough to be efficient, small enough that a
     *  rollback caused by one bad row replays only a little work. */
    private static final int CHUNK_SIZE = 50;

    public static final String JOB_NAME = "resultImportJob";

    @Bean
    public Job resultImportJob(JobRepository jobRepository,
                               Step validateCsvHeaderStep,
                               Step importResultsStep,
                               UploadJobStatusListener statusListener) {
        return new JobBuilder(JOB_NAME, jobRepository)
                .listener(statusListener)
                .start(validateCsvHeaderStep)
                .next(importResultsStep)
                .build();
    }

    @Bean
    public Step validateCsvHeaderStep(JobRepository jobRepository,
                                      PlatformTransactionManager transactionManager,
                                      CsvHeaderValidationTasklet tasklet) {
        return new StepBuilder("validateCsvHeaderStep", jobRepository)
                .tasklet(tasklet, transactionManager)
                .build();
    }

    @Bean
    public Step importResultsStep(JobRepository jobRepository,
                                  PlatformTransactionManager transactionManager,
                                  FlatFileItemReader<ResultCsvRow> resultCsvReader,
                                  ResultRowProcessor processor,
                                  ExamResultWriter writer,
                                  RejectedRowListener rejectedRowListener) {
        return new StepBuilder("importResultsStep", jobRepository)
                .<ResultCsvRow, ExamResult>chunk(CHUNK_SIZE, transactionManager)
                .reader(resultCsvReader)
                .processor(processor)
                .writer(writer)
                .faultTolerant()
                // Skip the two failures that are a property of one row, and
                // nothing else: an infrastructure error must still fail the job.
                .skip(RowValidationException.class)
                .skip(FlatFileParseException.class)
                .skipLimit(Integer.MAX_VALUE)
                .listener(rejectedRowListener)
                .build();
    }

    /**
     * Reads the staged file named in the job parameters. Step-scoped because
     * the path is only known at launch time.
     */
    @Bean
    @StepScope
    public FlatFileItemReader<ResultCsvRow> resultCsvReader(
            @Value("#{jobParameters['filePath']}") String filePath) {

        FlatFileItemReader<ResultCsvRow> reader = new FlatFileItemReader<>();
        reader.setName("resultCsvReader");
        reader.setResource(new FileSystemResource(filePath));
        reader.setLinesToSkip(1);
        reader.setStrict(true);
        reader.setLineMapper(new ResultCsvLineMapper());
        return reader;
    }

    /**
     * Launches jobs on a separate thread so the upload request can return 202
     * immediately instead of holding the HTTP connection open for the duration
     * of the import.
     */
    @Bean
    public JobLauncher asyncJobLauncher(JobRepository jobRepository) throws Exception {
        TaskExecutorJobLauncher launcher = new TaskExecutorJobLauncher();
        launcher.setJobRepository(jobRepository);
        launcher.setTaskExecutor(new SimpleAsyncTaskExecutor("result-import-"));
        launcher.afterPropertiesSet();
        return launcher;
    }
}
