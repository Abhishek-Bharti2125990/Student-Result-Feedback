package com.srip.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;

/**
 * Grade bands, bound from {@code app.grading.*}. Kept in configuration because
 * grading scales differ per board and per school year, and changing one should
 * not require a redeploy of compiled logic.
 */
@ConfigurationProperties(prefix = "app.grading")
public record GradingProperties(
        List<Band> bands,
        BigDecimal passPercentage
) {

    /**
     * @param minPercentage inclusive floor at which this grade applies
     * @param grade         label shown to users
     * @param points        grade point used for GPA-style aggregates
     */
    public record Band(BigDecimal minPercentage, String grade, BigDecimal points) {
    }

    public GradingProperties {
        bands = (bands == null || bands.isEmpty()) ? defaultBands() : bands;
        passPercentage = passPercentage == null ? new BigDecimal("40") : passPercentage;
        // Highest floor first, so the first match is the correct grade.
        bands = bands.stream()
                .sorted(Comparator.comparing(Band::minPercentage).reversed())
                .toList();
    }

    private static List<Band> defaultBands() {
        return List.of(
                new Band(new BigDecimal("90"), "A+", new BigDecimal("10")),
                new Band(new BigDecimal("80"), "A", new BigDecimal("9")),
                new Band(new BigDecimal("70"), "B+", new BigDecimal("8")),
                new Band(new BigDecimal("60"), "B", new BigDecimal("7")),
                new Band(new BigDecimal("50"), "C", new BigDecimal("6")),
                new Band(new BigDecimal("40"), "D", new BigDecimal("5")),
                new Band(BigDecimal.ZERO, "F", BigDecimal.ZERO));
    }
}
