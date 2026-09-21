package com.srip.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.srip.ai.ClaudeClient;
import com.srip.ai.FallbackFeedbackWriter;
import com.srip.ai.PromptFactory;
import com.srip.config.CacheConfig;
import com.srip.domain.AiFeedback;
import com.srip.dto.ai.FeedbackDtos.FeedbackEnvelope;
import com.srip.dto.ai.FeedbackDtos.ParentFeedback;
import com.srip.dto.ai.FeedbackDtos.StudentFeedback;
import com.srip.dto.ai.FeedbackDtos.TeacherFeedback;
import com.srip.dto.analytics.AnalyticsDtos.ClassAnalytics;
import com.srip.dto.analytics.AnalyticsDtos.StudentSnapshot;
import com.srip.exception.ApiExceptions;
import com.srip.repository.AiFeedbackRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * Produces the three feedback documents and keeps them.
 *
 * <p>A model call costs money and is not reproducible, so feedback is generated
 * once and then read back: the parent who opens the portal a week later must
 * see the same words the teacher saw. {@code refresh=true} is the explicit
 * override when a re-upload has changed the underlying marks.
 *
 * <p>If the Claude API is unavailable - no key, or the call fails - the
 * rule-based writer produces the document instead and the response says so via
 * {@code source}. A result portal going dark because an upstream service is
 * down would be a worse failure than slightly plainer prose.
 */
@Service
public class FeedbackService {

    private static final Logger log = LoggerFactory.getLogger(FeedbackService.class);

    private final AnalyticsService analytics;
    private final ClassInsightService classInsights;
    private final ClaudeClient claude;
    private final PromptFactory prompts;
    private final FallbackFeedbackWriter fallback;
    private final AiFeedbackRepository stored;
    private final ObjectMapper objectMapper;

    public FeedbackService(AnalyticsService analytics,
                           ClassInsightService classInsights,
                           ClaudeClient claude,
                           PromptFactory prompts,
                           FallbackFeedbackWriter fallback,
                           AiFeedbackRepository stored,
                           ObjectMapper objectMapper) {
        this.analytics = analytics;
        this.classInsights = classInsights;
        this.claude = claude;
        this.prompts = prompts;
        this.fallback = fallback;
        this.stored = stored;
        this.objectMapper = objectMapper;
    }

    // -- Student ------------------------------------------------------------

    @Caching(
            cacheable = @Cacheable(cacheNames = CacheConfig.CACHE_FEEDBACK,
                    key = "'student:' + #studentId + ':' + #examId", condition = "!#refresh"),
            put = @CachePut(cacheNames = CacheConfig.CACHE_FEEDBACK,
                    key = "'student:' + #studentId + ':' + #examId", condition = "#refresh"))
    @Transactional
    public FeedbackEnvelope studentFeedback(Long studentId, Long examId, boolean refresh) {
        if (!refresh) {
            Optional<FeedbackEnvelope> existing = readStored(
                    stored.findFirstByStudentIdAndExamIdAndAudienceOrderByGeneratedAtDesc(
                            studentId, examId, AiFeedback.Audience.STUDENT),
                    StudentFeedback.class);
            if (existing.isPresent()) {
                return existing.get();
            }
        }

        StudentSnapshot snapshot = analytics.snapshot(studentId, examId);
        return generate(
                AiFeedback.Audience.STUDENT,
                studentId,
                null,
                examId,
                StudentFeedback.class,
                prompts.studentSystemPrompt(),
                () -> prompts.studentUserPrompt(snapshot),
                () -> fallback.studentFeedback(snapshot));
    }

    // -- Parent -------------------------------------------------------------

    @Caching(
            cacheable = @Cacheable(cacheNames = CacheConfig.CACHE_FEEDBACK,
                    key = "'parent:' + #studentId + ':' + #examId", condition = "!#refresh"),
            put = @CachePut(cacheNames = CacheConfig.CACHE_FEEDBACK,
                    key = "'parent:' + #studentId + ':' + #examId", condition = "#refresh"))
    @Transactional
    public FeedbackEnvelope parentFeedback(Long studentId, Long examId, boolean refresh) {
        if (!refresh) {
            Optional<FeedbackEnvelope> existing = readStored(
                    stored.findFirstByStudentIdAndExamIdAndAudienceOrderByGeneratedAtDesc(
                            studentId, examId, AiFeedback.Audience.PARENT),
                    ParentFeedback.class);
            if (existing.isPresent()) {
                return existing.get();
            }
        }

        StudentSnapshot snapshot = analytics.snapshot(studentId, examId);
        return generate(
                AiFeedback.Audience.PARENT,
                studentId,
                null,
                examId,
                ParentFeedback.class,
                prompts.parentSystemPrompt(),
                () -> prompts.studentUserPrompt(snapshot),
                () -> fallback.parentFeedback(snapshot));
    }

