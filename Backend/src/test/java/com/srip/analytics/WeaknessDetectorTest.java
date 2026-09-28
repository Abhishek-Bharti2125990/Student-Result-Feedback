package com.srip.analytics;

import com.srip.config.AnalyticsProperties;
import com.srip.domain.Exam;
import com.srip.domain.ExamResult;
import com.srip.domain.Student;
import com.srip.domain.Subject;
import com.srip.domain.Topic;
import com.srip.domain.TopicScore;
import com.srip.dto.analytics.AnalyticsDtos.StrongSubject;
import com.srip.dto.analytics.AnalyticsDtos.StrongTopic;
import com.srip.dto.analytics.AnalyticsDtos.WeakSubject;
import com.srip.dto.analytics.AnalyticsDtos.WeakTopic;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class WeaknessDetectorTest {

    private final WeaknessDetector detector =
            new WeaknessDetector(new AnalyticsProperties(null, null, null, null, 2));

    private final Student student = new Student("1001", "Ayushman", "10", "A", "2025-2026");
    private final Exam exam = new Exam("UNIT-TEST-1", "Unit Test 1", "TERM_1",
            LocalDate.of(2025, 7, 15), "10", "2025-2026");

    @Test
    void flagsASubjectBelowTheAbsoluteThreshold() {
        List<WeakSubject> weak = detector.detectWeakSubjects(List.of(
                result("MATHEMATICS", "Mathematics", "45"),
                result("SCIENCE", "Science", "70"),
                result("ENGLISH", "English", "72")));

        assertThat(weak).extracting(WeakSubject::subjectCode).containsExactly("MATHEMATICS");
        assertThat(weak.get(0).reason()).contains("50");
    }

    @Test
    void flagsASubjectFarBelowTheStudentsOwnAverageEvenWhenItIsAGoodMark() {
        // The strong student quietly slipping in one subject. 68% passes every
        // absolute test, so only the relative check can catch it.
        List<WeakSubject> weak = detector.detectWeakSubjects(List.of(
                result("MATHEMATICS", "Mathematics", "68"),
                result("SCIENCE", "Science", "92"),
                result("ENGLISH", "English", "94"),
                result("COMPUTER-SCIENCE", "Computer Science", "90")));

        assertThat(weak).extracting(WeakSubject::subjectCode).containsExactly("MATHEMATICS");
        assertThat(weak.get(0).reason()).contains("below the student's own average");
    }

    @Test
    void reportsNothingWhenEverySubjectIsHealthyAndEven() {
        assertThat(detector.detectWeakSubjects(List.of(
                result("MATHEMATICS", "Mathematics", "80"),
                result("SCIENCE", "Science", "82"),
                result("ENGLISH", "English", "78")))).isEmpty();
    }

    @Test
    void averagesASubjectAcrossEveryExamItAppearsIn() {
        List<WeakSubject> weak = detector.detectWeakSubjects(List.of(
                result("MATHEMATICS", "Mathematics", "30"),
                result("MATHEMATICS", "Mathematics", "50"),
                result("SCIENCE", "Science", "85"),
                result("ENGLISH", "English", "88")));

        assertThat(weak).hasSize(1);
        assertThat(weak.get(0).averagePercentage()).isEqualByComparingTo("40.00");
    }

    @Test
    void emptyInputProducesNoFindingsRatherThanADivideByZero() {
        assertThat(detector.detectWeakSubjects(List.of())).isEmpty();
        assertThat(detector.detectWeakTopics(List.of())).isEmpty();
        assertThat(detector.detectStrongSubjects(List.of())).isEmpty();
        assertThat(detector.detectStrongTopics(List.of())).isEmpty();
    }

    @Test
    void countsHowManyExamsATopicWasWeakIn() {
        Subject maths = new Subject("MATHEMATICS", "Mathematics", "10");
        Topic quadratics = new Topic(maths, "Algebra", "Quadratic Equations");
        Topic triangles = new Topic(maths, "Geometry", "Triangles");

        List<WeakTopic> weak = detector.detectWeakTopics(List.of(
                topicScore(quadratics, "40"),
                topicScore(quadratics, "44"),
                topicScore(triangles, "88")));

        assertThat(weak).extracting(WeakTopic::topicName).containsExactly("Quadratic Equations");
        assertThat(weak.get(0).occurrences()).isEqualTo(2);
        assertThat(weak.get(0).averagePercentage()).isEqualByComparingTo("42.00");
    }

    @Test
    void weakestTopicIsReportedFirst() {
        Subject maths = new Subject("MATHEMATICS", "Mathematics", "10");

        List<WeakTopic> weak = detector.detectWeakTopics(List.of(
                topicScore(new Topic(maths, "Algebra", "Quadratic Equations"), "45"),
                topicScore(new Topic(maths, "Trigonometry", "Trigonometry"), "20"),
                topicScore(new Topic(maths, "Statistics", "Statistics"), "50")));

        assertThat(weak).extracting(WeakTopic::topicName)
                .containsExactly("Trigonometry", "Quadratic Equations", "Statistics");
    }

    // -- Strengths -----------------------------------------------------------

    @Test
    void namesOnlySubjectsAtOrAboveTheStrengthThreshold() {
        // The default threshold is 75. A subject at 74 is a perfectly good mark
        // and still not something worth calling a strength.
        List<StrongSubject> strong = detector.detectStrongSubjects(List.of(
                result("MATHEMATICS", "Mathematics", "74"),
                result("SCIENCE", "Science", "75"),
                result("ENGLISH", "English", "91")));

        assertThat(strong).extracting(StrongSubject::subjectName)
                .containsExactly("English", "Science");
    }

    @Test
    void strongestTopicIsReportedFirst() {
        Subject science = new Subject("SCIENCE", "Science", "10");

        List<StrongTopic> strong = detector.detectStrongTopics(List.of(
                topicScore(new Topic(science, "Physics", "Motion"), "88"),
                topicScore(new Topic(science, "Physics", "Light"), "76"),
                topicScore(new Topic(science, "Biology", "Life Processes"), "60")));

        assertThat(strong).extracting(StrongTopic::topicName).containsExactly("Motion", "Light");
    }

    @Test
    void strengthUsesNoRelativeTestSoAnEvenlyWeakStudentHasNoStrengths() {
        // Every subject is that student's "best subject" relative to something.
        // Only an absolute standard makes the claim mean anything.
        assertThat(detector.detectStrongSubjects(List.of(
                result("MATHEMATICS", "Mathematics", "42"),
                result("SCIENCE", "Science", "38"),
                result("ENGLISH", "English", "46")))).isEmpty();
    }

    @Test
    void aTopicIsAveragedAcrossPapersBeforeBeingCalledAStrength() {
        Subject maths = new Subject("MATHEMATICS", "Mathematics", "10");
        Topic circles = new Topic(maths, "Geometry", "Circles");

        // 90 then 50 averages to 70, which is below the threshold - one good
        // paper should not earn a permanent label.
        assertThat(detector.detectStrongTopics(List.of(
                topicScore(circles, "90"),
                topicScore(circles, "50")))).isEmpty();
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
        return new TopicScore(null, topic, new BigDecimal(percentage),
                new BigDecimal("100"), new BigDecimal(percentage));
    }
}
