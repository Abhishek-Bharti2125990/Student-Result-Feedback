package com.srip.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;

/**
 * Thresholds that decide what counts as "weak", bound from {@code app.analytics.*}.
 *
 * @param weakSubjectThreshold    absolute percentage at or below which a subject is weak
 * @param weakTopicThreshold      absolute percentage at or below which a topic is weak
 * @param relativeWeaknessMargin  a subject this many points below the student's own
 *                                average is also weak, which catches the strong
 *                                student who is quietly slipping in one subject
 * @param trendMinExams           minimum number of exams before a trend is reported
 */
@ConfigurationProperties(prefix = "app.analytics")
public record AnalyticsProperties(
        BigDecimal weakSubjectThreshold,
        BigDecimal weakTopicThreshold,
        BigDecimal relativeWeaknessMargin,
        int trendMinExams
) {

    public AnalyticsProperties {
        weakSubjectThreshold = weakSubjectThreshold == null ? new BigDecimal("50") : weakSubjectThreshold;
        weakTopicThreshold = weakTopicThreshold == null ? new BigDecimal("50") : weakTopicThreshold;
        relativeWeaknessMargin = relativeWeaknessMargin == null ? new BigDecimal("15") : relativeWeaknessMargin;
        trendMinExams = trendMinExams < 2 ? 2 : trendMinExams;
    }
}