    // -- Teacher ------------------------------------------------------------

    @Caching(
            cacheable = @Cacheable(cacheNames = CacheConfig.CACHE_FEEDBACK,
                    key = "'teacher:' + #className + ':' + #examId", condition = "!#refresh"),
            put = @CachePut(cacheNames = CacheConfig.CACHE_FEEDBACK,
                    key = "'teacher:' + #className + ':' + #examId", condition = "#refresh"))
    @Transactional
    public FeedbackEnvelope teacherFeedback(Long examId, String className, boolean refresh) {
        if (!refresh) {
            Optional<FeedbackEnvelope> existing = readStored(
                    stored.findFirstByClassNameAndExamIdAndAudienceOrderByGeneratedAtDesc(
                            className, examId, AiFeedback.Audience.TEACHER),
                    TeacherFeedback.class);
            if (existing.isPresent()) {
                return existing.get();
            }
        }

        ClassAnalytics classAnalytics = classInsights.classAnalytics(examId, className);
        return generate(
                AiFeedback.Audience.TEACHER,
                null,
                className,
                examId,
                TeacherFeedback.class,
                prompts.teacherSystemPrompt(),
                () -> prompts.teacherUserPrompt(classAnalytics),
                () -> fallback.teacherFeedback(classAnalytics));
    }

    // -- Internals -----------------------------------------------------------

    /**
     * Calls Claude, or the local writer if Claude is unavailable, then persists
     * the result.
     *
     * @param userPrompt      built lazily so the prompt is not assembled when
     *                        the fallback path is taken
     * @param fallbackWriter  the local generator for this audience
     */
    private <T> FeedbackEnvelope generate(AiFeedback.Audience audience,
                                          Long studentId,
                                          String className,
                                          Long examId,
                                          Class<T> type,
                                          String systemPrompt,
                                          Supplier<String> userPrompt,
                                          Supplier<T> fallbackWriter) {

        T payload;
        String model;
        AiFeedback.Source source;
        Integer inputTokens = null;
        Integer outputTokens = null;

        if (claude.isAvailable()) {
            try {
                ClaudeClient.Generated<T> generated =
                        claude.generate(type, systemPrompt, userPrompt.get());
                payload = generated.value();
                model = generated.model();
                source = AiFeedback.Source.CLAUDE;
                inputTokens = generated.inputTokens();
                outputTokens = generated.outputTokens();
                log.info("Generated {} feedback via {} ({} in / {} out tokens)",
                        audience, model, inputTokens, outputTokens);
            } catch (ApiExceptions.AiGenerationException e) {
                log.warn("Claude call failed for {} feedback, using the local writer instead: {}",
                        audience, e.getMessage());
                payload = fallbackWriter.get();
                model = "rule-based";
                source = AiFeedback.Source.FALLBACK;
            }
        } else {
            payload = fallbackWriter.get();
            model = "rule-based";
            source = AiFeedback.Source.FALLBACK;
        }

        AiFeedback record = new AiFeedback(
                studentId, className, examId, audience, toJson(payload),
                model, source, inputTokens, outputTokens);
        AiFeedback saved = stored.save(record);

        return new FeedbackEnvelope(
                audience.name(), model, source.name(), saved.getGeneratedAt(), false, payload);
    }

    private <T> Optional<FeedbackEnvelope> readStored(Optional<AiFeedback> record, Class<T> type) {
        return record.map(entity -> new FeedbackEnvelope(
                entity.getAudience().name(),
                entity.getModel(),
                entity.getSource().name(),
                entity.getGeneratedAt(),
                true,
                fromJson(entity.getPayloadJson(), type)));
    }

    private String toJson(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialise generated feedback", e);
        }
    }

    private <T> T fromJson(String json, Class<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException e) {
            // A stored document that no longer matches its record shape means
            // the schema changed under it; regenerating is the only fix.
            throw new ApiExceptions.AiGenerationException(
                    "Stored feedback could not be read back; regenerate it with ?refresh=true", e);
        }
    }
}
