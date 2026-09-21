package com.srip.dto.analytics;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Everything the analytics engine produces. */
public final class AnalyticsDtos {

    private AnalyticsDtos() {
    }

    /**
     * One subject inside one exam.
     *
     * @param classAverage mean percentage for this subject across the class
     * @param deltaVsClass this student's percentage minus {@code classAverage};
     *                     negative means below peers
     */
    public record SubjectPerformance(
            Long subjectId,
            String subjectCode,
            String subjectName,
            BigDecimal marksObtained,
            BigDecimal maxMarks,
            BigDecimal percentage,
            String grade,
            BigDecimal classAverage,
            BigDecimal deltaVsClass
    ) {
    }

    public record TopicPerformance(
            String subjectCode,
            String topicName,
            BigDecimal marksObtained,
            BigDecimal maxMarks,
            BigDecimal percentage
    ) {
    }

    /** A student's full result card for a single exam. */
    public record ExamReport(
            Long studentId,
            String admissionNo,
            String studentName,
            String className,
            String section,
            Long examId,
            String examCode,
            String examName,
            LocalDate examDate,
            BigDecimal totalMarks,
            BigDecimal totalMaxMarks,
            BigDecimal overallPercentage,
            String overallGrade,
            boolean passed,
            int rankInClass,
            int classSize,
            List<SubjectPerformance> subjects,
            List<TopicPerformance> topics
    ) {
    }

    public record RankingEntry(
            int rank,
            Long studentId,
            String admissionNo,
            String studentName,
            BigDecimal totalMarks,
            BigDecimal totalMaxMarks,
            BigDecimal percentage,
            String grade
    ) {
    }

    /**
     * @param reason why the subject was flagged: below the absolute threshold,
     *               below the student's own average, or both
     */
    public record WeakSubject(
            String subjectCode,
            String subjectName,
            BigDecimal averagePercentage,
            String reason
    ) {
    }

    /**
     * @param occurrences how many exams this topic has been weak in; a repeat
     *                    offender is a different problem from a single bad day
     */
    public record WeakTopic(
            String subjectCode,
            String topicName,
            BigDecimal averagePercentage,
            int occurrences
    ) {
    }

    public record TrendPoint(
            String examCode,
            String examName,
            LocalDate examDate,
            BigDecimal percentage
    ) {
    }

    /** @param direction one of IMPROVING, DECLINING, STABLE, INSUFFICIENT_DATA */
    public record SubjectTrend(
            String subjectCode,
            String subjectName,
            List<TrendPoint> points,
            BigDecimal change,
            String direction
    ) {
    }

    public record PerformanceTrend(
            List<TrendPoint> overall,
            List<SubjectTrend> bySubject,
            BigDecimal overallChange,
            String overallDirection
    ) {
    }

    /**
     * The single object handed to the AI layer. Assembling it once, from the
     * deterministic analytics, keeps the model's input auditable: any feedback
     * can be traced back to the numbers it was given.
     */
    public record StudentSnapshot(
            Long studentId,
            String admissionNo,
            String studentName,
            String className,
            String section,
            String examCode,
            String examName,
            BigDecimal overallPercentage,
            String overallGrade,
            boolean passed,
            int rankInClass,
            int classSize,
            BigDecimal classAveragePercentage,
            List<SubjectPerformance> subjects,
            List<WeakSubject> weakSubjects,
            List<WeakTopic> weakTopics,
            PerformanceTrend trend
    ) {
    }

    public record SubjectStat(
            String subjectCode,
            String subjectName,
            BigDecimal average,
            BigDecimal highest,
            BigDecimal lowest,
            long passCount,
            long failCount
    ) {
    }

    public record StrugglingStudent(
            Long studentId,
            String admissionNo,
            String studentName,
            BigDecimal overallPercentage,
            int rankInClass,
            List<String> weakSubjects
    ) {
    }

    /** Teacher-facing view of one exam for one class. */
    public record ClassAnalytics(
            Long examId,
            String examCode,
            String examName,
            String className,
            int classSize,
            BigDecimal classAveragePercentage,
            BigDecimal highestPercentage,
            BigDecimal lowestPercentage,
            List<RankingEntry> rankings,
            List<SubjectStat> subjectStats,
            List<StrugglingStudent> strugglingStudents
    ) {
    }
}
