package com.srip.ai;

import com.srip.dto.ai.FeedbackDtos.StudentFeedback;
import com.srip.dto.ai.FeedbackDtos.StudyPlanItem;
import com.srip.dto.ai.FeedbackDtos.TeacherFeedback;
import com.srip.dto.ai.FeedbackDtos.WeakStudentNote;
import com.srip.dto.analytics.AnalyticsDtos.ClassAnalytics;
import com.srip.dto.analytics.AnalyticsDtos.ClassTopicWeakness;
import com.srip.dto.analytics.AnalyticsDtos.StrongSubject;
import com.srip.dto.analytics.AnalyticsDtos.StrongTopic;
import com.srip.dto.analytics.AnalyticsDtos.StrugglingStudent;
import com.srip.dto.analytics.AnalyticsDtos.StudentSnapshot;
import com.srip.dto.analytics.AnalyticsDtos.SubjectPerformance;
import com.srip.dto.analytics.AnalyticsDtos.SubjectStat;
import com.srip.dto.analytics.AnalyticsDtos.SubjectTrend;
import com.srip.dto.analytics.AnalyticsDtos.WeakSubject;
import com.srip.dto.analytics.AnalyticsDtos.WeakTopic;
import com.srip.dto.dashboard.DashboardDtos.ResourceSuggestion;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Writes the same two documents from templates, with no model call.
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
    private static final int MAX_PLAN_ITEMS = 6;

    public StudentFeedback studentFeedback(StudentSnapshot snapshot, List<ResourceSuggestion> resources) {
        return new StudentFeedback(
                studentSummary(snapshot),
                strengths(snapshot),
                weaknesses(snapshot),
                studyPlan(snapshot, resources),
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

        return new TeacherFeedback(
                classSummary(analytics),
                weakStudents,
                interventions(analytics, weakestSubjects),
                remedialActions(analytics, weakStudents, weakestSubjects),
                topicsToReteach(analytics, weakestSubjects));
    }

    // -- Student -------------------------------------------------------------

    private List<String> strengths(StudentSnapshot snapshot) {
        List<String> strengths = new ArrayList<>();

        for (StrongSubject strong : snapshot.strongSubjects()) {
            if (strengths.size() >= 3) {
                break;
            }
            strengths.add("%s at %s%% - one of your stronger subjects."
                    .formatted(strong.subjectName(), plain(strong.averagePercentage())));
        }
        for (StrongTopic strong : snapshot.strongTopics()) {
            if (strengths.size() >= MAX_LIST_ITEMS) {
                break;
            }
            strengths.add("%s at %s%% - you have this topic secure."
                    .formatted(strong.topicName(), plain(strong.averagePercentage())));
        }

        if (!strengths.isEmpty()) {
            return strengths;
        }

        // Nothing cleared the strength threshold, so name the best of what there
        // is rather than telling the student they have no strengths at all.
        return snapshot.subjects().stream()
                .max(Comparator.comparing(SubjectPerformance::percentage))
                .map(best -> List.of(
                        "Your strongest subject this exam was %s at %s%% - that is the base to build on."
                                .formatted(best.subjectName(), plain(best.percentage()))))
                .orElse(List.of("No subject marks were recorded for this exam."));
    }

    private List<String> weaknesses(StudentSnapshot snapshot) {
        List<String> weaknesses = new ArrayList<>();

        for (WeakTopic weak : snapshot.weakTopics()) {
            if (weaknesses.size() >= 3) {
                break;
            }
            weaknesses.add("%s at %s%% - this is where the marks went."
                    .formatted(weak.topicName(), plain(weak.averagePercentage())));
        }
        for (WeakSubject weak : snapshot.weakSubjects()) {
            if (weaknesses.size() >= MAX_LIST_ITEMS) {
                break;
            }
            weaknesses.add("%s at %s%% - %s".formatted(
                    weak.subjectName(), plain(weak.averagePercentage()), weak.reason()));
        }

        return weaknesses.isEmpty()
                ? List.of("No subject or topic fell below the weakness thresholds in this exam.")
                : weaknesses;
    }

    private List<StudyPlanItem> studyPlan(StudentSnapshot snapshot, List<ResourceSuggestion> resources) {
        List<StudyPlanItem> plan = new ArrayList<>();
        int week = 1;

        for (WeakTopic topic : snapshot.weakTopics()) {
            if (plan.size() >= 4) {
                break;
            }
            plan.add(new StudyPlanItem(
                    topic.subjectCode(),
                    topic.topicName(),
                    "Revise the chapter on %s, then solve 20 practice questions a day. Current average %s%%."
                            .formatted(topic.topicName(), plain(topic.averagePercentage())),
                    "Week " + week++,
                    30,
                    resourceFor(resources, topic.topicName())));
        }

        for (WeakSubject weak : snapshot.weakSubjects()) {
            if (plan.size() >= MAX_PLAN_ITEMS) {
                break;
            }
            plan.add(new StudyPlanItem(
                    weak.subjectCode(),
                    "General",
                    "Revise %s from the start of the term and take one timed past paper. Current average %s%%."
                            .formatted(weak.subjectName(), plain(weak.averagePercentage())),
                    "Week " + week++,
                    30,
                    null));
        }

        for (SubjectTrend trend : snapshot.trend().bySubject()) {
            if (plan.size() >= MAX_PLAN_ITEMS) {
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
                    20,
                    null));
        }

        if (plan.isEmpty()) {
            plan.add(new StudyPlanItem(
                    snapshot.subjects().isEmpty() ? "General" : snapshot.subjects().get(0).subjectCode(),
                    "Consolidation",
                    "No weak area was flagged. Keep the current routine and take one timed past paper per subject.",
                    "Ongoing",
                    20,
                    null));
        }
        return plan;
    }

    /** @return the title of a vetted resource for this topic, or null if none */
    private String resourceFor(List<ResourceSuggestion> resources, String topicName) {
        if (resources == null || topicName == null) {
            return null;
        }
        return resources.stream()
                .filter(resource -> topicName.equalsIgnoreCase(resource.topic()))
                .map(ResourceSuggestion::title)
                .findFirst()
                .orElse(null);
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

    // -- Teacher -------------------------------------------------------------

    private String classSummary(ClassAnalytics analytics) {
        return "%s in class %s averaged %s%% across %d students, ranging from %s%% to %s%%. %d student(s) need attention."
                .formatted(analytics.examName(), analytics.className(),
                        plain(analytics.classAveragePercentage()), analytics.classSize(),
                        plain(analytics.lowestPercentage()), plain(analytics.highestPercentage()),
                        analytics.strugglingStudents().size());
    }

    private List<String> interventions(ClassAnalytics analytics, List<SubjectStat> weakestSubjects) {
        List<String> interventions = new ArrayList<>();

        for (ClassTopicWeakness topic : analytics.weakestTopics()) {
            if (interventions.size() >= 3) {
                break;
            }
            interventions.add("%d of %d students are weak on %s (%s) - schedule a remedial class on it before moving on."
                    .formatted(topic.weakStudents(), analytics.classSize(),
                            topic.topicName(), topic.subjectName()));
        }

        for (SubjectStat stat : weakestSubjects) {
            if (interventions.size() >= MAX_LIST_ITEMS) {
                break;
            }
            interventions.add("%s averaged %s%% with %d of %d below the pass mark - re-teach the core unit."
                    .formatted(stat.subjectName(), plain(stat.average()),
                            stat.failCount(), stat.passCount() + stat.failCount()));
        }

        if (analytics.classAveragePercentage().compareTo(new BigDecimal("50")) < 0) {
            interventions.add("The class average of %s%% suggests a pacing problem rather than individual gaps; consider reviewing the term plan."
                    .formatted(plain(analytics.classAveragePercentage())));
        }

        return interventions.isEmpty()
                ? List.of("No class-wide intervention is indicated; the spread is within the expected range.")
                : interventions;
    }

    private List<String> remedialActions(ClassAnalytics analytics,
                                         List<WeakStudentNote> weakStudents,
                                         List<SubjectStat> weakestSubjects) {
        List<String> remedial = new ArrayList<>();

        if (!weakStudents.isEmpty()) {
            remedial.add("Conduct a weekly 40-minute remedial class for the %d flagged students."
                    .formatted(weakStudents.size()));
            remedial.add("Share a worksheet on the weakest topic and mark it within a week so the gap is visible early.");
            remedial.add("Pair each flagged student with a peer who scored above 75%% in that subject.");
        }
        if (!analytics.weakestTopics().isEmpty()) {
            remedial.add("Schedule a revision session on %s, the topic the most students lost marks on."
                    .formatted(analytics.weakestTopics().get(0).topicName()));
        }
        if (!weakestSubjects.isEmpty()) {
            remedial.add("Issue a targeted practice set for %s."
                    .formatted(weakestSubjects.get(0).subjectName()));
        }

        return remedial.isEmpty()
                ? List.of("Maintain current practice; no remedial action is indicated by this exam.")
                : remedial;
    }

    private List<String> topicsToReteach(ClassAnalytics analytics, List<SubjectStat> weakestSubjects) {
        List<String> reteach = new ArrayList<>();

        for (ClassTopicWeakness topic : analytics.weakestTopics()) {
            if (reteach.size() >= MAX_LIST_ITEMS) {
                break;
            }
            reteach.add("%s (%s) - %d student(s) weak, class average %s%%"
                    .formatted(topic.topicName(), topic.subjectName(),
                            topic.weakStudents(), plain(topic.classAveragePercentage())));
        }

        if (reteach.isEmpty()) {
            weakestSubjects.forEach(stat -> reteach.add("%s - class average %s%%"
                    .formatted(stat.subjectName(), plain(stat.average()))));
        }

        return reteach.isEmpty() ? List.of("No topic requires class-wide re-teaching.") : reteach;
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
                : "Conduct additional %s practice sessions and re-test within two weeks."
                        .formatted(student.weakSubjects().get(0));

        String priority = student.overallPercentage().compareTo(new BigDecimal("40")) < 0
                ? "HIGH"
                : student.overallPercentage().compareTo(new BigDecimal("55")) < 0 ? "MEDIUM" : "LOW";

        return new WeakStudentNote(student.studentName(), concern, action, priority);
    }

    private static String plain(BigDecimal value) {
        return value == null ? "n/a" : value.stripTrailingZeros().toPlainString();
    }
}
