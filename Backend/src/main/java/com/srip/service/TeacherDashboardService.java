package com.srip.service;

import com.srip.analytics.ScoreCategory;
import com.srip.domain.Student;
import com.srip.domain.StudentAnalytics;
import com.srip.dto.analytics.AnalyticsDtos.CategoryCount;
import com.srip.dto.analytics.AnalyticsDtos.ClassAnalytics;
import com.srip.dto.analytics.AnalyticsDtos.StrongTopic;
import com.srip.dto.analytics.AnalyticsDtos.WeakSubject;
import com.srip.dto.analytics.AnalyticsDtos.WeakTopic;
import com.srip.dto.dashboard.DashboardDtos.CategoryBucket;
import com.srip.dto.dashboard.DashboardDtos.StudentCategoryCard;
import com.srip.dto.dashboard.DashboardDtos.TeacherDashboard;
import com.srip.exception.ApiExceptions;
import com.srip.repository.StudentAnalyticsRepository;
import com.srip.repository.StudentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Builds {@code GET /api/teacher/dashboard} and the four per-category lists.
 *
 * <p>The dashboard deliberately does not start from individual students. A
 * teacher with forty results does not want forty rows; they want to know how
 * many are in trouble and what to do on Monday. So the top level is the four
 * score categories, and a student only appears inside the bucket they fall in -
 * with their score, their weak topics and a suggested action already on the
 * card, because those are the three things the next decision needs.
 *
 * <p>Every read comes from the stored {@code analytics} rows, written by the
 * import job. Nothing here re-ranks a class or calls a model, so the page is
 * cheap to draw however many times it is refreshed.
 */
@Service
public class TeacherDashboardService {

    /** Weak topics named on one student's card. More is a list, not a plan. */
    private static final int MAX_CARD_TOPICS = 4;

    private final StudentAnalyticsRepository storedAnalytics;
    private final StudentRepository students;
    private final ClassInsightService classInsights;
    private final FeedbackService feedback;
    private final AnalyticsJson json;

    public TeacherDashboardService(StudentAnalyticsRepository storedAnalytics,
                                   StudentRepository students,
                                   ClassInsightService classInsights,
                                   FeedbackService feedback,
                                   AnalyticsJson json) {
        this.storedAnalytics = storedAnalytics;
        this.students = students;
        this.classInsights = classInsights;
        this.feedback = feedback;
        this.json = json;
    }

    /**
     * @param requestedClassName the class to show, or null for the only class
     *                           with data - or the first, alphabetically, if
     *                           there are several
     * @param requestedExamId    the exam to show, or null for the most recently
     *                           imported one for that class
     */
    @Transactional(readOnly = true)
    public TeacherDashboard dashboard(String requestedClassName, Long requestedExamId) {
        String className = resolveClassName(requestedClassName);
        Long examId = resolveExamId(className, requestedExamId);

        ClassAnalytics classAnalytics = classInsights.classAnalytics(examId, className);

        List<CategoryBucket> buckets = new ArrayList<>(ScoreCategory.values().length);
        for (ScoreCategory category : ScoreCategory.values()) {
            List<StudentCategoryCard> cards = cards(examId, className, category);
            buckets.add(new CategoryBucket(category, category.label(), cards.size(), cards));
        }

        return new TeacherDashboard(
                className,
                examId,
                classAnalytics.examName(),
                classAnalytics.classSize(),
                classAnalytics.classAveragePercentage(),
                classAnalytics.highestPercentage(),
                classAnalytics.lowestPercentage(),
                categoryCounts(examId, className),
                buckets,
                classAnalytics.weakestTopics(),
                // Read, never generate. Drawing a dashboard must not trigger a
                // billed model call; the import job writes this document.
                feedback.storedTeacherFeedback(examId, className).orElse(null),
                Instant.now());
    }

    /** One bucket on its own, for {@code /api/teacher/students/{category}}. */
    @Transactional(readOnly = true)
    public List<StudentCategoryCard> studentsInCategory(ScoreCategory category,
                                                        String requestedClassName,
                                                        Long requestedExamId) {
        String className = resolveClassName(requestedClassName);
        Long examId = resolveExamId(className, requestedExamId);
        return cards(examId, className, category);
    }

    // -- Internals -----------------------------------------------------------

