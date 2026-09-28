package com.srip.dto.dashboard;

import com.srip.analytics.ScoreCategory;
import com.srip.dto.ai.FeedbackDtos.FeedbackEnvelope;
import com.srip.dto.analytics.AnalyticsDtos.CategoryCount;
import com.srip.dto.analytics.AnalyticsDtos.ClassTopicWeakness;
import com.srip.dto.analytics.AnalyticsDtos.SubjectPerformance;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** The two screens the platform exists to draw. */
public final class DashboardDtos {

    private DashboardDtos() {
    }

    // -- Student -------------------------------------------------------------

    /**
     * One strength or weakness as the student sees it.
     *
     * @param marker the glyph the UI prefixes the line with; supplied by the API
     *               so a checkmark never gets attached to a weakness by a client
     *               that guessed
     */
    public record TopicHighlight(
            String subject,
            String chapter,
            String topic,
            BigDecimal percentage,
            String marker
    ) {
        public static final String STRONG_MARKER = "✓";
        public static final String WEAK_MARKER = "⚠";
    }

    /** A book or video suggested for one weak topic. */
    public record ResourceSuggestion(
            String subject,
            String topic,
            String resourceType,
            String title,
            String url
    ) {
    }

    /** Everything on {@code GET /api/student/dashboard}. */
    public record StudentDashboard(
            Long studentId,
            String admissionNo,
            String studentName,
            String className,
            String section,
            Long examId,
            String examName,
            BigDecimal percentage,
            String grade,
            ScoreCategory category,
            String categoryLabel,
            boolean passed,
            int rankInClass,
            int classSize,
            BigDecimal classAveragePercentage,
            List<SubjectPerformance> subjects,
            List<TopicHighlight> strongTopics,
            List<TopicHighlight> weakTopics,
            List<ResourceSuggestion> recommendedResources
    ) {
    }

    // -- Teacher -------------------------------------------------------------

    /**
     * One student as the teacher dashboard lists them.
     *
     * <p>Deliberately not a link to a full profile. The teacher's first question
     * is "who needs what, and what do I do on Monday?", so the score, the weak
     * topics and a suggested action are on the card itself.
     */
    public record StudentCategoryCard(
            Long studentId,
            String admissionNo,
            String studentName,
            String className,
            String section,
            BigDecimal percentage,
            String grade,
            ScoreCategory category,
            int rankInClass,
            List<String> weakTopics,
            List<String> weakSubjects,
            List<String> strongTopics,
            String suggestedAction
    ) {
    }

    /** One of the four buckets on {@code GET /api/teacher/dashboard}. */
    public record CategoryBucket(
            ScoreCategory category,
            String label,
            long studentCount,
            List<StudentCategoryCard> students
    ) {
    }

    /**
     * Everything on {@code GET /api/teacher/dashboard}: the class totals, the
     * four buckets, the topics to re-teach, and the AI's advice on what to do.
     */
    public record TeacherDashboard(
            String className,
            Long examId,
            String examName,
            int totalStudents,
            BigDecimal classAveragePercentage,
            BigDecimal highestPercentage,
            BigDecimal lowestPercentage,
            List<CategoryCount> categoryCounts,
            List<CategoryBucket> buckets,
            List<ClassTopicWeakness> weakestTopics,
            FeedbackEnvelope aiGuidance,
            Instant generatedAt
    ) {
    }
}
