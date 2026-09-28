package com.srip.service;

import com.srip.analytics.GradingService;
import com.srip.analytics.RankingService;
import com.srip.analytics.WeaknessDetector;
import com.srip.domain.ExamResult;
import com.srip.domain.Student;
import com.srip.domain.StudentAnalytics;
import com.srip.domain.TopicScore;
import com.srip.dto.analytics.AnalyticsDtos.RankingEntry;
import com.srip.repository.ExamResultRepository;
import com.srip.repository.StudentAnalyticsRepository;
import com.srip.repository.StudentRepository;
import com.srip.repository.TopicScoreRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Computes and stores the {@code analytics} row for one student in one exam.
 *
 * <p>Called by the analytics step of the import job, once per (student, exam)
 * pair the uploaded file touched. Everything it writes is derivable from
 * {@code exam_results} and {@code topic_scores}; storing it anyway is what lets
 * the teacher dashboard bucket a class by category with one indexed query
 * instead of re-ranking the class on every page load.
 *
 * <p>Writes are upserts keyed on (student, exam). Re-uploading a corrected file
 * must revise the standing, not append a second version of it.
 */
@Service
public class AnalyticsWriteService {

    private static final Logger log = LoggerFactory.getLogger(AnalyticsWriteService.class);

    private final StudentRepository students;
    private final ExamResultRepository examResults;
    private final TopicScoreRepository topicScores;
    private final StudentAnalyticsRepository stored;
    private final ClassInsightService classInsights;
    private final GradingService grading;
    private final RankingService ranking;
    private final WeaknessDetector weaknessDetector;
    private final AnalyticsJson json;

    public AnalyticsWriteService(StudentRepository students,
                                 ExamResultRepository examResults,
                                 TopicScoreRepository topicScores,
                                 StudentAnalyticsRepository stored,
                                 ClassInsightService classInsights,
                                 GradingService grading,
                                 RankingService ranking,
                                 WeaknessDetector weaknessDetector,
                                 AnalyticsJson json) {
        this.students = students;
        this.examResults = examResults;
        this.topicScores = topicScores;
        this.stored = stored;
        this.classInsights = classInsights;
        this.grading = grading;
        this.ranking = ranking;
        this.weaknessDetector = weaknessDetector;
        this.json = json;
    }

    /**
     * @param uploadJobId stamped on the row so a disputed figure can be traced
     *                    back to the file that produced it
     * @return the stored row, or null when the student has no results for this
     *         exam (which happens if every one of their rows was rejected)
     */
    @Transactional
    public StudentAnalytics computeAndStore(Long studentId, Long examId, Long uploadJobId) {
        Student student = students.findById(studentId).orElse(null);
        if (student == null) {
            return null;
        }

        List<ExamResult> results = examResults.findForStudentAndExam(studentId, examId);
        if (results.isEmpty()) {
            return null;
        }

        BigDecimal totalMarks = sumMarks(results);
        BigDecimal totalMax = sumMax(results);
        BigDecimal percentage = grading.percentage(totalMarks, totalMax);

        List<RankingEntry> rankings = classInsights.rankings(examId, student.getClassName());
        List<TopicScore> examTopics = topicScores.findForStudentAndExam(studentId, examId);

        StudentAnalytics row = stored.findByStudentIdAndExamId(studentId, examId)
                .orElseGet(() -> new StudentAnalytics(
                        studentId, examId, student.getClassName(), student.getSection()));

        row.setClassName(student.getClassName());
        row.setSection(student.getSection());
        row.setTotalMarks(grading.scale(totalMarks));
        row.setMaxMarks(grading.scale(totalMax));
        row.setPercentage(percentage);
        row.setGrade(grading.grade(percentage));
        row.setCategory(grading.category(percentage));
        row.setRankInClass(ranking.rankOf(rankings, studentId));
        row.setClassSize(rankings.size());

        // Strengths and weaknesses are computed from this exam only. A student's
        // standing after an exam is about that exam; the whole-history view is
        // what the trend endpoints are for.
        row.setStrongSubjectsJson(json.encode(weaknessDetector.detectStrongSubjects(results)));
        row.setWeakSubjectsJson(json.encode(weaknessDetector.detectWeakSubjects(results)));
        row.setStrongTopicsJson(json.encode(weaknessDetector.detectStrongTopics(examTopics)));
        row.setWeakTopicsJson(json.encode(weaknessDetector.detectWeakTopics(examTopics)));

        row.setUploadJobId(uploadJobId);
        row.setComputedAt(Instant.now());

        StudentAnalytics saved = stored.save(row);
        log.debug("Analytics for student {} exam {}: {}% grade {} category {} rank {}/{}",
                studentId, examId, saved.getPercentage(), saved.getGrade(),
                saved.getCategory(), saved.getRankInClass(), saved.getClassSize());
        return saved;
    }

    private static BigDecimal sumMarks(List<ExamResult> results) {
        return results.stream()
                .map(ExamResult::getMarksObtained)
                .filter(value -> value != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static BigDecimal sumMax(List<ExamResult> results) {
        return results.stream()
                .map(ExamResult::getMaxMarks)
                .filter(value -> value != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
