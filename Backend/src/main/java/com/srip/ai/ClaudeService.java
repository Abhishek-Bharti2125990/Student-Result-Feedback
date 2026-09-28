package com.srip.ai;

import com.srip.domain.AiFeedback;
import com.srip.dto.ai.FeedbackDtos.StudentFeedback;
import com.srip.dto.ai.FeedbackDtos.TeacherFeedback;
import com.srip.dto.analytics.AnalyticsDtos.ClassAnalytics;
import com.srip.dto.analytics.AnalyticsDtos.StudentSnapshot;
import com.srip.dto.dashboard.DashboardDtos.ResourceSuggestion;
import com.srip.exception.ApiExceptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.function.Supplier;

/**
 * Produces a feedback document, from Claude when it can and from the local
 * writer when it cannot.
 *
 * <p>This is the only place that decides between the two. {@link ClaudeClient}
 * knows how to call the API and nothing about feedback;
 * {@code FeedbackService} knows how to store a document and nothing about how it
 * was produced. Keeping the choice here means the degraded path is exercised by
 * the same code every caller uses, rather than being an afterthought bolted on
 * at one call site.
 *
 * <p>Falling back rather than failing is deliberate. A result portal going dark
 * because an upstream API is briefly unavailable would be a worse failure than
 * slightly plainer prose, and every response says which of the two it got.
 */
@Service
public class ClaudeService {

    private static final Logger log = LoggerFactory.getLogger(ClaudeService.class);

    private static final String FALLBACK_MODEL = "rule-based";

    private final ClaudeClient claude;
    private final PromptFactory prompts;
    private final FallbackFeedbackWriter fallback;

    public ClaudeService(ClaudeClient claude, PromptFactory prompts, FallbackFeedbackWriter fallback) {
        this.claude = claude;
        this.prompts = prompts;
        this.fallback = fallback;
    }

    /**
     * @param resources the vetted books and videos for this student's weak
     *                  topics; passed through to the prompt so the model can only
     *                  recommend something the school actually holds
     */
    public Generated<StudentFeedback> studentFeedback(StudentSnapshot snapshot,
                                                      List<ResourceSuggestion> resources) {
        return generate(
                AiFeedback.Audience.STUDENT,
                StudentFeedback.class,
                prompts.studentSystemPrompt(),
                () -> prompts.studentUserPrompt(snapshot, resources),
                () -> fallback.studentFeedback(snapshot, resources));
    }

    public Generated<TeacherFeedback> teacherFeedback(ClassAnalytics analytics) {
        return generate(
                AiFeedback.Audience.TEACHER,
                TeacherFeedback.class,
                prompts.teacherSystemPrompt(),
                () -> prompts.teacherUserPrompt(analytics),
                () -> fallback.teacherFeedback(analytics));
    }

    /**
     * @param userPrompt     built lazily, so the prompt is not assembled at all
     *                       when the fallback path is taken
     * @param fallbackWriter the local generator for this audience
     */
    private <T> Generated<T> generate(AiFeedback.Audience audience,
                                      Class<T> type,
                                      String systemPrompt,
                                      Supplier<String> userPrompt,
                                      Supplier<T> fallbackWriter) {
        if (!claude.isAvailable()) {
            return fallbackResult(fallbackWriter.get());
        }

        try {
            ClaudeClient.Generated<T> generated = claude.generate(type, systemPrompt, userPrompt.get());
            log.info("Generated {} feedback via {} ({} in / {} out tokens)",
                    audience, generated.model(), generated.inputTokens(), generated.outputTokens());
            return new Generated<>(
                    generated.value(),
                    generated.model(),
                    AiFeedback.Source.CLAUDE,
                    generated.inputTokens(),
                    generated.outputTokens());
        } catch (ApiExceptions.AiGenerationException e) {
            log.warn("Claude call failed for {} feedback, using the local writer instead: {}",
                    audience, e.getMessage());
            return fallbackResult(fallbackWriter.get());
        }
    }

    private <T> Generated<T> fallbackResult(T payload) {
        return new Generated<>(payload, FALLBACK_MODEL, AiFeedback.Source.FALLBACK, null, null);
    }

    /**
     * A finished document and where it came from.
     *
     * @param source       CLAUDE or FALLBACK; surfaced all the way to the API so
     *                     nobody mistakes one for the other
     * @param inputTokens  billed input tokens, null on the fallback path
     * @param outputTokens billed output tokens, null on the fallback path
     */
    public record Generated<T>(
            T payload,
            String model,
            AiFeedback.Source source,
            Integer inputTokens,
            Integer outputTokens
    ) {
    }
}
