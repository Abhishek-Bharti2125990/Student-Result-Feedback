package com.srip.analytics;

import com.srip.config.AnalyticsProperties;
import com.srip.domain.Exam;
import com.srip.domain.ExamResult;
import com.srip.domain.Student;
import com.srip.domain.Subject;
import com.srip.dto.analytics.AnalyticsDtos.PerformanceTrend;
import com.srip.dto.analytics.AnalyticsDtos.SubjectTrend;
import com.srip.dto.analytics.AnalyticsDtos.TrendPoint;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TrendAnalyzerTest {

    private final TrendAnalyzer analyzer =
            new TrendAnalyzer(new AnalyticsProperties(null, null, null, 2));

    private final Student student = new Student("STU1", "Aarav", "10", "A", "2025-2026");
    private final Subject maths = new Subject("MATH", "Mathematics", "10");

    private final Exam july = exam(1L, "UT1", LocalDate.of(2025, 7, 15));
    private final Exam september = exam(2L, "MID", LocalDate.of(2025, 9, 20));
    private final Exam november = exam(3L, "UT2", LocalDate.of(2025, 11, 10));

    @Test
    void ordersTheSeriesByExamDateNotByInsertionOrder() {
        // Results are routinely backfilled out of sequence; insertion order
        // would invert the trend.
        PerformanceTrend trend = analyzer.analyse(List.of(
                result(november, maths, "80"),
                result(july, maths, "50"),
                result(september, maths, "65")));

        assertThat(trend.overall()).extracting(TrendPoint::examCode).containsExactly("UT1", "MID", "UT2");
    }

    @Test
    void risingMarksReadAsImproving() {
        PerformanceTrend trend = analyzer.analyse(List.of(
                result(july, maths, "50"),
                result(september, maths, "72")));

        assertThat(trend.overallDirection()).isEqualTo(TrendAnalyzer.IMPROVING);
        assertThat(trend.overallChange()).isEqualByComparingTo("22.00");
    }

    @Test
    void fallingMarksReadAsDeclining() {
        PerformanceTrend trend = analyzer.analyse(List.of(
                result(july, maths, "80"),
                result(september, maths, "61")));

        assertThat(trend.overallDirection()).isEqualTo(TrendAnalyzer.DECLINING);
        assertThat(trend.overallChange()).isEqualByComparingTo("-19.00");
    }

    @Test
    void aSmallMovementIsNoiseRatherThanATrend() {
        PerformanceTrend trend = analyzer.analyse(List.of(
                result(july, maths, "70"),
                result(september, maths, "73")));

        assertThat(trend.overallDirection()).isEqualTo(TrendAnalyzer.STABLE);
    }

    @Test
    void oneExamIsNotEnoughToClaimADirection() {
        PerformanceTrend trend = analyzer.analyse(List.of(result(july, maths, "70")));

        assertThat(trend.overallDirection()).isEqualTo(TrendAnalyzer.INSUFFICIENT_DATA);
        assertThat(trend.overall()).hasSize(1);
    }

    @Test
    void noResultsProduceAnEmptyTrendRatherThanAnError() {
        PerformanceTrend trend = analyzer.analyse(List.of());

        assertThat(trend.overall()).isEmpty();
        assertThat(trend.bySubject()).isEmpty();
        assertThat(trend.overallDirection()).isEqualTo(TrendAnalyzer.INSUFFICIENT_DATA);
    }

    @Test
    void overallPercentageCombinesSubjectsWithinTheSameExam() {
        Subject science = new Subject("SCI", "Science", "10");

        PerformanceTrend trend = analyzer.analyse(List.of(
                result(july, maths, "40"),
                result(july, science, "60"),
                result(september, maths, "70"),
                result(september, science, "90")));

        // 100/200 then 160/200.
        assertThat(trend.overall()).extracting(TrendPoint::percentage)
                .containsExactly(new BigDecimal("50.00"), new BigDecimal("80.00"));
    }

    @Test
    void steepestDecliningSubjectIsListedFirst() {
        Subject science = new Subject("SCI", "Science", "10");

        PerformanceTrend trend = analyzer.analyse(List.of(
                result(july, maths, "80"),
                result(september, maths, "55"),
                result(july, science, "60"),
                result(september, science, "70")));

        assertThat(trend.bySubject()).extracting(SubjectTrend::subjectCode)
                .containsExactly("MATH", "SCI");
        assertThat(trend.bySubject().get(0).direction()).isEqualTo(TrendAnalyzer.DECLINING);
    }

    private static Exam exam(Long id, String code, LocalDate date) {
        Exam exam = new Exam(code, code, "TERM_1", date, "10", "2025-2026");
        // Trends group by exam id, which JPA assigns on persist; set it directly
        // so the analyzer can be tested without a database.
        ReflectionTestUtils.setField(exam, "id", id);
        return exam;
    }

    private ExamResult result(Exam exam, Subject subject, String percentage) {
        ExamResult result = new ExamResult(student, exam, subject);
        result.setMarksObtained(new BigDecimal(percentage));
        result.setMaxMarks(new BigDecimal("100"));
        result.setPercentage(new BigDecimal(percentage));
        result.setGrade("X");
        return result;
    }
}
