package com.srip.dto.ai;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.time.Instant;
import java.util.List;

/**
 * The two feedback documents: one for the student, one for the teacher.
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

            @JsonPropertyDescription("One concrete action the student should take, e.g. 'Revise the NCERT chapter on Quadratic Equations and solve 20 practice questions daily'")
            String action,

            @JsonPropertyDescription("When to do it, e.g. 'Week 1' or 'Daily for 2 weeks'")
            String timeframe,

            @JsonPropertyDescription("Minutes of study per day for this item, e.g. 30")
            int dailyMinutes,

            @JsonPropertyDescription("Title of a book or video from the supplied study resources, or null if none was supplied for this topic. Never invent one.")
            String resource
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

            @JsonPropertyDescription("The specific concern, including the subject or topic and the percentage")
            String concern,

            @JsonPropertyDescription("One concrete action the teacher should take for this student, e.g. 'Conduct additional Mathematics practice sessions'")
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

            @JsonPropertyDescription("Three to five classroom-level intervention suggestions, e.g. re-teaching a topic most of the class lost marks on")
            List<String> interventionSuggestions,

            @JsonPropertyDescription("Three to five remedial recommendations: remedial classes, worksheets, revision sessions, peer pairing")
            List<String> remedialRecommendations,

            @JsonPropertyDescription("Topics where the whole class underperformed and which should be re-taught")
            List<String> topicsToReteach
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
