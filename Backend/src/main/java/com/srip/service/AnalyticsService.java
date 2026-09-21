package com.srip.service;

import com.srip.analytics.GradingService;
import com.srip.analytics.RankingService;
import com.srip.analytics.TrendAnalyzer;
import com.srip.analytics.WeaknessDetector;
import com.srip.domain.Exam;
import com.srip.domain.ExamResult;
import com.srip.domain.Student;
import com.srip.domain.TopicScore;
import com.srip.dto.analytics.AnalyticsDtos.ExamReport;
import com.srip.dto.analytics.AnalyticsDtos.PerformanceTrend;
import com.srip.dto.analytics.AnalyticsDtos.RankingEntry;
import com.srip.dto.analytics.AnalyticsDtos.StudentSnapshot;
import com.srip.dto.analytics.AnalyticsDtos.SubjectPerformance;
import com.srip.dto.analytics.AnalyticsDtos.TopicPerformance;
import com.srip.dto.analytics.AnalyticsDtos.WeakSubject;
import com.srip.dto.analytics.AnalyticsDtos.WeakTopic;
import com.srip.dto.result.ResultDtos.ExamResultView;
import com.srip.dto.result.ResultDtos.TopicScoreView;
import com.srip.exception.ApiExceptions;
import com.srip.repository.ExamRepository;
import com.srip.repository.ExamResultRepository;
import com.srip.repository.StudentRepository;
import com.srip.repository.TopicScoreRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Per-student analytics: result cards, weakness detection and trends.
 *
 * <p>Class-wide aggregates are delegated to {@link ClassInsightService} so its
 * caching actually takes effect across the bean boundary.
 */
@Service
public class AnalyticsService {

    private final StudentRepository students;
    private final ExamRepository exams;
    private final ExamResultRepository examResults;
    private final TopicScoreRepository topicScores;
    private final GradingService grading;
    private final RankingService ranking;
    private final WeaknessDetector weaknessDetector;
    private final TrendAnalyzer trendAnalyzer;
    private final ClassInsightService classInsights;

    public AnalyticsService(StudentRepository students,
                            ExamRepository exams,
                            ExamResultRepository examResults,
                            TopicScoreRepository topicScores,
                            GradingService grading,
                            RankingService ranking,
                            WeaknessDetector weaknessDetector,
                            TrendAnalyzer trendAnalyzer,
                            ClassInsightService classInsights) {
        this.students = students;
        this.exams = exams;
        this.examResults = examResults;
        this.topicScores = topicScores;
        this.grading = grading;
        this.ranking = ranking;
        this.weaknessDetector = weaknessDetector;
        this.trendAnalyzer = trendAnalyzer;
        this.classInsights = classInsights;
    }

    // -- Single student, single exam -----------------------------------------

    @Transactional(readOnly = true)
    public ExamReport examReport(Long studentId, Long examId) {
        Student student = requireStudent(studentId);
        Exam exam = requireExam(examId);

        List<ExamResult> results = examResults.findForStudentAndExam(studentId, examId);
        if (results.isEmpty()) {
            throw new ApiExceptions.NotFoundException(
                    "No results recorded for student " + student.getAdmissionNo() + " in exam " + exam.getCode());
        }

        Map<Long, BigDecimal> classAverages = classInsights.subjectAverages(examId, student.getClassName());
        List<SubjectPerformance> subjects = results.stream()
                .map(result -> toSubjectPerformance(result, classAverages))
                .toList();

        BigDecimal totalMarks = sum(results, ExamResult::getMarksObtained);
        BigDecimal totalMax = sum(results, ExamResult::getMaxMarks);
        BigDecimal overallPercentage = grading.percentage(totalMarks, totalMax);

        List<RankingEntry> rankings = classInsights.rankings(examId, student.getClassName());
        List<TopicPerformance> topics = topicScores.findForStudentAndExam(studentId, examId).stream()
                .map(this::toTopicPerformance)
                .toList();

        return new ExamReport(
                student.getId(),
                student.getAdmissionNo(),
                student.getFullName(),
                student.getClassName(),
                student.getSection(),
                exam.getId(),
                exam.getCode(),
                exam.getName(),
                exam.getExamDate(),
                grading.scale(totalMarks),
                grading.scale(totalMax),
                overallPercentage,
                grading.grade(overallPercentage),
                grading.isPass(overallPercentage),
                ranking.rankOf(rankings, studentId),
                rankings.size(),
                subjects,
                topics);
    }

