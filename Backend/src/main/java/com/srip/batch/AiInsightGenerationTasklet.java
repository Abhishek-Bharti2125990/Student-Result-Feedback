package com.srip.batch;

import com.srip.config.ClaudeProperties;
import com.srip.domain.Student;
import com.srip.exception.ApiExceptions;
import com.srip.repository.ExamResultRepository;
import com.srip.repository.StudentRepository;
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
 * Step 5 of the import: write the student and teacher feedback documents.
 *
 * <p>Generating here rather than on first read means a teacher who opens the
 * dashboard after an upload sees advice immediately, instead of waiting on a
 * model call while the page loads.
 *
 * <p>A failure never fails the job. The marks and the analytics are the
 * deliverable; advice about them is valuable but secondary, and losing a
 * finished import because an external API was briefly unavailable would be the
 * wrong trade. Each document is generated in its own transaction - see
 * {@link FeedbackGenerationRunner} - so stepping over one failure is safe.
 */
@Component
@StepScope
public class AiInsightGenerationTasklet implements Tasklet {

    private static final Logger log = LoggerFactory.getLogger(AiInsightGenerationTasklet.class);

    private final ExamResultRepository examResults;
    private final StudentRepository students;
    private final FeedbackGenerationRunner generator;
    private final ClaudeProperties claudeProperties;
    private final Long uploadJobId;

    public AiInsightGenerationTasklet(ExamResultRepository examResults,
                                      StudentRepository students,
                                      FeedbackGenerationRunner generator,
                                      ClaudeProperties claudeProperties,
                                      @Value("#{jobParameters['uploadJobId']}") Long uploadJobId) {
        this.examResults = examResults;
        this.students = students;
        this.generator = generator;
        this.claudeProperties = claudeProperties;
        this.uploadJobId = uploadJobId;
    }

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        if (!claudeProperties.generateOnImport()) {
            log.info("Skipping AI insight generation: app.claude.generate-on-import is false");
            return RepeatStatus.FINISHED;
        }

        List<Object[]> pairs = examResults.findStudentExamPairsForUploadJob(uploadJobId);
        Set<ClassExam> classExams = new LinkedHashSet<>();

        int studentDocuments = 0;
        for (Object[] pair : pairs) {
            Long studentId = (Long) pair[0];
            Long examId = (Long) pair[1];

            students.findById(studentId)
                    .map(Student::getClassName)
                    .ifPresent(className -> classExams.add(new ClassExam(className, examId)));

            if (generate(() -> generator.generateStudentFeedback(studentId, examId),
                    "student " + studentId)) {
                studentDocuments++;
            }
        }

        int classDocuments = 0;
        for (ClassExam classExam : classExams) {
            if (generate(() -> generator.generateTeacherFeedback(classExam.examId(), classExam.className()),
                    "class " + classExam.className())) {
                classDocuments++;
            }
        }

        log.info("AI insights generated for upload job {}: {} student document(s), {} class document(s)",
                uploadJobId, studentDocuments, classDocuments);
        return RepeatStatus.FINISHED;
    }

    /** @return true when the document was written */
    private boolean generate(Runnable generation, String subject) {
        try {
            generation.run();
            return true;
        } catch (ApiExceptions.NotFoundException e) {
            // Every row for this student or class was rejected, so there is
            // nothing to write feedback about. Not an error.
            log.debug("No feedback generated for {}: {}", subject, e.getMessage());
            return false;
        } catch (RuntimeException e) {
            log.warn("Could not generate feedback for {}: {}", subject, e.getMessage());
            return false;
        }
    }

    private record ClassExam(String className, Long examId) {
    }
}
