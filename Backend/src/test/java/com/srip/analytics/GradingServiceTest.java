package com.srip.analytics;

import com.srip.config.GradingProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class GradingServiceTest {

    private GradingService grading;

    @BeforeEach
    void setUp() {
        // Null bands and pass mark exercise the configured defaults.
        grading = new GradingService(new GradingProperties(null, null));
    }

    @Test
    void percentageIsScaledToTwoDecimals() {
        assertThat(grading.percentage(new BigDecimal("1"), new BigDecimal("3")))
                .isEqualByComparingTo("33.33");
    }

    @Test
    void percentageOfZeroMaxMarksIsZeroRatherThanAnArithmeticError() {
        // A malformed row reaching this far must not blow up a whole chunk.
        assertThat(grading.percentage(new BigDecimal("10"), BigDecimal.ZERO))
                .isEqualByComparingTo("0.00");
    }

    @Test
    void nullMarksAreTreatedAsZero() {
        assertThat(grading.percentage(null, new BigDecimal("100"))).isEqualByComparingTo("0.00");
    }

    @ParameterizedTest
    @CsvSource({
            "100, A+",
            "90,  A+",
            "89.99, A",
            "80,  A",
            "70,  B+",
            "60,  B",
            "50,  C",
            "40,  D",
            "39.99, F",
            "0,   F"
    })
    void gradeBoundariesAreInclusiveAtTheFloor(String percentage, String expectedGrade) {
        assertThat(grading.grade(new BigDecimal(percentage))).isEqualTo(expectedGrade);
    }

    @Test
    void passMarkIsInclusive() {
        assertThat(grading.isPass(new BigDecimal("40"))).isTrue();
        assertThat(grading.isPass(new BigDecimal("39.99"))).isFalse();
    }

    @Test
    void gradePointsFollowTheSameBands() {
        assertThat(grading.gradePoints(new BigDecimal("95"))).isEqualByComparingTo("10");
        assertThat(grading.gradePoints(new BigDecimal("10"))).isEqualByComparingTo("0");
    }

    @Test
    void bandsAreSortedDescendingRegardlessOfConfigurationOrder() {
        // Configuration is human-edited YAML, so the order cannot be trusted.
        GradingProperties shuffled = new GradingProperties(
                java.util.List.of(
                        new GradingProperties.Band(new BigDecimal("40"), "D", new BigDecimal("5")),
                        new GradingProperties.Band(new BigDecimal("90"), "A+", new BigDecimal("10")),
                        new GradingProperties.Band(BigDecimal.ZERO, "F", BigDecimal.ZERO)),
                new BigDecimal("40"));

        assertThat(new GradingService(shuffled).grade(new BigDecimal("95"))).isEqualTo("A+");
    }
}
