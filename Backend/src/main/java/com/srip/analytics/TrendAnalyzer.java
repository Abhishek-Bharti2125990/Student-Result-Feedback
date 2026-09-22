package com.srip.analytics;

import com.srip.config.AnalyticsProperties;
import com.srip.domain.Exam;
import com.srip.domain.ExamResult;
import com.srip.dto.analytics.AnalyticsDtos.PerformanceTrend;
import com.srip.dto.analytics.AnalyticsDtos.SubjectTrend;
import com.srip.dto.analytics.AnalyticsDtos.TrendPoint;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Turns a student's result history into direction of travel.
 *
 * <p>Series are ordered by exam date rather than by insertion order, because
 * results are often uploaded out of sequence - a missed paper backfilled weeks
 * later would otherwise invert the trend.
 */
@Service
public class TrendAnalyzer {

    /** Below this movement, a change is noise rather than a trend. */
    private static final BigDecimal SIGNIFICANT_CHANGE = new BigDecimal("5");

    public static final String IMPROVING = "IMPROVING";
    public static final String DECLINING = "DECLINING";
    public static final String STABLE = "STABLE";
    public static final String INSUFFICIENT_DATA = "INSUFFICIENT_DATA";

    private final AnalyticsProperties properties;

    public TrendAnalyzer(AnalyticsProperties properties) {
        this.properties = properties;
    }

    /** @param results a student's full history; grouping by exam is done here */
    public PerformanceTrend analyse(List<ExamResult> results) {
        if (results.isEmpty()) {
            return new PerformanceTrend(List.of(), List.of(),
                    BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP), INSUFFICIENT_DATA);
        }

        List<TrendPoint> overall = overallSeries(results);
        List<SubjectTrend> bySubject = subjectSeries(results);

        BigDecimal overallChange = change(overall);
        return new PerformanceTrend(overall, bySubject, overallChange, direction(overall.size(), overallChange));
    }

    private List<TrendPoint> overallSeries(List<ExamResult> results) {
        Map<Long, ExamAggregate> byExam = new LinkedHashMap<>();
        for (ExamResult result : results) {
            Exam exam = result.getExam();
            byExam.computeIfAbsent(exam.getId(), ignored -> new ExamAggregate(exam))
                    .add(result.getMarksObtained(), result.getMaxMarks());
        }
        return byExam.values().stream()
                .sorted(Comparator.comparing(aggregate -> aggregate.exam().getExamDate()))
                .map(aggregate -> new TrendPoint(
                        aggregate.exam().getCode(),
                        aggregate.exam().getName(),
                        aggregate.exam().getExamDate(),
                        aggregate.percentage()))
                .toList();
    }

    private List<SubjectTrend> subjectSeries(List<ExamResult> results) {
        Map<String, List<ExamResult>> bySubject = new LinkedHashMap<>();
        for (ExamResult result : results) {
            bySubject.computeIfAbsent(result.getSubject().getCode(), ignored -> new ArrayList<>()).add(result);
        }

        List<SubjectTrend> trends = new ArrayList<>(bySubject.size());
        for (Map.Entry<String, List<ExamResult>> entry : bySubject.entrySet()) {
            List<ExamResult> subjectResults = entry.getValue();
            List<TrendPoint> points = subjectResults.stream()
                    .sorted(Comparator.comparing(result -> result.getExam().getExamDate()))
                    .map(result -> new TrendPoint(
                            result.getExam().getCode(),
                            result.getExam().getName(),
                            result.getExam().getExamDate(),
                            result.getPercentage()))
                    .toList();

            BigDecimal subjectChange = change(points);
            trends.add(new SubjectTrend(
                    entry.getKey(),
                    subjectResults.get(0).getSubject().getName(),
                    points,
                    subjectChange,
                    direction(points.size(), subjectChange)));
        }

        // Steepest decline first: that is what a teacher needs to see.
        trends.sort(Comparator.comparing(SubjectTrend::change));
        return trends;
    }

    /** Last minus first. A middle dip that recovers is not a decline. */
    private BigDecimal change(List<TrendPoint> points) {
        if (points.size() < 2) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        BigDecimal first = points.get(0).percentage();
        BigDecimal last = points.get(points.size() - 1).percentage();
        return last.subtract(first).setScale(2, RoundingMode.HALF_UP);
    }

    private String direction(int pointCount, BigDecimal change) {
        if (pointCount < properties.trendMinExams()) {
            return INSUFFICIENT_DATA;
        }
        if (change.compareTo(SIGNIFICANT_CHANGE) >= 0) {
            return IMPROVING;
        }
        if (change.compareTo(SIGNIFICANT_CHANGE.negate()) <= 0) {
            return DECLINING;
        }
        return STABLE;
    }

    /** Accumulates one exam's marks so an overall percentage can be derived. */
    private static final class ExamAggregate {
        private final Exam exam;
        private BigDecimal marks = BigDecimal.ZERO;
        private BigDecimal max = BigDecimal.ZERO;

        ExamAggregate(Exam exam) {
            this.exam = exam;
        }

        void add(BigDecimal obtained, BigDecimal maximum) {
            if (obtained != null) {
                marks = marks.add(obtained);
            }
            if (maximum != null) {
                max = max.add(maximum);
            }
        }

        Exam exam() {
            return exam;
        }

        BigDecimal percentage() {
            if (max.signum() <= 0) {
                return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
            }
            return marks.multiply(new BigDecimal("100")).divide(max, 2, RoundingMode.HALF_UP);
        }
    }
}
