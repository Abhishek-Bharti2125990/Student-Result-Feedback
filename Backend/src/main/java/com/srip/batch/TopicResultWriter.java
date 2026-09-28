package com.srip.batch;

import com.srip.analytics.GradingService;
import com.srip.domain.ExamResult;
import com.srip.domain.TopicScore;
import com.srip.repository.ExamRepository;
import com.srip.repository.ExamResultRepository;
import com.srip.repository.StudentRepository;
import com.srip.repository.SubjectRepository;
import com.srip.repository.TopicRepository;
import com.srip.repository.TopicScoreRepository;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Persists a chunk of topic rows and keeps each subject total in step with them.
 *
 * <p>The file is one topic per line, so a subject paper is spread over several
 * lines and - on a large file - over several chunks. Each chunk therefore has to
 * do three things per subject: write its topic rows, re-total the subject from
 * <em>all</em> of its topic rows, and store the new percentage and grade.
 *
 * <p>The re-total is a SQL {@code sum} rather than a walk over the entity's
 * collection, because the rows contributing to it may have been written by an
 * earlier chunk in an earlier transaction. The {@code saveAndFlush} before it is
 * what makes this chunk's own rows visible to that query.
 *
 * <p>Rows are grouped by subject paper first so a chunk containing five topics
 * of one paper re-totals once instead of five times.
 *
 * <p>Step-scoped so it can read the upload job id from the job parameters and
 * stamp it onto every row. That stamp is what lets an operator answer "which
 * upload produced this mark?" months later, and it is how the analytics step
 * learns which students the file touched.
 */
@Component
@StepScope
public class TopicResultWriter implements ItemWriter<TopicResultRow> {

    private final ExamResultRepository examResults;
    private final TopicScoreRepository topicScores;
    private final StudentRepository students;
    private final ExamRepository exams;
    private final SubjectRepository subjects;
    private final TopicRepository topics;
    private final GradingService grading;
    private final Long uploadJobId;

    public TopicResultWriter(ExamResultRepository examResults,
                             TopicScoreRepository topicScores,
                             StudentRepository students,
                             ExamRepository exams,
                             SubjectRepository subjects,
                             TopicRepository topics,
                             GradingService grading,
                             @Value("#{jobParameters['uploadJobId']}") Long uploadJobId) {
        this.examResults = examResults;
        this.topicScores = topicScores;
        this.students = students;
        this.exams = exams;
        this.subjects = subjects;
        this.topics = topics;
        this.grading = grading;
        this.uploadJobId = uploadJobId;
    }

    @Override
    public void write(Chunk<? extends TopicResultRow> chunk) {
        Map<TopicResultRow.PaperId, List<TopicResultRow>> byPaper = groupByPaper(chunk);

        for (Map.Entry<TopicResultRow.PaperId, List<TopicResultRow>> entry : byPaper.entrySet()) {
            ExamResult result = findOrCreatePaper(entry.getKey());
            for (TopicResultRow row : entry.getValue()) {
                upsertTopicScore(result, row);
            }
            retotal(result);
        }
    }

    private Map<TopicResultRow.PaperId, List<TopicResultRow>> groupByPaper(
            Chunk<? extends TopicResultRow> chunk) {
        Map<TopicResultRow.PaperId, List<TopicResultRow>> byPaper = new LinkedHashMap<>();
        for (TopicResultRow row : chunk) {
            byPaper.computeIfAbsent(row.paperId(), ignored -> new ArrayList<>()).add(row);
        }
        return byPaper;
    }

    private ExamResult findOrCreatePaper(TopicResultRow.PaperId paper) {
        ExamResult result = examResults
                .findByStudentIdAndExamIdAndSubjectId(paper.studentId(), paper.examId(), paper.subjectId())
                .orElseGet(() -> new ExamResult(
                        students.getReferenceById(paper.studentId()),
                        exams.getReferenceById(paper.examId()),
                        subjects.getReferenceById(paper.subjectId())));

        // A brand-new paper has no marks yet; they are set by retotal() once its
        // topic rows exist. Zeroes keep the NOT NULL columns satisfiable in
        // between, and are never read, because retotal() runs before the commit.
        if (result.getId() == null) {
            result.setMarksObtained(BigDecimal.ZERO);
            result.setMaxMarks(BigDecimal.ONE);
            result.setPercentage(BigDecimal.ZERO);
            result.setGrade(grading.grade(BigDecimal.ZERO));
        }
        result.setUploadJobId(uploadJobId);
        return examResults.saveAndFlush(result);
    }

    /**
     * Writes one topic row, updating it if this student already has a mark for
     * this topic. Re-uploading a corrected file must correct the mark, not add a
     * second one alongside it.
     */
    private void upsertTopicScore(ExamResult result, TopicResultRow row) {
        BigDecimal percentage = grading.percentage(row.marksObtained(), row.maxMarks());

        TopicScore score = topicScores
                .findByExamResultIdAndTopicId(result.getId(), row.topicId())
                .orElse(null);

        if (score == null) {
            score = new TopicScore(
                    result,
                    topics.getReferenceById(row.topicId()),
                    row.marksObtained(),
                    row.maxMarks(),
                    percentage);
        } else {
            score.setMarksObtained(row.marksObtained());
            score.setMaxMarks(row.maxMarks());
            score.setPercentage(percentage);
        }
        topicScores.saveAndFlush(score);
    }

    /** Recomputes the subject total from every topic row now stored against it. */
    private void retotal(ExamResult result) {
        TopicScoreRepository.Totals totals = topicScores.sumForExamResult(result.getId());

        BigDecimal marks = totals == null || totals.getTotalMarks() == null
                ? BigDecimal.ZERO
                : totals.getTotalMarks();
        BigDecimal max = totals == null || totals.getTotalMax() == null
                ? BigDecimal.ZERO
                : totals.getTotalMax();

        BigDecimal percentage = grading.percentage(marks, max);
        result.setMarksObtained(grading.scale(marks));
        // The column is CHECK (max_marks > 0), and a paper always has at least
        // the row that created it, so this floor is defence rather than a case.
        result.setMaxMarks(max.signum() > 0 ? grading.scale(max) : BigDecimal.ONE);
        result.setPercentage(percentage);
        result.setGrade(grading.grade(percentage));
        examResults.save(result);
    }
}
