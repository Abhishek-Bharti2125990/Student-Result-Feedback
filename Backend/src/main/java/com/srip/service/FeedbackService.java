package com.srip.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.srip.ai.ClaudeService;
import com.srip.config.CacheConfig;
import com.srip.domain.AiFeedback;
import com.srip.dto.ai.FeedbackDtos.FeedbackEnvelope;
import com.srip.dto.ai.FeedbackDtos.StudentFeedback;
import com.srip.dto.ai.FeedbackDtos.TeacherFeedback;
import com.srip.dto.analytics.AnalyticsDtos.ClassAnalytics;
import com.srip.dto.analytics.AnalyticsDtos.StudentSnapshot;
import com.srip.dto.dashboard.DashboardDtos.ResourceSuggestion;
import com.srip.exception.ApiExceptions;
import com.srip.repository.AiFeedbackRepository;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Produces the two feedback documents and keeps them.
 *
 * <p>A model call costs money and is not reproducible, so feedback is generated
 * once and then read back: the student who opens the portal a week later must
 * see the same words the teacher saw. {@code refresh=true} is the explicit
 * override when a re-upload has changed the underlying marks, and it is what the
 * import job's final step uses.
 *
 * <p>This class is only about assembling the input and storing the output.
 * Choosing between Claude and the local writer belongs to
 * {@link ClaudeService}, so that decision is made in one place rather than at
 * every call site.
 */
@Service
public class FeedbackService {

    private final AnalyticsService analytics;
    private final ClassInsightService classInsights;
    private final StudyResourceService studyResources;
    private final ClaudeService claudeService;
    private final AiFeedbackRepository stored;
    private final ObjectMapper objectMapper;

    public FeedbackService(AnalyticsService analytics,
                           ClassInsightService classInsights,
                           StudyResourceService studyResources,
                           ClaudeService claudeService,
                           AiFeedbackRepository stored,
                           ObjectMapper objectMapper) {
        this.analytics = analytics;
        this.classInsights = classInsights;
        this.studyResources = studyResources;
        this.claudeService = claudeService;
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
        List<ResourceSuggestion> resources = studyResources.forWeakTopics(snapshot.weakTopics());

        ClaudeService.Generated<StudentFeedback> generated =
                claudeService.studentFeedback(snapshot, resources);

        return store(generated, AiFeedback.Audience.STUDENT, studentId, null, examId);
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
        ClaudeService.Generated<TeacherFeedback> generated =
                claudeService.teacherFeedback(classAnalytics);

        return store(generated, AiFeedback.Audience.TEACHER, null, className, examId);
    }

    /**
     * The stored teacher document, or empty when none has been generated.
     *
     * <p>The dashboard uses this rather than {@link #teacherFeedback} so that
     * drawing a page never triggers a billed model call as a side effect.
     */
    @Transactional(readOnly = true)
    public Optional<FeedbackEnvelope> storedTeacherFeedback(Long examId, String className) {
        return readStored(
                stored.findFirstByClassNameAndExamIdAndAudienceOrderByGeneratedAtDesc(
                        className, examId, AiFeedback.Audience.TEACHER),
                TeacherFeedback.class);
    }

    // -- Internals -----------------------------------------------------------

    private <T> FeedbackEnvelope store(ClaudeService.Generated<T> generated,
                                       AiFeedback.Audience audience,
                                       Long studentId,
                                       String className,
                                       Long examId) {
        AiFeedback record = new AiFeedback(
                studentId,
                className,
                examId,
                audience,
                toJson(generated.payload()),
                generated.model(),
                generated.source(),
                generated.inputTokens(),
                generated.outputTokens());

        AiFeedback saved = stored.save(record);

        return new FeedbackEnvelope(
                audience.name(),
                generated.model(),
                generated.source().name(),
                saved.getGeneratedAt(),
                false,
                generated.payload());
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
