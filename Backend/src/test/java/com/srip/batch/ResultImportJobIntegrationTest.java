package com.srip.batch;

import com.srip.analytics.ScoreCategory;
import com.srip.domain.Role;
import com.srip.domain.Student;
import com.srip.domain.StudentAnalytics;
import com.srip.domain.UploadJob;
import com.srip.dto.ai.FeedbackDtos.FeedbackEnvelope;
import com.srip.dto.ai.FeedbackDtos.StudentFeedback;
import com.srip.dto.analytics.AnalyticsDtos.ClassAnalytics;
import com.srip.dto.analytics.AnalyticsDtos.ClassTopicWeakness;
import com.srip.dto.analytics.AnalyticsDtos.ExamReport;
import com.srip.dto.analytics.AnalyticsDtos.PerformanceTrend;
import com.srip.dto.analytics.AnalyticsDtos.RankingEntry;
import com.srip.dto.analytics.AnalyticsDtos.WeakTopic;
import com.srip.dto.dashboard.DashboardDtos.CategoryBucket;
import com.srip.dto.dashboard.DashboardDtos.ResourceSuggestion;
import com.srip.dto.dashboard.DashboardDtos.StudentCategoryCard;
import com.srip.dto.dashboard.DashboardDtos.StudentDashboard;
import com.srip.dto.dashboard.DashboardDtos.TeacherDashboard;
import com.srip.dto.dashboard.DashboardDtos.TopicHighlight;
import com.srip.dto.result.ResultDtos.UploadJobView;
import com.srip.repository.AiFeedbackRepository;
import com.srip.repository.ExamRepository;
import com.srip.repository.ExamResultRepository;
import com.srip.repository.StudentAnalyticsRepository;
import com.srip.repository.StudentRepository;
import com.srip.repository.TopicScoreRepository;
import com.srip.repository.UploadErrorRepository;
import com.srip.repository.UploadJobRepository;
import com.srip.repository.UserAccountRepository;
import com.srip.service.AnalyticsService;
import com.srip.service.ClassInsightService;
import com.srip.service.FeedbackService;
import com.srip.service.ResultUploadService;
import com.srip.service.StudentDashboardService;
import com.srip.service.TeacherDashboardService;
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
 * <p>Result data, analytics and the caches are cleared before each test. The
 * context is shared for speed, and without the reset one test's import would
 * silently change another's expected counts - the kind of order-dependent
 * flakiness that is painful to diagnose later.
 */
@SpringBootTest
@ActiveProfiles("test")
class ResultImportJobIntegrationTest {

    private static final String UNIT_TEST_1 = "samples/results-unit-test-1.csv";
    private static final String MIDTERM = "samples/results-midterm.csv";
    private static final String WITH_ERRORS = "samples/results-with-errors.csv";

    /** 8 students x 24 topic rows. */
    private static final int TOPIC_ROWS = 192;

    /** 8 students x 5 subjects. */
    private static final int SUBJECT_PAPERS = 40;

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
    private StudentAnalyticsRepository storedAnalytics;

    @Autowired
    private AiFeedbackRepository aiFeedback;

    @Autowired
    private AnalyticsService analytics;

    @Autowired
    private ClassInsightService classInsights;

    @Autowired
    private FeedbackService feedback;

    @Autowired
    private StudentDashboardService studentDashboard;

    @Autowired
    private TeacherDashboardService teacherDashboard;

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
        storedAnalytics.deleteAllInBatch();
        examResults.deleteAllInBatch();
        uploadErrors.deleteAllInBatch();
        aiFeedback.deleteAllInBatch();
        uploadJobs.deleteAllInBatch();

