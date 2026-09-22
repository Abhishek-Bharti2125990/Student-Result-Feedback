package com.srip.dto.ai;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.time.Instant;
import java.util.List;

/**
 * The three feedback documents.
 *
 * <p>These records are also the JSON schema handed to Claude as a structured
 * output format, which is why every field carries a description: the schema is
 * the instruction. Constraining the response shape server-side means the API
 * cannot return prose that fails to parse, so no brittle text scraping is
 * needed on the way back.
 */
public final class FeedbackDtos {

    private FeedbackDtos() {
    }

    // -- Student ------------------------------------------------------------

    public record StudyPlanItem(
            @JsonPropertyDescription("Subject this task belongs to")
            String subject,

            @JsonPropertyDescription("Specific topic to work on, or 'General' if the whole subject")
            String topic,

            @JsonPropertyDescription("One concrete action the student should take, e.g. 'Re-solve the 10 quadratic equations from chapter 4'")
            String action,

            @JsonPropertyDescription("When to do it, e.g. 'Week 1' or 'Daily for 2 weeks'")
            String timeframe,

            @JsonPropertyDescription("Suggested study hours per week for this item")
            int weeklyHours
    ) {
    }

    public record StudentFeedback(
            @JsonPropertyDescription("Two or three sentences summarising this exam for the student, addressed to them directly as 'you'")
            String summary,

            @JsonPropertyDescription("Three to five specific strengths, each tied to a subject or topic the data supports")
            List<String> strengths,

            @JsonPropertyDescription("Three to five specific weaknesses, phrased as fixable gaps rather than judgements")
            List<String> weaknesses,

            @JsonPropertyDescription("Four to six ordered study plan items, hardest-hitting weakness first")
            List<StudyPlanItem> studyPlan,

            @JsonPropertyDescription("One short encouraging closing line, honest rather than inflated")
            String motivationalNote
    ) {
    }

    // -- Teacher ------------------------------------------------------------

    public record WeakStudentNote(
            @JsonPropertyDescription("Student name exactly as given in the input data")
            String studentName,

            @JsonPropertyDescription("The specific concern, including the subject and the percentage")
            String concern,

            @JsonPropertyDescription("One concrete action the teacher should take for this student")
            String suggestedAction,

            @JsonPropertyDescription("Priority: HIGH, MEDIUM or LOW")
            String priority
    ) {
    }

    public record TeacherFeedback(
            @JsonPropertyDescription("Two or three sentences on how the class performed overall")
            String classSummary,

            @JsonPropertyDescription("The students needing attention, most urgent first")
            List<WeakStudentNote> weakStudents,

            @JsonPropertyDescription("Three to five classroom-level intervention suggestions, e.g. re-teaching a topic the whole class failed")
            List<String> interventionSuggestions,

            @JsonPropertyDescription("Three to five remedial recommendations: extra sessions, practice sets, peer pairing, parent contact")
            List<String> remedialRecommendations,

            @JsonPropertyDescription("Topics where the whole class underperformed and which should be re-taught")
            List<String> topicsToReteach
    ) {
    }

    // -- Parent -------------------------------------------------------------

    public record WeeklyGuidanceItem(
            @JsonPropertyDescription("Which week this applies to, e.g. 'Week 1'")
            String week,

            @JsonPropertyDescription("What the child should focus on that week")
            String focus,

            @JsonPropertyDescription("What the parent should specifically do that week")
            String parentAction
    ) {
    }

    public record ParentFeedback(
            @JsonPropertyDescription("Two or three sentences for the parent, in plain non-technical language, no jargon and no grade shaming")
            String summary,

            @JsonPropertyDescription("Four to six practical things the parent can do at home, each doable by a working parent")
            List<String> homeSupportRecommendations,

            @JsonPropertyDescription("A four-week guidance schedule")
            List<WeeklyGuidanceItem> weeklyGuidance,

            @JsonPropertyDescription("What is going well, so the conversation at home does not start with problems")
            List<String> positivesToAcknowledge,

            @JsonPropertyDescription("One short closing line of encouragement for the parent")
            String encouragement
    ) {
    }

    // -- Envelope -----------------------------------------------------------

    /**
     * Wraps a feedback document with its provenance.
     *
     * @param source CLAUDE when the model produced it, FALLBACK when the local
     *               rule-based writer did; shown so no one mistakes one for the other
     */
    public record FeedbackEnvelope(
            String audience,
            String model,
            String source,
            Instant generatedAt,
            boolean cached,
            Object feedback
    ) {
    }
}
