package com.srip.ai;

import com.srip.dto.ai.FeedbackDtos.ParentFeedback;
import com.srip.dto.ai.FeedbackDtos.StudentFeedback;
import com.srip.dto.ai.FeedbackDtos.StudyPlanItem;
import com.srip.dto.ai.FeedbackDtos.TeacherFeedback;
import com.srip.dto.ai.FeedbackDtos.WeakStudentNote;
import com.srip.dto.ai.FeedbackDtos.WeeklyGuidanceItem;
import com.srip.dto.analytics.AnalyticsDtos.ClassAnalytics;
import com.srip.dto.analytics.AnalyticsDtos.StrugglingStudent;
import com.srip.dto.analytics.AnalyticsDtos.StudentSnapshot;
import com.srip.dto.analytics.AnalyticsDtos.SubjectPerformance;
import com.srip.dto.analytics.AnalyticsDtos.SubjectStat;
import com.srip.dto.analytics.AnalyticsDtos.SubjectTrend;
import com.srip.dto.analytics.AnalyticsDtos.WeakSubject;
import com.srip.dto.analytics.AnalyticsDtos.WeakTopic;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Writes the same three documents from templates, with no model call.
 *
 * <p>This exists so the platform is fully functional without an API key: every
 * endpoint returns real, data-grounded feedback on a fresh clone. It is also
 * the degraded path when the Claude API is unreachable, which matters because a
 * result portal should not go dark because an upstream service is down.
 *
 * <p>The output is honest about what it is - responses carry
 * {@code source: "FALLBACK"} - and it is deliberately more mechanical than the
 * generated version. It restates and prioritises the analytics; it does not
 * attempt the judgement the model brings.
 */
@Component
public class FallbackFeedbackWriter {

    private static final int MAX_LIST_ITEMS = 5;

    public StudentFeedback studentFeedback(StudentSnapshot snapshot) {
        List<SubjectPerformance> ranked = snapshot.subjects().stream()
                .sorted(Comparator.comparing(SubjectPerformance::percentage).reversed())
                .toList();

        List<String> strengths = new ArrayList<>();
        for (SubjectPerformance subject : ranked) {
            if (strengths.size() >= 3) {
                break;
            }
            if (subject.percentage().compareTo(new BigDecimal("60")) >= 0) {
                strengths.add("%s at %s%% (grade %s)%s".formatted(
                        subject.subjectName(),
                        plain(subject.percentage()),
                        subject.grade(),
                        aboveClassNote(subject)));
            }
        }
        if (strengths.isEmpty()) {
            strengths.add("Your strongest subject this exam was %s at %s%% - that is the base to build on."
                    .formatted(ranked.get(0).subjectName(), plain(ranked.get(0).percentage())));
        }

        List<String> weaknesses = snapshot.weakSubjects().stream()
                .limit(MAX_LIST_ITEMS)
                .map(weak -> "%s at %s%% - %s".formatted(
                        weak.subjectName(), plain(weak.averagePercentage()), weak.reason()))
                .toList();
        if (weaknesses.isEmpty()) {
            weaknesses = List.of("No subject fell below the weakness thresholds in this exam.");
        }

        return new StudentFeedback(
                studentSummary(snapshot),
                strengths,
                weaknesses,
                studyPlan(snapshot),
                snapshot.passed()
                        ? "You are on track. Pick the one weakest topic and give it a fortnight of steady attention."
                        : "This exam did not go the way you wanted, and that is recoverable. Start with the single weakest topic rather than everything at once.");
    }

