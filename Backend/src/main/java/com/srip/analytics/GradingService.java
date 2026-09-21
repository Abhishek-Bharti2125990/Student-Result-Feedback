package com.srip.analytics;

import com.srip.config.GradingProperties;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Percentage and grade calculation.
 *
 * <p>All arithmetic uses {@link BigDecimal} with an explicit scale. Marks are
 * money-like: a mark of 49.995 must not round its way across a pass boundary
 * differently depending on the platform, which is exactly what {@code double}
 * would risk.
 */
@Service
public class GradingService {

    private static final int PERCENTAGE_SCALE = 2;
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private final GradingProperties properties;

    public GradingService(GradingProperties properties) {
        this.properties = properties;
    }

    /** @return marks as a percentage of max, or zero when max is zero or absent */
    public BigDecimal percentage(BigDecimal marksObtained, BigDecimal maxMarks) {
        if (marksObtained == null || maxMarks == null || maxMarks.signum() <= 0) {
            return BigDecimal.ZERO.setScale(PERCENTAGE_SCALE, RoundingMode.HALF_UP);
        }
        return marksObtained
                .multiply(HUNDRED)
                .divide(maxMarks, PERCENTAGE_SCALE, RoundingMode.HALF_UP);
    }

    /** The grade label for a percentage, from the configured bands. */
    public String grade(BigDecimal percentage) {
        BigDecimal value = percentage == null ? BigDecimal.ZERO : percentage;
        return properties.bands().stream()
                .filter(band -> value.compareTo(band.minPercentage()) >= 0)
                .findFirst()
                .map(GradingProperties.Band::grade)
                // Bands are sorted descending and include a zero floor, so this
                // is unreachable with valid configuration.
                .orElse("F");
    }

    public BigDecimal gradePoints(BigDecimal percentage) {
        BigDecimal value = percentage == null ? BigDecimal.ZERO : percentage;
        return properties.bands().stream()
                .filter(band -> value.compareTo(band.minPercentage()) >= 0)
                .findFirst()
                .map(GradingProperties.Band::points)
                .orElse(BigDecimal.ZERO);
    }

    public boolean isPass(BigDecimal percentage) {
        return percentage != null && percentage.compareTo(properties.passPercentage()) >= 0;
    }

    public BigDecimal passPercentage() {
        return properties.passPercentage();
    }

    public BigDecimal scale(BigDecimal value) {
        return value == null
                ? BigDecimal.ZERO.setScale(PERCENTAGE_SCALE, RoundingMode.HALF_UP)
                : value.setScale(PERCENTAGE_SCALE, RoundingMode.HALF_UP);
    }
}