    @Transactional(readOnly = true)
    public List<ExamResultView> resultsOf(Long studentId) {
        requireStudent(studentId);

        // One query for every topic score, grouped in memory, instead of a
        // lazy-loaded collection per result row.
        Map<Long, List<TopicScore>> scoresByResult = topicScores.findAllForStudent(studentId).stream()
                .collect(Collectors.groupingBy(score -> score.getExamResult().getId()));

        return examResults.findAllForStudent(studentId).stream()
                .map(result -> new ExamResultView(
                        result.getId(),
                        result.getExam().getCode(),
                        result.getExam().getName(),
                        result.getSubject().getCode(),
                        result.getSubject().getName(),
                        result.getMarksObtained(),
                        result.getMaxMarks(),
                        result.getPercentage(),
                        result.getGrade(),
                        result.isAttempted(),
                        result.getRemarks(),
                        scoresByResult.getOrDefault(result.getId(), List.of()).stream()
                                .map(score -> new TopicScoreView(
                                        score.getTopic().getName(),
                                        score.getMarksObtained(),
                                        score.getMaxMarks(),
                                        score.getPercentage()))
                                .toList()))
                .toList();
    }

    // -- Whole history -------------------------------------------------------

    @Transactional(readOnly = true)
    public PerformanceTrend trendOf(Long studentId) {
        requireStudent(studentId);
        return trendAnalyzer.analyse(examResults.findAllForStudent(studentId));
    }

    @Transactional(readOnly = true)
    public List<WeakSubject> weakSubjectsOf(Long studentId) {
        requireStudent(studentId);
        return weaknessDetector.detectWeakSubjects(examResults.findAllForStudent(studentId));
    }

    @Transactional(readOnly = true)
    public List<WeakTopic> weakTopicsOf(Long studentId) {
        requireStudent(studentId);
        return weaknessDetector.detectWeakTopics(topicScores.findAllForStudent(studentId));
    }

    // -- AI input ------------------------------------------------------------

    /**
     * Assembles the one object the AI layer is allowed to see.
     *
     * <p>Everything in it is computed, not generated, which is what makes the
     * feedback auditable: any claim in a generated document can be checked
     * against the snapshot the model was given.
     */
    @Transactional(readOnly = true)
    public StudentSnapshot snapshot(Long studentId, Long examId) {
        ExamReport report = examReport(studentId, examId);

        List<WeakSubject> weakSubjects = weaknessDetector.detectWeakSubjects(
                examResults.findForStudentAndExam(studentId, examId));
        List<WeakTopic> weakTopics = weaknessDetector.detectWeakTopics(
                topicScores.findAllForStudent(studentId));
        PerformanceTrend trend = trendAnalyzer.analyse(examResults.findAllForStudent(studentId));

        BigDecimal classAverage = ClassInsightService.average(
                classInsights.rankings(examId, report.className()).stream()
                        .map(RankingEntry::percentage)
                        .toList());

        return new StudentSnapshot(
                report.studentId(),
                report.admissionNo(),
                report.studentName(),
                report.className(),
                report.section(),
                report.examCode(),
                report.examName(),
                report.overallPercentage(),
                report.overallGrade(),
                report.passed(),
                report.rankInClass(),
                report.classSize(),
                classAverage,
                report.subjects(),
                weakSubjects,
                weakTopics,
                trend);
    }

    // -- Internals -----------------------------------------------------------

    private SubjectPerformance toSubjectPerformance(ExamResult result, Map<Long, BigDecimal> classAverages) {
        BigDecimal classAverage = classAverages.get(result.getSubject().getId());
        BigDecimal delta = classAverage == null
                ? null
                : result.getPercentage().subtract(classAverage).setScale(2, RoundingMode.HALF_UP);
        return new SubjectPerformance(
                result.getSubject().getId(),
                result.getSubject().getCode(),
                result.getSubject().getName(),
                result.getMarksObtained(),
                result.getMaxMarks(),
                result.getPercentage(),
                result.getGrade(),
                classAverage,
                delta);
    }

    private TopicPerformance toTopicPerformance(TopicScore score) {
        return new TopicPerformance(
                score.getTopic().getSubject().getCode(),
                score.getTopic().getName(),
                score.getMarksObtained(),
                score.getMaxMarks(),
                score.getPercentage());
    }

    private Student requireStudent(Long studentId) {
        return students.findById(studentId)
                .orElseThrow(() -> ApiExceptions.NotFoundException.of("Student", studentId));
    }

    private Exam requireExam(Long examId) {
        return exams.findById(examId)
                .orElseThrow(() -> ApiExceptions.NotFoundException.of("Exam", examId));
    }

    private static BigDecimal sum(List<ExamResult> results, Function<ExamResult, BigDecimal> extractor) {
        return results.stream()
                .map(extractor)
                .filter(value -> value != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