    public TeacherFeedback teacherFeedback(ClassAnalytics analytics) {
        List<WeakStudentNote> weakStudents = analytics.strugglingStudents().stream()
                .limit(10)
                .map(this::toWeakStudentNote)
                .toList();

        List<SubjectStat> weakestSubjects = analytics.subjectStats().stream()
                .filter(stat -> stat.failCount() > 0 || stat.average().compareTo(new BigDecimal("55")) < 0)
                .limit(3)
                .toList();

        List<String> interventions = new ArrayList<>();
        for (SubjectStat stat : weakestSubjects) {
            interventions.add("%s averaged %s%% with %d of %d below the pass mark - re-teach the core unit before moving on."
                    .formatted(stat.subjectName(), plain(stat.average()),
                            stat.failCount(), stat.passCount() + stat.failCount()));
        }
        if (analytics.classAveragePercentage().compareTo(new BigDecimal("50")) < 0) {
            interventions.add("The class average of %s%% suggests a pacing problem rather than individual gaps; consider reviewing the term plan."
                    .formatted(plain(analytics.classAveragePercentage())));
        }
        if (interventions.isEmpty()) {
            interventions.add("No class-wide intervention is indicated; the spread is within the expected range.");
        }

        List<String> remedial = new ArrayList<>();
        if (!weakStudents.isEmpty()) {
            remedial.add("Run a weekly 40-minute remedial slot for the %d flagged students."
                    .formatted(weakStudents.size()));
            remedial.add("Pair each flagged student with a peer who scored above 75%% in that subject.");
            remedial.add("Send a short written note home for any student below the pass mark, with two specific actions.");
        }
        if (!weakestSubjects.isEmpty()) {
            remedial.add("Issue a targeted practice set for %s and mark it within a week so the gap is visible early."
                    .formatted(weakestSubjects.get(0).subjectName()));
        }
        if (remedial.isEmpty()) {
            remedial.add("Maintain current practice; no remedial action is indicated by this exam.");
        }

        List<String> reteach = weakestSubjects.stream()
                .map(stat -> "%s - class average %s%%".formatted(stat.subjectName(), plain(stat.average())))
                .toList();

        String summary = "%s in class %s averaged %s%% across %d students, ranging from %s%% to %s%%. %d student(s) need attention."
                .formatted(analytics.examName(), analytics.className(),
                        plain(analytics.classAveragePercentage()), analytics.classSize(),
                        plain(analytics.lowestPercentage()), plain(analytics.highestPercentage()),
                        analytics.strugglingStudents().size());

        return new TeacherFeedback(summary, weakStudents, interventions, remedial,
                reteach.isEmpty() ? List.of("No topic requires class-wide re-teaching.") : reteach);
    }

    public ParentFeedback parentFeedback(StudentSnapshot snapshot) {
        String name = firstName(snapshot.studentName());

        List<String> positives = new ArrayList<>();
        snapshot.subjects().stream()
                .sorted(Comparator.comparing(SubjectPerformance::percentage).reversed())
                .limit(2)
                .forEach(subject -> positives.add("%s scored %s%% in %s."
                        .formatted(name, plain(subject.percentage()), subject.subjectName())));
        if (snapshot.passed()) {
            positives.add("%s passed this exam overall.".formatted(name));
        }

        List<String> homeSupport = new ArrayList<>();
        homeSupport.add("Set a fixed 45-minute study slot at the same time each evening - consistency helps more than long sessions.");
        homeSupport.add("Ask %s to explain one thing they learned that day out loud; explaining it is what reveals whether it landed."
                .formatted(name));

        List<WeakSubject> weak = snapshot.weakSubjects();
        if (!weak.isEmpty()) {
            homeSupport.add("Focus the extra time on %s, the subject furthest behind at %s%%."
                    .formatted(weak.get(0).subjectName(), plain(weak.get(0).averagePercentage())));
        }
        if (!snapshot.weakTopics().isEmpty()) {
            WeakTopic topic = snapshot.weakTopics().get(0);
            homeSupport.add("Ask the class teacher for practice material on %s specifically, rather than the whole subject."
                    .formatted(topic.topicName()));
        }
        homeSupport.add("Keep phones out of the study space for that slot; no other single change makes as much difference.");
        homeSupport.add("Check in on effort and routine rather than on marks - marks follow the routine.");

        List<WeeklyGuidanceItem> weekly = List.of(
                new WeeklyGuidanceItem("Week 1",
                        weak.isEmpty() ? "Revise the weakest exam topics" : "Basics of " + weak.get(0).subjectName(),
                        "Sit with %s once this week while they work, without correcting - just present.".formatted(name)),
                new WeeklyGuidanceItem("Week 2",
                        "Practice questions on the same topic",
                        "Ask to see one completed practice sheet and acknowledge the effort."),
                new WeeklyGuidanceItem("Week 3",
                        "A timed practice paper",
                        "Help set up a quiet hour with no interruptions, and let them finish before discussing it."),
                new WeeklyGuidanceItem("Week 4",
                        "Review the mistakes from the practice paper",
                        "Talk through what improved since week 1 rather than what is still wrong."));

        String summary = "%s scored %s%% overall in %s, which is grade %s. That places %s %d out of %d in the class, where the class average was %s%%. %s"
                .formatted(name, plain(snapshot.overallPercentage()), snapshot.examName(),
                        snapshot.overallGrade(), name, snapshot.rankInClass(), snapshot.classSize(),
                        plain(snapshot.classAveragePercentage()),
                        snapshot.passed()
                                ? "This is a pass."
                                : "This is below the pass mark, so the next few weeks matter.");

        return new ParentFeedback(summary, homeSupport, weekly,
                positives.isEmpty() ? List.of("%s completed every paper in this exam.".formatted(name)) : positives,
                "Small, regular support at home moves this more than anything else. You do not need to teach the subject to help.");
    }

