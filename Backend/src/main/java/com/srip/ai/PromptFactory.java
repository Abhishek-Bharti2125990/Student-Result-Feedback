package com.srip.ai;

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
import java.util.List;

/**
 * Builds the prompts.
 *
 * <p>The system prompts are constants. That is deliberate: they form the cached
 * prefix of every request, and a single varying character - a timestamp, a
 * student name - would invalidate the cache for the whole class. All
 * per-student data therefore goes in the user message.
 *
 * <p>The user message contains only numbers the analytics engine computed. The
 * model is asked to interpret and advise, never to calculate: arithmetic is the
 * one thing this system already knows exactly, and letting a model redo it
 * would introduce errors for no benefit.
 */
@Component
public class PromptFactory {

    private static final String COMMON_RULES = """
            Ground every statement in the supplied numbers. Never invent a mark, \
            a subject, a topic or a name that is not in the data. If the data is \
            thin, say so plainly rather than padding.

            Be specific. "Work harder at maths" is useless; "re-solve the \
            quadratic-equation set, where 12 of 25 marks were lost" is useful.

            Do not moralise, shame, or compare the student to named peers. Rank \
            and class average may be mentioned as context, never as a verdict on \
            the person.""";

    static final String STUDENT_SYSTEM_PROMPT = """
            You are an experienced school tutor writing directly to a secondary \
            school student about their own exam results.

            %s

            Address the student as "you". Keep the language plain and warm \
            without being patronising. The study plan must be something a \
            teenager can actually follow alongside normal school work: concrete \
            tasks, realistic weekly hours, hardest-hitting weakness first.
            """.formatted(COMMON_RULES);

    static final String TEACHER_SYSTEM_PROMPT = """
            You are a head of department advising a class teacher after an exam.

            %s

            Write for a professional. Be direct and economical. Prioritise: name \
            the students who need attention first and say what to do about each \
            one. Distinguish an individual problem from a class-wide one - if \
            most of the class lost marks on the same topic, that is a teaching \
            issue, not forty separate student issues, and it should appear under \
            topics to re-teach.
            """.formatted(COMMON_RULES);

    static final String PARENT_SYSTEM_PROMPT = """
            You are a school counsellor writing to the parent or guardian of a \
            secondary school student after an exam.

            %s

            Assume no educational jargon and no familiarity with grading \
            systems; explain anything technical in one clause. Assume the parent \
            works and has limited evening time, so every recommendation must be \
            achievable in that reality. Open with what is going well. Never \
            suggest punishment, withdrawal of privileges, or private tuition as \
            a default answer.
            """.formatted(COMMON_RULES);

    public String studentSystemPrompt() {
        return STUDENT_SYSTEM_PROMPT;
    }

    public String teacherSystemPrompt() {
        return TEACHER_SYSTEM_PROMPT;
    }

    public String parentSystemPrompt() {
        return PARENT_SYSTEM_PROMPT;
    }

    /** The per-student payload shared by the student and parent prompts. */
    public String studentUserPrompt(StudentSnapshot snapshot) {
        StringBuilder out = new StringBuilder(1024);

        out.append("STUDENT\n")
                .append("Name: ").append(snapshot.studentName()).append('\n')
                .append("Class: ").append(snapshot.className());
        if (snapshot.section() != null) {
            out.append(" section ").append(snapshot.section());
        }
        out.append('\n')
                .append("Exam: ").append(snapshot.examName())
                .append(" (").append(snapshot.examCode()).append(")\n\n");

        out.append("OVERALL\n")
                .append("Percentage: ").append(plain(snapshot.overallPercentage())).append("%\n")
                .append("Grade: ").append(snapshot.overallGrade()).append('\n')
                .append("Result: ").append(snapshot.passed() ? "pass" : "below pass mark").append('\n')
                .append("Rank: ").append(snapshot.rankInClass())
                .append(" of ").append(snapshot.classSize()).append('\n')
                .append("Class average: ").append(plain(snapshot.classAveragePercentage())).append("%\n\n");

        out.append("SUBJECT RESULTS (this exam)\n");
        for (SubjectPerformance subject : snapshot.subjects()) {
            out.append("- ").append(subject.subjectName())
                    .append(": ").append(plain(subject.marksObtained()))
                    .append('/').append(plain(subject.maxMarks()))
                    .append(" = ").append(plain(subject.percentage())).append('%')
                    .append(" grade ").append(subject.grade());
            if (subject.classAverage() != null) {
                out.append(" (class average ").append(plain(subject.classAverage())).append('%');
                if (subject.deltaVsClass() != null) {
                    out.append(", ").append(signed(subject.deltaVsClass())).append(" vs class");
                }
                out.append(')');
            }
            out.append('\n');
        }

        appendWeakSubjects(out, snapshot.weakSubjects());
        appendWeakTopics(out, snapshot.weakTopics());
        appendTrends(out, snapshot);

        out.append("\nWrite the feedback document now, following the required schema.");
        return out.toString();
    }