    private List<StudentCategoryCard> cards(Long examId, String className, ScoreCategory category) {
        List<StudentAnalytics> rows = storedAnalytics
                .findByExamIdAndClassNameAndCategoryOrderByPercentageAsc(examId, className, category);
        if (rows.isEmpty()) {
            return List.of();
        }

        Map<Long, Student> studentsById = students
                .findAllById(rows.stream().map(StudentAnalytics::getStudentId).toList())
                .stream()
                .collect(Collectors.toMap(Student::getId, Function.identity()));

        return rows.stream()
                .map(row -> toCard(row, studentsById.get(row.getStudentId())))
                .toList();
    }

    private StudentCategoryCard toCard(StudentAnalytics row, Student student) {
        List<WeakTopic> weakTopics = json.weakTopics(row.getWeakTopicsJson());
        List<WeakSubject> weakSubjects = json.weakSubjects(row.getWeakSubjectsJson());
        List<StrongTopic> strongTopics = json.strongTopics(row.getStrongTopicsJson());

        return new StudentCategoryCard(
                row.getStudentId(),
                student == null ? null : student.getAdmissionNo(),
                student == null ? "Unknown" : student.getFullName(),
                row.getClassName(),
                row.getSection(),
                row.getPercentage(),
                row.getGrade(),
                row.getCategory(),
                row.getRankInClass(),
                weakTopics.stream().limit(MAX_CARD_TOPICS).map(WeakTopic::topicName).toList(),
                weakSubjects.stream().map(WeakSubject::subjectName).toList(),
                strongTopics.stream().limit(MAX_CARD_TOPICS).map(StrongTopic::topicName).toList(),
                suggestedAction(row.getCategory(), weakSubjects, weakTopics));
    }

    /**
     * A rule-based next step per category.
     *
     * <p>Rule-based on purpose. This line appears on every card, so it must be
     * present even when no model has run, and it must never contradict the
     * numbers next to it. The AI's richer advice sits alongside the buckets in
     * {@code aiGuidance}, where a reader can see it for what it is.
     */
    private String suggestedAction(ScoreCategory category,
                                   List<WeakSubject> weakSubjects,
                                   List<WeakTopic> weakTopics) {
        String weakestSubject = weakSubjects.isEmpty() ? null : weakSubjects.get(0).subjectName();
        String weakestTopic = weakTopics.isEmpty() ? null : weakTopics.get(0).topicName();

        return switch (category) {
            case CRITICAL -> weakestSubject != null
                    ? "Conduct additional %s practice sessions and re-test within two weeks."
                            .formatted(weakestSubject)
                    : "Review the whole paper with the student and identify where marks were lost.";
            case AVERAGE -> weakestTopic != null
                    ? "Provide worksheet practice on %s.".formatted(weakestTopic)
                    : "Provide worksheet practice on the weakest chapters.";
            case GOOD -> weakestTopic != null
                    ? "Close the remaining gap on %s with a short revision session.".formatted(weakestTopic)
                    : "Set stretch questions to move this student into the top band.";
            case EXCELLENT -> "Set advanced practice and consider pairing this student with one who is struggling.";
        };
    }

    private List<CategoryCount> categoryCounts(Long examId, String className) {
        Map<ScoreCategory, Long> counts = new EnumMap<>(ScoreCategory.class);
        for (ScoreCategory category : ScoreCategory.values()) {
            counts.put(category, 0L);
        }
        storedAnalytics.tallyCategories(examId, className).forEach(tally ->
                counts.put(tally.getCategory(),
                        tally.getStudents() == null ? 0L : tally.getStudents()));

        return counts.entrySet().stream()
                .map(entry -> new CategoryCount(entry.getKey(), entry.getKey().label(), entry.getValue()))
                .toList();
    }

    private String resolveClassName(String requestedClassName) {
        if (requestedClassName != null && !requestedClassName.isBlank()) {
            return requestedClassName;
        }
        return storedAnalytics.findDistinctClassNames().stream()
                .findFirst()
                .orElseThrow(() -> new ApiExceptions.NotFoundException(
                        "No results have been uploaded yet, so there is no class to show"));
    }

    private Long resolveExamId(String className, Long requestedExamId) {
        if (requestedExamId != null) {
            return requestedExamId;
        }
        return storedAnalytics.findExamIdsForClassByRecency(className).stream()
                .findFirst()
                .orElseThrow(() -> new ApiExceptions.NotFoundException(
                        "No results have been uploaded for class " + className + " yet"));
    }
}
