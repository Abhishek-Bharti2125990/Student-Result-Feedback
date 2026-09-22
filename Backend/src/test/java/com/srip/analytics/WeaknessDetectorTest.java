package com.srip.analytics;

import com.srip.config.AnalyticsProperties;
import com.srip.domain.Exam;
import com.srip.domain.ExamResult;
import com.srip.domain.Student;
import com.srip.domain.Subject;
import com.srip.domain.Topic;
import com.srip.domain.TopicScore;
import com.srip.dto.analytics.AnalyticsDtos.WeakSubject;
import com.srip.dto.analytics.AnalyticsDtos.WeakTopic;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class WeaknessDetectorTest {

    private final WeaknessDetector detector =
            new WeaknessDetector(new AnalyticsProperties(null, null, null, 2));

    private final Student student = new Student("STU1", "Aarav", "10", "A", "2025-2026");
    private final Exam exam = new Exam("UT1", "Unit Test 1", "TERM_1",
            LocalDate.of(2025, 7, 15), "10", "2025-2026");

    @Test
    void flagsASubjectBelowTheAbsoluteThreshold() {
        List<WeakSubject> weak = detector.detectWeakSubjects(List.of(
                result("MATH", "Mathematics", "45"),
                result("SCI", "Science", "70"),
                result("ENG", "English", "72")));

        assertThat(weak).extracting(WeakSubject::subjectCode).containsExactly("MATH");
        assertThat(weak.get(0).reason()).contains("50");
    }

    @Test
    void flagsASubjectFarBelowTheStudentsOwnAverageEvenWhenItIsAGoodMark() {
        // The strong student quietly slipping in one subject. 68% passes every
        // absolute test, so only the relative check can catch it.
        List<WeakSubject> weak = detector.detectWeakSubjects(List.of(
                result("MATH", "Mathematics", "68"),
                result("SCI", "Science", "92"),
                result("ENG", "English", "94"),
                result("CS", "Computer Science", "90")));

        assertThat(weak).extracting(WeakSubject::subjectCode).containsExactly("MATH");
        assertThat(weak.get(0).reason()).contains("below the student's own average");
    }

    @Test
    void reportsNothingWhenEverySubjectIsHealthyAndEven() {
        assertThat(detector.detectWeakSubjects(List.of(
                result("MATH", "Mathematics", "80"),
                result("SCI", "Science", "82"),
                result("ENG", "English", "78")))).isEmpty();
    }

    @Test
    void averagesASubjectAcrossEveryExamItAppearsIn() {
        List<WeakSubject> weak = detector.detectWeakSubjects(List.of(
                result("MATH", "Mathematics", "30"),
                result("MATH", "Mathematics", "50"),
                result("SCI", "Science", "85"),
                result("ENG", "English", "88")));

        assertThat(weak).hasSize(1);
        assertThat(weak.get(0).averagePercentage()).isEqualByComparingTo("40.00");
    }

    @Test
    void emptyInputProducesNoFindingsRatherThanADivideByZero() {
        assertThat(detector.detectWeakSubjects(List.of())).isEmpty();
        assertThat(detector.detectWeakTopics(List.of())).isEmpty();
    }

    @Test
    void countsHowManyExamsATopicWasWeakIn() {
        Subject maths = new Subject("MATH", "Mathematics", "10");
        Topic algebra = new Topic(maths, "Algebra");
        Topic geometry = new Topic(maths, "Geometry");

        List<WeakTopic> weak = detector.detectWeakTopics(List.of(
                topicScore(algebra, "40"),
                topicScore(algebra, "44"),
                topicScore(geometry, "88")));

        assertThat(weak).extracting(WeakTopic::topicName).containsExactly("Algebra");
        assertThat(weak.get(0).occurrences()).isEqualTo(2);
        assertThat(weak.get(0).averagePercentage()).isEqualByComparingTo("42.00");
    }

    @Test
    void weakestTopicIsReportedFirst() {
        Subject maths = new Subject("MATH", "Mathematics", "10");

        List<WeakTopic> weak = detector.detectWeakTopics(List.of(
                topicScore(new Topic(maths, "Algebra"), "45"),
                topicScore(new Topic(maths, "Geometry"), "20"),
                topicScore(new Topic(maths, "Statistics"), "50")));

        assertThat(weak).extracting(WeakTopic::topicName)
                .containsExactly("Geometry", "Algebra", "Statistics");
    }

    private ExamResult result(String code, String name, String percentage) {
        ExamResult result = new ExamResult(student, exam, new Subject(code, name, "10"));
        result.setMaxMarks(new BigDecimal("100"));
        result.setMarksObtained(new BigDecimal(percentage));
        result.setPercentage(new BigDecimal(percentage));
        result.setGrade("X");
        return result;
    }

    private TopicScore topicScore(Topic topic, String percentage) {
        return new TopicScore(topic, new BigDecimal(percentage),
                new BigDecimal("100"), new BigDecimal(percentage));
    }
}
