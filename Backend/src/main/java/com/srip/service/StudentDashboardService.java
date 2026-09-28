package com.srip.service;

import com.srip.domain.Student;
import com.srip.dto.ai.FeedbackDtos.FeedbackEnvelope;
import com.srip.dto.analytics.AnalyticsDtos.StudentSnapshot;
import com.srip.dto.analytics.AnalyticsDtos.TopicPerformance;
import com.srip.dto.dashboard.DashboardDtos.ResourceSuggestion;
import com.srip.dto.dashboard.DashboardDtos.StudentDashboard;
import com.srip.dto.dashboard.DashboardDtos.TopicHighlight;
import com.srip.exception.ApiExceptions;
import com.srip.repository.ExamResultRepository;
import com.srip.repository.StudentAnalyticsRepository;
import com.srip.repository.StudentRepository;
import com.srip.repository.TopicScoreRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Builds {@code GET /api/student/dashboard} and its two companions.
 *
 * <p>The exam is optional in the API. A student asking "how did I do?" means the
 * most recent exam, and making them look up an exam id first would be the wrong
 * way round; when none is given, the latest computed standing is used.
 *
 * <p>Strong and weak topics carry their marker glyph from here rather than from
 * the client. Deciding in one place which list gets a tick and which gets a
 * warning means a client cannot put the wrong one against a failing topic.
 */
@Service
public class StudentDashboardService {

    private final StudentRepository students;
    private final StudentAnalyticsRepository storedAnalytics;
    private final ExamResultRepository examResults;
    private final TopicScoreRepository topicScores;
    private final AnalyticsService analytics;
    private final StudyResourceService studyResources;
    private final FeedbackService feedback;

    public StudentDashboardService(StudentRepository students,
                                   StudentAnalyticsRepository storedAnalytics,
                                   ExamResultRepository examResults,
                                   TopicScoreRepository topicScores,
                                   AnalyticsService analytics,
                                   StudyResourceService studyResources,
                                   FeedbackService feedback) {
        this.students = students;
        this.storedAnalytics = storedAnalytics;
        this.examResults = examResults;
        this.topicScores = topicScores;
        this.analytics = analytics;
        this.studyResources = studyResources;
        this.feedback = feedback;
    }

    @Transactional(readOnly = true)
    public StudentDashboard dashboard(Long studentId, Long requestedExamId) {
        Student student = requireStudent(studentId);
        Long examId = resolveExamId(studentId, requestedExamId);
        StudentSnapshot snapshot = analytics.snapshot(studentId, examId);

        return new StudentDashboard(
                student.getId(),
                student.getAdmissionNo(),
                student.getFullName(),
                student.getClassName(),
                student.getSection(),
                examId,
                snapshot.examName(),
                snapshot.overallPercentage(),
                snapshot.overallGrade(),
                snapshot.category(),
                snapshot.category().label(),
                snapshot.passed(),
                snapshot.rankInClass(),
                snapshot.classSize(),
                snapshot.classAveragePercentage(),
                snapshot.subjects(),
                strongHighlights(studentId, examId, snapshot),
                weakHighlights(studentId, examId, snapshot),
                studyResources.forWeakTopics(snapshot.weakTopics()));
    }

    /**
     * The stored feedback document, generating one only if the import job did
     * not - which happens when {@code app.claude.generate-on-import} is off.
     */
    @Transactional
    public FeedbackEnvelope studentFeedback(Long studentId, Long requestedExamId, boolean refresh) {
        requireStudent(studentId);
        Long examId = resolveExamId(studentId, requestedExamId);
        return feedback.studentFeedback(studentId, examId, refresh);
    }

    @Transactional(readOnly = true)
    public List<ResourceSuggestion> resources(Long studentId, Long requestedExamId) {
        requireStudent(studentId);
        Long examId = resolveExamId(studentId, requestedExamId);
        return studyResources.forWeakTopics(analytics.snapshot(studentId, examId).weakTopics());
    }

    // -- Internals -----------------------------------------------------------

    /**
     * @return the requested exam, or the student's most recently computed one
     * @throws ApiExceptions.NotFoundException when the student has no results at all
     */
    private Long resolveExamId(Long studentId, Long requestedExamId) {
        if (requestedExamId != null) {
            return requestedExamId;
        }

        return storedAnalytics.findLatestForStudent(studentId).stream()
                .findFirst()
                .map(row -> row.getExamId())
                // Analytics are written by the import job, so a student can have
                // marks but no stored standing if the job was interrupted. Fall
                // back to their latest result rather than claiming no data.
                .or(() -> examResults.findAllForStudent(studentId).stream()
                        .reduce((first, second) -> second)
                        .map(result -> result.getExam().getId()))
                .orElseThrow(() -> new ApiExceptions.NotFoundException(
                        "No results have been uploaded for this student yet"));
    }

    /**
     * Topic highlights are rebuilt from the exam's topic rows rather than read
     * from the snapshot's aggregates, so each line can name its chapter - which
     * is what makes "revise chapter 4" possible instead of just naming the topic.
     */
    private List<TopicHighlight> strongHighlights(Long studentId, Long examId, StudentSnapshot snapshot) {
        Map<String, TopicPerformance> byTopic = topicPerformanceByName(studentId, examId);
        List<TopicHighlight> highlights = new ArrayList<>();
        snapshot.strongTopics().forEach(strong ->
                highlights.add(toHighlight(byTopic.get(key(strong.topicName())), strong.topicName(),
                        strong.averagePercentage(), TopicHighlight.STRONG_MARKER)));
        return highlights;
    }

    private List<TopicHighlight> weakHighlights(Long studentId, Long examId, StudentSnapshot snapshot) {
        Map<String, TopicPerformance> byTopic = topicPerformanceByName(studentId, examId);
        List<TopicHighlight> highlights = new ArrayList<>();
        snapshot.weakTopics().forEach(weak ->
                highlights.add(toHighlight(byTopic.get(key(weak.topicName())), weak.topicName(),
                        weak.averagePercentage(), TopicHighlight.WEAK_MARKER)));
        return highlights;
    }

    private Map<String, TopicPerformance> topicPerformanceByName(Long studentId, Long examId) {
        Map<String, TopicPerformance> byTopic = new HashMap<>();
        topicScores.findForStudentAndExam(studentId, examId).forEach(score -> byTopic.put(
                key(score.getTopic().getName()),
                new TopicPerformance(
                        score.getTopic().getSubject().getCode(),
                        score.getTopic().getSubject().getName(),
                        score.getTopic().getChapterName(),
                        score.getTopic().getName(),
                        score.getMarksObtained(),
                        score.getMaxMarks(),
                        score.getPercentage())));
        return byTopic;
    }

    private static TopicHighlight toHighlight(TopicPerformance performance,
                                              String topicName,
                                              BigDecimal percentage,
                                              String marker) {
        return new TopicHighlight(
                performance == null ? null : performance.subjectName(),
                performance == null ? null : performance.chapterName(),
                topicName,
                percentage,
                marker);
    }

    private static String key(String topicName) {
        return topicName == null ? "" : topicName.toLowerCase(Locale.ROOT);
    }

    private Student requireStudent(Long studentId) {
        return students.findById(studentId)
                .orElseThrow(() -> ApiExceptions.NotFoundException.of("Student", studentId));
    }
}
