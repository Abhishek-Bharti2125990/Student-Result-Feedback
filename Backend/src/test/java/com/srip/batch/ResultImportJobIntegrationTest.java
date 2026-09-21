package com.srip.batch;

import com.srip.domain.Role;
import com.srip.domain.Student;
import com.srip.domain.UploadJob;
import com.srip.dto.ai.FeedbackDtos.FeedbackEnvelope;
import com.srip.dto.ai.FeedbackDtos.StudentFeedback;
import com.srip.dto.analytics.AnalyticsDtos.ClassAnalytics;
import com.srip.dto.analytics.AnalyticsDtos.ExamReport;
import com.srip.dto.analytics.AnalyticsDtos.PerformanceTrend;
import com.srip.dto.analytics.AnalyticsDtos.RankingEntry;
import com.srip.dto.analytics.AnalyticsDtos.WeakTopic;
import com.srip.dto.result.ResultDtos.UploadJobView;
import com.srip.repository.AiFeedbackRepository;
import com.srip.repository.ExamRepository;
import com.srip.repository.ExamResultRepository;
import com.srip.repository.StudentRepository;
import com.srip.repository.TopicScoreRepository;
import com.srip.repository.UploadErrorRepository;
import com.srip.repository.UploadJobRepository;
import com.srip.repository.UserAccountRepository;
import com.srip.service.AnalyticsService;
import com.srip.service.ClassInsightService;
import com.srip.service.FeedbackService;
import com.srip.service.ResultUploadService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.support.TaskExecutorJobLauncher;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.test.context.ActiveProfiles;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end check of the import job and everything that reads from it.
 *
 * <p>The job is launched through a synchronous launcher built here rather than
 * the application's async one, so assertions run against a finished import
 * instead of racing it.
 *
 * <p>Result data and the analytics caches are cleared before each test. The
 * context is shared for speed, and without the reset one test's import would
 * silently change another's expected counts - the kind of order-dependent
 * flakiness that is painful to diagnose later.
 */
@SpringBootTest
@ActiveProfiles("test")
class ResultImportJobIntegrationTest {

    @Autowired
    private Job resultImportJob;

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    private UploadJobRepository uploadJobs;

    @Autowired
    private UploadErrorRepository uploadErrors;

    @Autowired
    private UserAccountRepository users;

    @Autowired
    private StudentRepository students;

    @Autowired
    private ExamRepository exams;

    @Autowired
    private ExamResultRepository examResults;

    @Autowired
    private TopicScoreRepository topicScores;

    @Autowired
    private AiFeedbackRepository aiFeedback;

    @Autowired
    private AnalyticsService analytics;

    @Autowired
    private ClassInsightService classInsights;

    @Autowired
    private FeedbackService feedback;

    @Autowired
    private ResultUploadService uploadService;

    @Autowired
    private CacheManager cacheManager;

    private TaskExecutorJobLauncher syncLauncher;
    private final AtomicLong runCounter = new AtomicLong();

    @BeforeEach
    void setUp() throws Exception {
        syncLauncher = new TaskExecutorJobLauncher();
        syncLauncher.setJobRepository(jobRepository);
        syncLauncher.setTaskExecutor(new SyncTaskExecutor());
        syncLauncher.afterPropertiesSet();

        // Child rows first: the foreign keys are real.
        topicScores.deleteAllInBatch();
        examResults.deleteAllInBatch();
        uploadErrors.deleteAllInBatch();
        aiFeedback.deleteAllInBatch();
        uploadJobs.deleteAllInBatch();

        cacheManager.getCacheNames().forEach(name -> cacheManager.getCache(name).clear());
    }

    @Test
    void importsACleanFileAndComputesTheWholeAnalyticsChain() throws Exception {
        JobExecution execution = runImport("samples/results-UT1-2025.csv");
        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);

        // 8 students x 5 subjects.
        Long examId = exams.findByCode("UT1-2025").orElseThrow().getId();
        assertThat(examResults.findForExamAndClass(examId, "10")).hasSize(40);

        Student aarav = students.findByAdmissionNo("STU1001").orElseThrow();
        ExamReport report = analytics.examReport(aarav.getId(), examId);