        cacheManager.getCacheNames().forEach(name -> cacheManager.getCache(name).clear());
    }

    @Test
    void importsACleanFileAndComputesTheWholeAnalyticsChain() throws Exception {
        JobExecution execution = runImport(UNIT_TEST_1);
        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);

        // All five steps: header, reference data, import, analytics, insights.
        assertThat(execution.getStepExecutions()).hasSize(5);

        Long examId = examId("UNIT-TEST-1");
        assertThat(examResults.findForExamAndClass(examId, "10")).hasSize(SUBJECT_PAPERS);
        assertThat(topicScores.count()).isEqualTo(TOPIC_ROWS);

        Student ayushman = student("1001");
        ExamReport report = analytics.examReport(ayushman.getId(), examId);

        assertThat(report.totalMarks()).isEqualByComparingTo("477");
        assertThat(report.totalMaxMarks()).isEqualByComparingTo("600");
        assertThat(report.overallPercentage()).isEqualByComparingTo("79.50");
        assertThat(report.overallGrade()).isEqualTo("B+");
        assertThat(report.category()).isEqualTo(ScoreCategory.GOOD);
        assertThat(report.passed()).isTrue();
        assertThat(report.classSize()).isEqualTo(8);
        assertThat(report.subjects()).hasSize(5);
        // Every row of the file is a topic, so all 24 are present.
        assertThat(report.topics()).hasSize(24);
    }

    @Test
    void subjectTotalsAreTheSumOfTheirTopicRows() throws Exception {
        runImport(UNIT_TEST_1);

        Long examId = examId("UNIT-TEST-1");
        Student ayushman = student("1001");

        // Mathematics is 6 topics of 25 marks; 97 of 150 is 64.67%. The file
        // never states a subject total, so this is the writer's arithmetic.
        assertThat(analytics.examReport(ayushman.getId(), examId).subjects())
                .filteredOn(subject -> subject.subjectName().equals("Mathematics"))
                .singleElement()
                .satisfies(maths -> {
                    assertThat(maths.marksObtained()).isEqualByComparingTo("97");
                    assertThat(maths.maxMarks()).isEqualByComparingTo("150");
                    assertThat(maths.percentage()).isEqualByComparingTo("64.67");
                });
    }

    @Test
    void createsStudentsSubjectsAndTopicsNamedOnlyInTheFile() throws Exception {
        Path file = Files.createTempFile("new-people", ".csv");
        Files.writeString(file, """
                student_id,student_name,class_name,section,exam_name,subject,chapter_name,topic_name,marks_obtained,maximum_marks
                2001,Nikhil Joshi,9,C,Practice Test,Applied Physics,Mechanics,Vectors,18,25
                2001,Nikhil Joshi,9,C,Practice Test,Applied Physics,Mechanics,Momentum,20,25
                """);

        JobExecution execution = launch(file, newUploadJob());
        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);

        // A file is allowed to introduce people and curriculum the database has
        // never seen; that is how a new class is onboarded.
        Student nikhil = student("2001");
        assertThat(nikhil.getFullName()).isEqualTo("Nikhil Joshi");
        assertThat(nikhil.getClassName()).isEqualTo("9");
        assertThat(nikhil.getSection()).isEqualTo("C");

        Long examId = examId("PRACTICE-TEST");
        assertThat(analytics.examReport(nikhil.getId(), examId).subjects())
                .singleElement()
                .satisfies(subject -> {
                    assertThat(subject.subjectName()).isEqualTo("Applied Physics");
                    assertThat(subject.marksObtained()).isEqualByComparingTo("38");
                    assertThat(subject.maxMarks()).isEqualByComparingTo("50");
                });
    }

    @Test
    void rankingsPlaceTheTopScorerFirstAndCoverTheWholeClass() throws Exception {
        runImport(UNIT_TEST_1);
        Long examId = examId("UNIT-TEST-1");

        List<RankingEntry> rankings = classInsights.rankings(examId, "10");

        assertThat(rankings).hasSize(8);
        assertThat(rankings.get(0).admissionNo()).isEqualTo("1006");
        assertThat(rankings.get(0).rank()).isEqualTo(1);
        assertThat(rankings.get(rankings.size() - 1).admissionNo()).isEqualTo("1005");
        assertThat(rankings).extracting(RankingEntry::percentage)
                .isSortedAccordingTo(Comparator.reverseOrder());
    }

    @Test
    void analyticsRowsAreStoredForEveryStudentWithTheirCategoryAndRank() throws Exception {
        runImport(UNIT_TEST_1);
        Long examId = examId("UNIT-TEST-1");

        List<StudentAnalytics> rows =
                storedAnalytics.findByExamIdAndClassNameOrderByRankInClassAsc(examId, "10");

        assertThat(rows).hasSize(8);
        assertThat(rows).extracting(StudentAnalytics::getRankInClass)
                .containsExactly(1, 2, 3, 4, 5, 6, 7, 8);
        assertThat(rows).allSatisfy(row -> assertThat(row.getClassSize()).isEqualTo(8));

        StudentAnalytics ayushman = storedAnalytics
                .findByStudentIdAndExamId(student("1001").getId(), examId)
                .orElseThrow();

        assertThat(ayushman.getPercentage()).isEqualByComparingTo("79.50");
        assertThat(ayushman.getCategory()).isEqualTo(ScoreCategory.GOOD);
        assertThat(ayushman.getRankInClass()).isEqualTo(3);
        assertThat(ayushman.getWeakTopicsJson()).contains("Quadratic Equations");
        assertThat(ayushman.getStrongTopicsJson()).contains("Motion");
    }

    @Test
    void classAnalyticsBucketEveryStudentIntoAScoreCategory() throws Exception {
        runImport(UNIT_TEST_1);
        Long examId = examId("UNIT-TEST-1");

        ClassAnalytics classReport = classInsights.classAnalytics(examId, "10");

        assertThat(classReport.classSize()).isEqualTo(8);
        assertThat(classReport.subjectStats()).hasSize(5);
        assertThat(classReport.highestPercentage()).isGreaterThan(classReport.lowestPercentage());

        // 1005 at 42.67%, 1003 and 1007 in the fifties, three in the seventies,
        // 1002 and 1006 at 89%.
        assertThat(classReport.categoryCounts())
                .extracting(count -> count.category() + "=" + count.students())
                .containsExactly("CRITICAL=1", "AVERAGE=2", "GOOD=3", "EXCELLENT=2");

        assertThat(classReport.strugglingStudents())
                .extracting(student -> student.admissionNo())
                .contains("1005");
    }

    @Test
    void theTopicMostOfTheClassFailedIsSurfacedFirst() throws Exception {
        runImport(UNIT_TEST_1);
        Long examId = examId("UNIT-TEST-1");

        List<ClassTopicWeakness> weakest = classInsights.weakestTopics(examId, "10");

        // Five of eight students are weak on quadratic equations and four on
        // trigonometry, so those are teaching problems rather than nine
        // individual ones - and the order says which lesson to plan first.
        assertThat(weakest).isNotEmpty();
        assertThat(weakest.get(0).topicName()).isEqualTo("Quadratic Equations");
        assertThat(weakest.get(0).weakStudents()).isEqualTo(5);
        assertThat(weakest.get(0).subjectName()).isEqualTo("Mathematics");
        assertThat(weakest.get(0).chapterName()).isEqualTo("Algebra");
        assertThat(weakest).extracting(ClassTopicWeakness::topicName).contains("Trigonometry");
    }

    @Test
    void theTeacherDashboardSplitsTheClassIntoTheFourBuckets() throws Exception {
        runImport(UNIT_TEST_1);

        // Neither class nor exam is supplied: after one upload the dashboard
        // should resolve both, because that is the state a demo is in.
        TeacherDashboard dashboard = teacherDashboard.dashboard(null, null);

        assertThat(dashboard.className()).isEqualTo("10");
        assertThat(dashboard.examName()).isEqualTo("Unit Test 1");
        assertThat(dashboard.totalStudents()).isEqualTo(8);
        assertThat(dashboard.buckets()).hasSize(4);
        assertThat(dashboard.buckets()).extracting(CategoryBucket::studentCount)
                .containsExactly(1L, 2L, 3L, 2L);
        assertThat(dashboard.weakestTopics()).isNotEmpty();
        assertThat(dashboard.aiGuidance()).isNotNull();

        CategoryBucket critical = dashboard.buckets().get(0);
        assertThat(critical.category()).isEqualTo(ScoreCategory.CRITICAL);
        assertThat(critical.label()).isEqualTo("Students Below 50%");
        assertThat(critical.students()).singleElement().satisfies(card -> {
            assertThat(card.studentName()).isEqualTo("Aman Verma");
            assertThat(card.percentage()).isEqualByComparingTo("42.67");
            assertThat(card.weakTopics()).isNotEmpty();
            assertThat(card.suggestedAction()).isNotBlank();
        });
    }

    @Test
    void eachCategoryEndpointReturnsOnlyItsOwnStudents() throws Exception {
        runImport(UNIT_TEST_1);

        List<StudentCategoryCard> excellent =
                teacherDashboard.studentsInCategory(ScoreCategory.EXCELLENT, "10", null);

        assertThat(excellent).hasSize(2);
        assertThat(excellent).extracting(StudentCategoryCard::studentName)
                .containsExactlyInAnyOrder("Diya Patel", "Meera Krishnan");
        assertThat(excellent).allSatisfy(card ->
                assertThat(card.category()).isEqualTo(ScoreCategory.EXCELLENT));

        // Worst score first inside a bucket: that is who to look at first.
        List<StudentCategoryCard> good =
                teacherDashboard.studentsInCategory(ScoreCategory.GOOD, "10", null);
        assertThat(good).extracting(StudentCategoryCard::percentage)
                .isSortedAccordingTo(Comparator.naturalOrder());
    }

    @Test
    void theStudentDashboardMarksStrongAndWeakTopicsDifferently() throws Exception {
        runImport(UNIT_TEST_1);

        StudentDashboard dashboard = studentDashboard.dashboard(student("1001").getId(), null);

        assertThat(dashboard.studentName()).isEqualTo("Ayushman Sharma");
        assertThat(dashboard.percentage()).isEqualByComparingTo("79.50");
        assertThat(dashboard.category()).isEqualTo(ScoreCategory.GOOD);
        assertThat(dashboard.categoryLabel()).isEqualTo("Students Between 70 and 85");
        assertThat(dashboard.rankInClass()).isEqualTo(3);
        assertThat(dashboard.classSize()).isEqualTo(8);

        assertThat(dashboard.weakTopics()).extracting(TopicHighlight::topic)
                .contains("Quadratic Equations", "Trigonometry");
        assertThat(dashboard.weakTopics()).allSatisfy(topic ->
                assertThat(topic.marker()).isEqualTo(TopicHighlight.WEAK_MARKER));

        assertThat(dashboard.strongTopics()).extracting(TopicHighlight::topic).contains("Motion");
        assertThat(dashboard.strongTopics()).allSatisfy(topic ->
                assertThat(topic.marker()).isEqualTo(TopicHighlight.STRONG_MARKER));

        // The chapter comes through, so advice can say which chapter to revise.
        assertThat(dashboard.weakTopics())
                .filteredOn(topic -> topic.topic().equals("Quadratic Equations"))
                .singleElement()
                .satisfies(topic -> {
                    assertThat(topic.chapter()).isEqualTo("Algebra");
                    assertThat(topic.subject()).isEqualTo("Mathematics");
                });
    }

    @Test
    void weakTopicsAreMatchedToCuratedBooksAndVideos() throws Exception {
        runImport(UNIT_TEST_1);

        List<ResourceSuggestion> resources =
                studentDashboard.resources(student("1001").getId(), null);

        assertThat(resources).isNotEmpty();
        assertThat(resources).extracting(ResourceSuggestion::topic)
                .containsOnly("Quadratic Equations", "Trigonometry");
        assertThat(resources).extracting(ResourceSuggestion::resourceType)
                .contains("BOOK", "VIDEO");
        // Worst topic first, so the first suggestion is the one that matters.
        assertThat(resources.get(0).topic()).isEqualTo("Quadratic Equations");
        assertThat(resources).allSatisfy(resource ->
                assertThat(resource.title()).isNotBlank());
    }

    @Test
    void aSecondExamProducesATrendAndRepeatedWeakTopics() throws Exception {
        runImport(UNIT_TEST_1);
        runImport(MIDTERM);

        Student ayushman = student("1001");

        PerformanceTrend trend = analytics.trendOf(ayushman.getId());
        assertThat(trend.overall()).extracting(point -> point.examCode())
                .containsExactly("UNIT-TEST-1", "MIDTERM");

        // Quadratic equations were weak in both exams, which is the case worth
        // surfacing: a repeat gap, not one bad morning.
        List<WeakTopic> weakTopics = analytics.weakTopicsOf(ayushman.getId());
        assertThat(weakTopics).extracting(WeakTopic::topicName).contains("Quadratic Equations");
        assertThat(weakTopics.stream()
                .filter(topic -> topic.topicName().equals("Quadratic Equations"))
                .findFirst().orElseThrow()
                .occurrences()).isEqualTo(2);

        // Maths fell while the other subjects held or rose, so it sorts first.
        assertThat(trend.bySubject()).extracting(subject -> subject.subjectCode())
                .first().isEqualTo("MATHEMATICS");
    }

    @Test
    void reuploadingCorrectsAMarkInsteadOfDuplicatingIt() throws Exception {
        runImport(UNIT_TEST_1);
        runImport(UNIT_TEST_1);

        Long examId = examId("UNIT-TEST-1");
        assertThat(examResults.findForExamAndClass(examId, "10")).hasSize(SUBJECT_PAPERS);
        assertThat(topicScores.count()).isEqualTo(TOPIC_ROWS);
        assertThat(storedAnalytics.findByExamIdAndClassNameOrderByRankInClassAsc(examId, "10"))
                .hasSize(8);
    }

    @Test
    void reuploadingAFileThatDropsATopicDoesNotLeaveTheOldOneBehind() throws Exception {
        runImport(UNIT_TEST_1);

        // The same paper, re-issued with one topic instead of six. The subject
        // total must follow the new file, not keep the marks it no longer names.
        Path corrected = Files.createTempFile("corrected", ".csv");
        Files.writeString(corrected, """
                student_id,student_name,class_name,section,exam_name,subject,chapter_name,topic_name,marks_obtained,maximum_marks
                1001,Ayushman Sharma,10,A,Unit Test 1,Mathematics,Algebra,Quadratic Equations,22,25
                """);
        launch(corrected, newUploadJob());

        Long examId = examId("UNIT-TEST-1");
        assertThat(analytics.examReport(student("1001").getId(), examId).subjects())
                .filteredOn(subject -> subject.subjectName().equals("Mathematics"))
                .singleElement()
                .satisfies(maths -> {
                    assertThat(maths.marksObtained()).isEqualByComparingTo("22");
                    assertThat(maths.maxMarks()).isEqualByComparingTo("25");
                    assertThat(maths.percentage()).isEqualByComparingTo("88.00");
                });
    }

    @Test
    void badRowsAreRejectedIndividuallyAndReportedWithTheirLineNumbers() throws Exception {
        Long uploadJobId = newUploadJob();
        launch(copyToTempFile(WITH_ERRORS), uploadJobId);

        UploadJobView status = uploadService.status(uploadJobId);

        assertThat(status.status()).isEqualTo(UploadJob.Status.COMPLETED_WITH_ERRORS);
        // The sample has 12 data rows: 2 valid, 10 deliberately broken.
        assertThat(status.totalRecords()).isEqualTo(12);
        assertThat(status.validRecords()).isEqualTo(2);
        assertThat(status.invalidRecords()).isEqualTo(10);
        assertThat(status.errors()).hasSize(10);
        assertThat(status.errors()).allSatisfy(error ->
                assertThat(error.message()).isNotBlank());
        assertThat(status.errors()).extracting(error -> error.message())
                .anySatisfy(message -> assertThat(message).contains("exceeds maximum_marks"))
                .anySatisfy(message -> assertThat(message).contains("must be a number"))
                .anySatisfy(message -> assertThat(message).contains("must be greater than zero"))
                .anySatisfy(message -> assertThat(message).contains("cannot be negative"))
                .anySatisfy(message -> assertThat(message).contains("student_name is required"))
                .anySatisfy(message -> assertThat(message).contains("student_id is required"))
                .anySatisfy(message -> assertThat(message).contains("chapter_name is required"))
                .anySatisfy(message -> assertThat(message).contains("Expected 10 columns"));
    }

    @Test
    void feedbackFallsBackToTheLocalWriterWhenNoApiKeyIsConfigured() throws Exception {
        runImport(UNIT_TEST_1);

        Student ayushman = student("1001");
        Long examId = examId("UNIT-TEST-1");

        FeedbackEnvelope envelope = feedback.studentFeedback(ayushman.getId(), examId, true);

        assertThat(envelope.source()).isEqualTo("FALLBACK");
        assertThat(envelope.audience()).isEqualTo("STUDENT");
        assertThat(envelope.feedback()).isInstanceOf(StudentFeedback.class);

        StudentFeedback body = (StudentFeedback) envelope.feedback();
        assertThat(body.summary()).contains("79.5");
        assertThat(body.strengths()).isNotEmpty();
        assertThat(body.weaknesses()).isNotEmpty();
        assertThat(body.studyPlan()).isNotEmpty();
    }

    @Test
    void theStudyPlanOnlyEverNamesACuratedResource() throws Exception {
        runImport(UNIT_TEST_1);

        FeedbackEnvelope envelope = feedback.studentFeedback(
                student("1001").getId(), examId("UNIT-TEST-1"), true);
        StudentFeedback body = (StudentFeedback) envelope.feedback();

        List<String> curatedTitles = studentDashboard
                .resources(student("1001").getId(), null).stream()
                .map(ResourceSuggestion::title)
                .toList();

        // An invented book title is the fastest way to lose a teacher's trust,
        // so anything named here has to come from the library.
        assertThat(body.studyPlan())
                .filteredOn(item -> item.resource() != null)
                .isNotEmpty()
                .allSatisfy(item -> assertThat(curatedTitles).contains(item.resource()));
    }

    @Test
    void theImportJobWritesFeedbackForEveryStudentAndTheClass() throws Exception {
        runImport(UNIT_TEST_1);

        // The final step generates documents so a dashboard opened straight
        // after an upload is not blank.
        assertThat(aiFeedback.count()).isEqualTo(9);
        assertThat(feedback.storedTeacherFeedback(examId("UNIT-TEST-1"), "10")).isPresent();
        assertThat(aiFeedback.findByStudentIdOrderByGeneratedAtDesc(student("1001").getId()))
                .hasSize(1);
    }

    @Test
    void storedFeedbackIsServedBackWithoutRegenerating() throws Exception {
        runImport(UNIT_TEST_1);

        Student ayushman = student("1001");
        Long examId = examId("UNIT-TEST-1");

        FeedbackEnvelope first = feedback.studentFeedback(ayushman.getId(), examId, true);
        FeedbackEnvelope second = feedback.studentFeedback(ayushman.getId(), examId, false);

        assertThat(first.cached()).isFalse();
        assertThat(second.generatedAt()).isEqualTo(first.generatedAt());
    }

    @Test
    void aFileWithTheWrongHeaderFailsOnceRatherThanPerRow() throws Exception {
        Path file = Files.createTempFile("bad-header", ".csv");
        Files.writeString(file, """
                student,exam,subject,marks,total
                1001,Midterm,Mathematics,78,100
                """);

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

    private Student student(String studentId) {
        return students.findByAdmissionNo(studentId).orElseThrow();
    }

    private Long examId(String examCode) {
        return exams.findByCode(examCode).orElseThrow().getId();
    }

    private Path copyToTempFile(String classpathLocation) throws Exception {
        Path target = Files.createTempFile("srip-import", ".csv");
        try (InputStream in = new ClassPathResource(classpathLocation).getInputStream()) {
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        }
        return target;
    }
}
