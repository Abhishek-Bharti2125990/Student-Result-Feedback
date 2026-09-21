package com.srip.analytics;

import com.srip.config.AnalyticsProperties;
import com.srip.domain.ExamResult;
import com.srip.domain.TopicScore;
import com.srip.dto.analytics.AnalyticsDtos.WeakSubject;
import com.srip.dto.analytics.AnalyticsDtos.WeakTopic;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Finds the subjects and topics a student is losing marks in.
 *
 * <p>Two tests are applied, because one alone misses real cases. An absolute
 * threshold catches outright failure. A relative test - a subject well below
 * the student's own average - catches the strong student who scores 68 in one
 * subject while averaging 88 in the rest; that is a genuine gap that no
 * absolute cutoff would ever flag.
 */
@Service
public class WeaknessDetector {

    private final AnalyticsProperties properties;

    public WeaknessDetector(AnalyticsProperties properties) {
        this.properties = properties;
    }

    /**
     * @param results every result to consider; pass one exam's results for a
     *                per-exam view, or a student's full history for a standing view
     */
    public List<WeakSubject> detectWeakSubjects(List<ExamResult> results) {
        if (results.isEmpty()) {
            return List.of();
        }

        Map<String, Aggregate> bySubject = new LinkedHashMap<>();
        for (ExamResult result : results) {
            String code = result.getSubject().getCode();
            bySubject.computeIfAbsent(code,
                            key -> new Aggregate(code, result.getSubject().getName()))
                    .add(result.getPercentage());
        }

        BigDecimal overallAverage = average(bySubject.values().stream()
                .map(Aggregate::average)
                .toList());

        BigDecimal absoluteThreshold = properties.weakSubjectThreshold();
        BigDecimal relativeFloor = overallAverage.subtract(properties.relativeWeaknessMargin());

        List<WeakSubject> weak = new ArrayList<>();
        for (Aggregate aggregate : bySubject.values()) {
            BigDecimal value = aggregate.average();
            boolean belowAbsolute = value.compareTo(absoluteThreshold) <= 0;
            boolean belowOwnAverage = value.compareTo(relativeFloor) < 0;

            if (!belowAbsolute && !belowOwnAverage) {
                continue;
            }
            weak.add(new WeakSubject(
                    aggregate.code(),
                    aggregate.name(),
                    value,
                    reasonFor(belowAbsolute, belowOwnAverage, absoluteThreshold, overallAverage)));
        }

        weak.sort(Comparator.comparing(WeakSubject::averagePercentage));
        return weak;
    }

    /**
     * @param scores topic scores across however many exams are in scope
     * @return weak topics, worst first, each with the number of exams in which
     *         it fell below the threshold
     */
    public List<WeakTopic> detectWeakTopics(List<TopicScore> scores) {
        if (scores.isEmpty()) {
            return List.of();
        }

        BigDecimal threshold = properties.weakTopicThreshold();
        Map<String, TopicAggregate> byTopic = new LinkedHashMap<>();

        for (TopicScore score : scores) {
            String subjectCode = score.getTopic().getSubject().getCode();
            String topicName = score.getTopic().getName();
            String key = subjectCode + "::" + topicName;
            TopicAggregate aggregate = byTopic.computeIfAbsent(key,
                    ignored -> new TopicAggregate(subjectCode, topicName));
            aggregate.add(score.getPercentage(), threshold);
        }

        return byTopic.values().stream()
                .filter(aggregate -> aggregate.average().compareTo(threshold) <= 0
                        || aggregate.weakOccurrences() > 0)
                .map(aggregate -> new WeakTopic(
                        aggregate.subjectCode(),
                        aggregate.topicName(),
                        aggregate.average(),
                        aggregate.weakOccurrences()))
                // Repeated weakness outranks a single low score at the same average.
                .sorted(Comparator.comparing(WeakTopic::averagePercentage)
                        .thenComparing(Comparator.comparingInt(WeakTopic::occurrences).reversed()))
                .toList();
    }

    private String reasonFor(boolean belowAbsolute, boolean belowOwnAverage,
                             BigDecimal threshold, BigDecimal ownAverage) {
        if (belowAbsolute && belowOwnAverage) {
            return "Below the %s%% pass-risk threshold and below the student's own average of %s%%"
                    .formatted(threshold.toPlainString(), ownAverage.toPlainString());
        }
        if (belowAbsolute) {
            return "At or below the %s%% threshold".formatted(threshold.toPlainString());
        }
        return "More than %s points below the student's own average of %s%%"
                .formatted(properties.relativeWeaknessMargin().toPlainString(), ownAverage.toPlainString());
    }

    private static BigDecimal average(List<BigDecimal> values) {
        if (values.isEmpty()) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        BigDecimal sum = values.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        return sum.divide(BigDecimal.valueOf(values.size()), 2, RoundingMode.HALF_UP);
    }

    /** Running average for one subject. */
    private static final class Aggregate {
        private final String code;
        private final String name;
        private final List<BigDecimal> percentages = new ArrayList<>();

        Aggregate(String code, String name) {
            this.code = code;
            this.name = name;
        }

        void add(BigDecimal percentage) {
            if (percentage != null) {
                percentages.add(percentage);
            }
        }

        String code() {
            return code;
        }

        String name() {
            return name;
        }

        BigDecimal average() {
            return WeaknessDetector.average(percentages);
        }
    }

    /** Running average plus a count of how often the topic was weak. */
    private static final class TopicAggregate {
        private final String subjectCode;
        private final String topicName;
        private final List<BigDecimal> percentages = new ArrayList<>();
        private int weakOccurrences;

        TopicAggregate(String subjectCode, String topicName) {
            this.subjectCode = subjectCode;
            this.topicName = topicName;
        }

        void add(BigDecimal percentage, BigDecimal threshold) {
            if (percentage == null) {
                return;
            }
            percentages.add(percentage);
            if (percentage.compareTo(threshold) <= 0) {
                weakOccurrences++;
            }
        }

        String subjectCode() {
            return subjectCode;
        }

        String topicName() {
            return topicName;
        }

        int weakOccurrences() {
            return weakOccurrences;
        }

        BigDecimal average() {
            return WeaknessDetector.average(percentages);
        }
    }
}
