package com.srip.analytics;

import com.srip.config.GradingProperties;
import com.srip.domain.Student;
import com.srip.dto.analytics.AnalyticsDtos.RankingEntry;
import com.srip.repository.ExamResultRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RankingServiceTest {

    private final RankingService ranking =
            new RankingService(new GradingService(new GradingProperties(null, null)));

    @Test
    void ranksByPercentageDescending() {
        List<RankingEntry> result = ranking.rank(
                List.of(
                        totals(1L, "300", "500"),
                        totals(2L, "450", "500"),
                        totals(3L, "400", "500")),
                students());

        assertThat(result).extracting(RankingEntry::studentId).containsExactly(2L, 3L, 1L);
        assertThat(result).extracting(RankingEntry::rank).containsExactly(1, 2, 3);
        assertThat(result.get(0).percentage()).isEqualByComparingTo("90.00");
        assertThat(result.get(0).studentName()).isEqualTo("Beta");
    }

    @Test
    void tiedStudentsShareARankAndTheNextRankSkips() {
        // Two students on identical marks must not be silently ordered by id -
        // that would invent a winner the data does not support.
        List<RankingEntry> result = ranking.rank(
                List.of(
                        totals(1L, "450", "500"),
                        totals(2L, "450", "500"),
                        totals(3L, "400", "500")),
                students());

        assertThat(result).extracting(RankingEntry::rank).containsExactly(1, 1, 3);
    }

    @Test
    void percentageComparisonIgnoresDifferingTotalMaximums() {
        // A student who missed a paper has a smaller denominator; ranking on
        // raw marks would penalise them twice.
        List<RankingEntry> result = ranking.rank(
                List.of(
                        totals(1L, "90", "100"),
                        totals(2L, "400", "500")),
                students());

        assertThat(result.get(0).studentId()).isEqualTo(1L);
        assertThat(result.get(0).percentage()).isEqualByComparingTo("90.00");
    }

    @Test
    void rankOfReturnsZeroForAStudentWhoDidNotSitTheExam() {
        List<RankingEntry> result = ranking.rank(List.of(totals(1L, "400", "500")), students());

        assertThat(ranking.rankOf(result, 1L)).isEqualTo(1);
        assertThat(ranking.rankOf(result, 99L)).isZero();
    }

    @Test
    void missingStudentRecordDoesNotBreakTheTable() {
        List<RankingEntry> result = ranking.rank(List.of(totals(42L, "400", "500")), students());

        assertThat(result).hasSize(1);
        assertThat(result.get(0).studentName()).isEqualTo("Unknown");
    }

    private Map<Long, Student> students() {
        return Map.of(
                1L, new Student("STU1", "Alpha", "10", "A", "2025-2026"),
                2L, new Student("STU2", "Beta", "10", "A", "2025-2026"),
                3L, new Student("STU3", "Gamma", "10", "A", "2025-2026"));
    }

    private static ExamResultRepository.StudentTotals totals(Long studentId, String marks, String max) {
        return new Totals(studentId, new BigDecimal(marks), new BigDecimal(max), 5L);
    }

    /** Stands in for the Spring Data interface projection. */
    private record Totals(Long id, BigDecimal marks, BigDecimal max, Long count)
            implements ExamResultRepository.StudentTotals {

        @Override
        public Long getStudentId() {
            return id;
        }

        @Override
        public BigDecimal getTotalMarks() {
            return marks;
        }

        @Override
        public BigDecimal getTotalMax() {
            return max;
        }

        @Override
        public Long getSubjectCount() {
            return count;
        }
    }
}
