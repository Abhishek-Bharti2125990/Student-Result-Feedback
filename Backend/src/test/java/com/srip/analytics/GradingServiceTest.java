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
        // Null bands, pass mark and category bounds exercise the defaults.
        grading = new GradingService(new GradingProperties(null, null, null));
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
                new BigDecimal("40"),
                null);

        assertThat(new GradingService(shuffled).grade(new BigDecimal("95"))).isEqualTo("A+");
    }

    @ParameterizedTest
    @CsvSource({
            "0,     CRITICAL",
            "49.99, CRITICAL",
            // On a boundary the higher band wins: "50 to 70" includes 50.
            "50,    AVERAGE",
            "69.99, AVERAGE",
            "70,    GOOD",
            "84.99, GOOD",
            "85,    EXCELLENT",
            "100,   EXCELLENT"
    })
    void scoreCategoryBoundariesFallIntoTheHigherBand(String percentage, ScoreCategory expected) {
        assertThat(grading.category(new BigDecimal(percentage))).isEqualTo(expected);
    }

    @Test
    void aMissingPercentageIsTreatedAsCriticalRatherThanThrowing() {
        // A student with no marks recorded is the opposite of excellent, and a
        // dashboard must still be able to place them somewhere.
        assertThat(grading.category(null)).isEqualTo(ScoreCategory.CRITICAL);
    }

    @Test
    void categoryBoundsAreConfigurable() {
        GradingProperties strict = new GradingProperties(null, null,
                new GradingProperties.CategoryBounds(
                        new BigDecimal("60"), new BigDecimal("75"), new BigDecimal("90")));
        GradingService strictGrading = new GradingService(strict);

        assertThat(strictGrading.category(new BigDecimal("55"))).isEqualTo(ScoreCategory.CRITICAL);
        assertThat(strictGrading.category(new BigDecimal("85"))).isEqualTo(ScoreCategory.GOOD);
        assertThat(strictGrading.category(new BigDecimal("90"))).isEqualTo(ScoreCategory.EXCELLENT);
    }

    @Test
    void everyCategoryCarriesTheHeadingTheDashboardsShow() {
        assertThat(ScoreCategory.CRITICAL.label()).isEqualTo("Students Below 50%");
        assertThat(ScoreCategory.AVERAGE.label()).isEqualTo("Students Between 50 and 70");
        assertThat(ScoreCategory.GOOD.label()).isEqualTo("Students Between 70 and 85");
        assertThat(ScoreCategory.EXCELLENT.label()).isEqualTo("Students Above 85");
    }
}