        assertThat(report.totalMarks()).isEqualByComparingTo("414");
        assertThat(report.totalMaxMarks()).isEqualByComparingTo("500");
        assertThat(report.overallPercentage()).isEqualByComparingTo("82.80");
        assertThat(report.overallGrade()).isEqualTo("A");
        assertThat(report.passed()).isTrue();
        assertThat(report.classSize()).isEqualTo(8);
        assertThat(report.subjects()).hasSize(5);
        // The sample supplies a topic breakdown for maths and science only.
        assertThat(report.topics()).hasSize(8);
    }

    @Test
    void rankingsPlaceTheTopScorerFirstAndCoverTheWholeClass() throws Exception {
        runImport("samples/results-UT1-2025.csv");
        Long examId = exams.findByCode("UT1-2025").orElseThrow().getId();

        List<RankingEntry> rankings = classInsights.rankings(examId, "10");

        assertThat(rankings).hasSize(8);
        assertThat(rankings.get(0).admissionNo()).isEqualTo("STU1002");
        assertThat(rankings.get(0).rank()).isEqualTo(1);
        assertThat(rankings.get(rankings.size() - 1).admissionNo()).isEqualTo("STU1005");
        assertThat(rankings).extracting(RankingEntry::percentage)
                .isSortedAccordingTo(Comparator.reverseOrder());
    }

    @Test
    void classAnalyticsFlagTheFailingStudent() throws Exception {
        runImport("samples/results-UT1-2025.csv");
        Long examId = exams.findByCode("UT1-2025").orElseThrow().getId();

        ClassAnalytics classReport = classInsights.classAnalytics(examId, "10");

        assertThat(classReport.classSize()).isEqualTo(8);
        assertThat(classReport.strugglingStudents())
                .extracting(student -> student.admissionNo())
                .contains("STU1005");
        assertThat(classReport.subjectStats()).hasSize(5);
        assertThat(classReport.highestPercentage()).isGreaterThan(classReport.lowestPercentage());
    }

    @Test
    void aSecondExamProducesATrendAndRepeatedWeakTopics() throws Exception {
        runImport("samples/results-UT1-2025.csv");
        runImport("samples/results-MID-2025.csv");

        Student aarav = students.findByAdmissionNo("STU1001").orElseThrow();

        PerformanceTrend trend = analytics.trendOf(aarav.getId());
        assertThat(trend.overall()).extracting(point -> point.examCode())
                .containsExactly("UT1-2025", "MID-2025");

        // Algebra was weak in both exams, which is the case worth surfacing.
        List<WeakTopic> weakTopics = analytics.weakTopicsOf(aarav.getId());
        assertThat(weakTopics).extracting(WeakTopic::topicName).contains("Algebra");
        assertThat(weakTopics.stream()
                .filter(topic -> topic.topicName().equals("Algebra"))
                .findFirst().orElseThrow()
                .occurrences()).isEqualTo(2);

        // Maths fell while the other subjects held or rose, so it sorts first.
        assertThat(trend.bySubject()).extracting(subject -> subject.subjectCode())
                .first().isEqualTo("MATH");
    }

    @Test
    void reuploadingCorrectsAMarkInsteadOfDuplicatingIt() throws Exception {
        runImport("samples/results-UT1-2025.csv");
        runImport("samples/results-UT1-2025.csv");

        Long examId = exams.findByCode("UT1-2025").orElseThrow().getId();
        assertThat(examResults.findForExamAndClass(examId, "10")).hasSize(40);
    }

    @Test
    void badRowsAreRejectedIndividuallyAndReportedWithTheirLineNumbers() throws Exception {
        Long uploadJobId = newUploadJob();
        launch(copyToTempFile("samples/results-with-errors.csv"), uploadJobId);

        UploadJobView status = uploadService.status(uploadJobId);

        assertThat(status.status()).isEqualTo(UploadJob.Status.COMPLETED_WITH_ERRORS);
        // The sample has 13 data rows: 2 valid, 11 deliberately broken.
        assertThat(status.totalRecords()).isEqualTo(13);
        assertThat(status.validRecords()).isEqualTo(2);
        assertThat(status.invalidRecords()).isEqualTo(11);
        assertThat(status.errors()).hasSize(11);
        assertThat(status.errors()).allSatisfy(error ->
                assertThat(error.message()).isNotBlank());
        assertThat(status.errors()).extracting(error -> error.message())
                .anySatisfy(message -> assertThat(message).contains("Unknown admission_no"))
                .anySatisfy(message -> assertThat(message).contains("Unknown exam_code"))
                .anySatisfy(message -> assertThat(message).contains("exceeds max_marks"))
                .anySatisfy(message -> assertThat(message).contains("must be a number"))
                .anySatisfy(message -> assertThat(message).contains("Topic:got/max"));
    }

    @Test
    void feedbackFallsBackToTheLocalWriterWhenNoApiKeyIsConfigured() throws Exception {
        runImport("samples/results-UT1-2025.csv");

        Student aarav = students.findByAdmissionNo("STU1001").orElseThrow();
        Long examId = exams.findByCode("UT1-2025").orElseThrow().getId();

        FeedbackEnvelope envelope = feedback.studentFeedback(aarav.getId(), examId, true);

        assertThat(envelope.source()).isEqualTo("FALLBACK");
        assertThat(envelope.audience()).isEqualTo("STUDENT");
        assertThat(envelope.feedback()).isInstanceOf(StudentFeedback.class);

        StudentFeedback body = (StudentFeedback) envelope.feedback();
        assertThat(body.summary()).contains("82.8");
        assertThat(body.strengths()).isNotEmpty();
        assertThat(body.weaknesses()).isNotEmpty();
        assertThat(body.studyPlan()).isNotEmpty();
    }

    @Test
    void storedFeedbackIsServedBackWithoutRegenerating() throws Exception {
        runImport("samples/results-UT1-2025.csv");

        Student aarav = students.findByAdmissionNo("STU1001").orElseThrow();
        Long examId = exams.findByCode("UT1-2025").orElseThrow().getId();

        FeedbackEnvelope first = feedback.parentFeedback(aarav.getId(), examId, true);
        FeedbackEnvelope second = feedback.parentFeedback(aarav.getId(), examId, false);

        assertThat(first.cached()).isFalse();
        assertThat(second.generatedAt()).isEqualTo(first.generatedAt());
    }

    @Test
    void aFileWithTheWrongHeaderFailsOnceRatherThanPerRow() throws Exception {
        Path file = Files.createTempFile("bad-header", ".csv");
        Files.writeString(file, "student,exam,subject,marks,total\nSTU1001,UT1-2025,MATH,78,100\n");

        Long uploadJobId = newUploadJob();
        JobExecution execution = launch(file, uploadJobId);

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.FAILED);
        assertThat(execution.getAllFailureExceptions())
                .anySatisfy(failure -> assertThat(failure.getMessage()).contains("Unexpected CSV header"));
        assertThat(uploadService.status(uploadJobId).status()).isEqualTo(UploadJob.Status.FAILED);
        assertThat(uploadErrors.countByUploadJobId(uploadJobId)).isZero();
    }

    // -- Helpers -------------------------------------------------------------

    private JobExecution runImport(String classpathSample) throws Exception {
        return launch(copyToTempFile(classpathSample), newUploadJob());
    }

    private JobExecution launch(Path file, Long uploadJobId) throws Exception {
        JobParameters parameters = new JobParametersBuilder()
                .addLong("uploadJobId", uploadJobId)
                .addString("filePath", file.toAbsolutePath().toString())
                .addLong("requestedAt", Instant.now().toEpochMilli() + runCounter.incrementAndGet())
                .toJobParameters();
        return syncLauncher.run(resultImportJob, parameters);
    }

    private Long newUploadJob() {
        Long adminId = users.findByRole(Role.ADMIN).get(0).getId();
        return uploadJobs.save(new UploadJob("test.csv", "test.csv", adminId)).getId();
    }

    private Path copyToTempFile(String classpathLocation) throws Exception {
        Path target = Files.createTempFile("srip-import", ".csv");
        try (InputStream in = new ClassPathResource(classpathLocation).getInputStream()) {
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        }
        return target;
    }
}
