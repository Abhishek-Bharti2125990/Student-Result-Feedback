package com.srip.batch;

import com.srip.repository.ExamResultRepository;
import com.srip.service.AnalyticsWriteService;
import com.srip.service.ClassInsightService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Step 4 of the import: turn the marks that were just written into each
 * student's standing - percentage, grade, rank, category, strengths and
 * weaknesses - and store it.
 *
 * <p>Runs after the marks are committed, not alongside them. A rank is a
 * property of the whole class, so it cannot be computed while rows are still
 * arriving: a student ranked in chunk 1 would be ranked against a third of the
 * class.
 *
 * <p>Scope is the (student, exam) pairs this upload touched, read back from the
 * {@code upload_job_id} stamp on the result rows. A file covering one class must
 * not trigger a recompute of the whole school.
 *
 * <p>The cached class rankings are evicted first. They were computed from the
 * previous marks, and ranking every student against stale totals would be a
 * silent wrong answer, which is worse than a slow one.
 */
@Component
@StepScope
public class AnalyticsGenerationTasklet implements Tasklet {

    private static final Logger log = LoggerFactory.getLogger(AnalyticsGenerationTasklet.class);

    private final ExamResultRepository examResults;
    private final AnalyticsWriteService analyticsWriter;
    private final ClassInsightService classInsights;
    private final Long uploadJobId;

    public AnalyticsGenerationTasklet(ExamResultRepository examResults,
                                      AnalyticsWriteService analyticsWriter,
                                      ClassInsightService classInsights,
                                      @Value("#{jobParameters['uploadJobId']}") Long uploadJobId) {
        this.examResults = examResults;
        this.analyticsWriter = analyticsWriter;
        this.classInsights = classInsights;
        this.uploadJobId = uploadJobId;
    }

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        classInsights.invalidateCaches();

        Set<StudentExam> pairs = touchedStudentExams();
        int stored = 0;
        for (StudentExam pair : pairs) {
            if (analyticsWriter.computeAndStore(pair.studentId(), pair.examId(), uploadJobId) != null) {
                stored++;
            }
        }

        log.info("Analytics generated for {} of {} student/exam pair(s) in upload job {}",
                stored, pairs.size(), uploadJobId);
        return RepeatStatus.FINISHED;
    }

    private Set<StudentExam> touchedStudentExams() {
        List<Object[]> rows = examResults.findStudentExamPairsForUploadJob(uploadJobId);
        Set<StudentExam> pairs = new LinkedHashSet<>(rows.size());
        for (Object[] row : rows) {
            pairs.add(new StudentExam((Long) row[0], (Long) row[1]));
        }
        return pairs;
    }

    record StudentExam(Long studentId, Long examId) {
    }
}