    private WeakStudentNote toWeakStudentNote(StrugglingStudent student) {
        String concern = student.weakSubjects().isEmpty()
                ? "Overall %s%%, ranked %d - below the pass mark."
                        .formatted(plain(student.overallPercentage()), student.rankInClass())
                : "Overall %s%%, ranked %d; weak in %s."
                        .formatted(plain(student.overallPercentage()), student.rankInClass(),
                                String.join(", ", student.weakSubjects()));

        String action = student.weakSubjects().isEmpty()
                ? "Review the full paper with the student and identify where marks were lost."
                : "Start remedial work on %s and re-test within two weeks."
                        .formatted(student.weakSubjects().get(0));

        String priority = student.overallPercentage().compareTo(new BigDecimal("40")) < 0
                ? "HIGH"
                : student.overallPercentage().compareTo(new BigDecimal("55")) < 0 ? "MEDIUM" : "LOW";

        return new WeakStudentNote(student.studentName(), concern, action, priority);
    }

    private List<StudyPlanItem> studyPlan(StudentSnapshot snapshot) {
        List<StudyPlanItem> plan = new ArrayList<>();
        int week = 1;

        for (WeakTopic topic : snapshot.weakTopics()) {
            if (plan.size() >= 4) {
                break;
            }
            plan.add(new StudyPlanItem(
                    topic.subjectCode(),
                    topic.topicName(),
                    "Re-work every question on %s from the textbook, then attempt a fresh practice set. Current average %s%%."
                            .formatted(topic.topicName(), plain(topic.averagePercentage())),
                    "Week " + week++,
                    4));
        }

        for (WeakSubject weak : snapshot.weakSubjects()) {
            if (plan.size() >= 6) {
                break;
            }
            plan.add(new StudyPlanItem(
                    weak.subjectCode(),
                    "General",
                    "Revise %s from the start of the term and take one timed past paper. Current average %s%%."
                            .formatted(weak.subjectName(), plain(weak.averagePercentage())),
                    "Week " + week++,
                    3));
        }

        for (SubjectTrend trend : snapshot.trend().bySubject()) {
            if (plan.size() >= 6) {
                break;
            }
            if (!"DECLINING".equals(trend.direction())) {
                continue;
            }
            plan.add(new StudyPlanItem(
                    trend.subjectCode(),
                    "General",
                    "%s has fallen %s points since the first exam. Go back to the last topic you scored well in and work forward from there."
                            .formatted(trend.subjectName(), plain(trend.change().abs())),
                    "Week " + week++,
                    2));
        }

        if (plan.isEmpty()) {
            plan.add(new StudyPlanItem(
                    snapshot.subjects().isEmpty() ? "General" : snapshot.subjects().get(0).subjectCode(),
                    "Consolidation",
                    "No weak area was flagged. Keep the current routine and take one timed past paper per subject.",
                    "Ongoing",
                    2));
        }
        return plan;
    }

    private String studentSummary(StudentSnapshot snapshot) {
        String trendNote = switch (snapshot.trend().overallDirection()) {
            case "IMPROVING" -> " Your overall percentage is up %s points since your first exam."
                    .formatted(plain(snapshot.trend().overallChange()));
            case "DECLINING" -> " Your overall percentage is down %s points since your first exam, which is the thing to address."
                    .formatted(plain(snapshot.trend().overallChange().abs()));
            case "STABLE" -> " Your overall percentage has held steady across your exams.";
            default -> "";
        };

        return "You scored %s%% in %s, grade %s, ranking %d of %d against a class average of %s%%.%s"
                .formatted(plain(snapshot.overallPercentage()), snapshot.examName(),
                        snapshot.overallGrade(), snapshot.rankInClass(), snapshot.classSize(),
                        plain(snapshot.classAveragePercentage()), trendNote);
    }

    private String aboveClassNote(SubjectPerformance subject) {
        if (subject.deltaVsClass() == null || subject.deltaVsClass().signum() <= 0) {
            return "";
        }
        return ", %s points above the class average".formatted(plain(subject.deltaVsClass()));
    }

    private static String firstName(String fullName) {
        if (fullName == null || fullName.isBlank()) {
            return "Your child";
        }
        int space = fullName.indexOf(' ');
        return space > 0 ? fullName.substring(0, space) : fullName;
    }

    private static String plain(BigDecimal value) {
        return value == null ? "n/a" : value.stripTrailingZeros().toPlainString();
    }
}