    /** The per-class payload for the teacher prompt. */
    public String teacherUserPrompt(ClassAnalytics analytics) {
        StringBuilder out = new StringBuilder(1024);

        out.append("CLASS\n")
                .append("Class: ").append(analytics.className()).append('\n')
                .append("Exam: ").append(analytics.examName())
                .append(" (").append(analytics.examCode()).append(")\n")
                .append("Students: ").append(analytics.classSize()).append('\n')
                .append("Class average: ").append(plain(analytics.classAveragePercentage())).append("%\n")
                .append("Highest: ").append(plain(analytics.highestPercentage())).append("%\n")
                .append("Lowest: ").append(plain(analytics.lowestPercentage())).append("%\n\n");

        out.append("SUBJECT BREAKDOWN (weakest first)\n");
        for (SubjectStat stat : analytics.subjectStats()) {
            out.append("- ").append(stat.subjectName())
                    .append(": average ").append(plain(stat.average())).append('%')
                    .append(", range ").append(plain(stat.lowest())).append('-')
                    .append(plain(stat.highest())).append('%')
                    .append(", passed ").append(stat.passCount())
                    .append(", failed ").append(stat.failCount())
                    .append('\n');
        }

        out.append("\nSTUDENTS NEEDING ATTENTION (lowest first)\n");
        if (analytics.strugglingStudents().isEmpty()) {
            out.append("- none flagged\n");
        } else {
            for (StrugglingStudent student : analytics.strugglingStudents()) {
                out.append("- ").append(student.studentName())
                        .append(": ").append(plain(student.overallPercentage())).append('%')
                        .append(", rank ").append(student.rankInClass())
                        .append(" of ").append(analytics.classSize());
                if (!student.weakSubjects().isEmpty()) {
                    out.append(", weak in ").append(String.join(", ", student.weakSubjects()));
                }
                out.append('\n');
            }
        }

        out.append("""

                Write the teacher feedback document now, following the required \
                schema. Use the exact student names given above.""");
        return out.toString();
    }

    private void appendWeakSubjects(StringBuilder out, List<WeakSubject> weakSubjects) {
        out.append("\nWEAK SUBJECTS DETECTED\n");
        if (weakSubjects.isEmpty()) {
            out.append("- none\n");
            return;
        }
        for (WeakSubject weak : weakSubjects) {
            out.append("- ").append(weak.subjectName())
                    .append(" at ").append(plain(weak.averagePercentage())).append("% - ")
                    .append(weak.reason()).append('\n');
        }
    }

    private void appendWeakTopics(StringBuilder out, List<WeakTopic> weakTopics) {
        out.append("\nWEAK TOPICS DETECTED (across all exams sat)\n");
        if (weakTopics.isEmpty()) {
            out.append("- none, or no topic-level marks were recorded\n");
            return;
        }
        for (WeakTopic weak : weakTopics) {
            out.append("- ").append(weak.subjectCode()).append(" / ").append(weak.topicName())
                    .append(": average ").append(plain(weak.averagePercentage())).append('%');
            if (weak.occurrences() > 1) {
                out.append(" - weak in ").append(weak.occurrences()).append(" separate exams");
            }
            out.append('\n');
        }
    }

    private void appendTrends(StringBuilder out, StudentSnapshot snapshot) {
        out.append("\nTREND\n")
                .append("Overall direction: ").append(snapshot.trend().overallDirection())
                .append(" (").append(signed(snapshot.trend().overallChange()))
                .append(" points from first to latest exam)\n");

        if (snapshot.trend().overall().size() > 1) {
            out.append("Overall percentage by exam: ");
            out.append(String.join(", ", snapshot.trend().overall().stream()
                    .map(point -> point.examCode() + " " + plain(point.percentage()) + "%")
                    .toList()));
            out.append('\n');
        }

        out.append("Per-subject direction (steepest decline first):\n");
        for (SubjectTrend trend : snapshot.trend().bySubject()) {
            out.append("- ").append(trend.subjectName())
                    .append(": ").append(trend.direction())
                    .append(" (").append(signed(trend.change())).append(" points, ")
                    .append(trend.points().size()).append(" exam(s))\n");
        }
    }

    private static String plain(BigDecimal value) {
        return value == null ? "n/a" : value.stripTrailingZeros().toPlainString();
    }

    private static String signed(BigDecimal value) {
        if (value == null) {
            return "n/a";
        }
        String rendered = plain(value);
        return value.signum() > 0 ? "+" + rendered : rendered;
    }
}
