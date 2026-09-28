package com.srip.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.srip.dto.analytics.AnalyticsDtos.StrongSubject;
import com.srip.dto.analytics.AnalyticsDtos.StrongTopic;
import com.srip.dto.analytics.AnalyticsDtos.WeakSubject;
import com.srip.dto.analytics.AnalyticsDtos.WeakTopic;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Encodes the strength and weakness lists stored on the {@code analytics} row.
 *
 * <p>They are JSON columns rather than child tables because they are only ever
 * read back whole, as part of one student's card. Four child tables would add
 * four joins to every dashboard query and buy nothing.
 *
 * <p>Decoding is lenient: an unreadable column yields an empty list rather than
 * an error. These lists are a cached convenience, and the next upload rewrites
 * them; failing a whole dashboard because one stored list no longer matches its
 * record shape would be the wrong trade.
 */
@Component
public class AnalyticsJson {

    private static final Logger log = LoggerFactory.getLogger(AnalyticsJson.class);

    private static final TypeReference<List<StrongSubject>> STRONG_SUBJECTS = new TypeReference<>() {
    };
    private static final TypeReference<List<WeakSubject>> WEAK_SUBJECTS = new TypeReference<>() {
    };
    private static final TypeReference<List<StrongTopic>> STRONG_TOPICS = new TypeReference<>() {
    };
    private static final TypeReference<List<WeakTopic>> WEAK_TOPICS = new TypeReference<>() {
    };

    private final ObjectMapper objectMapper;

    public AnalyticsJson(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String encode(List<?> values) {
        try {
            return objectMapper.writeValueAsString(values == null ? List.of() : values);
        } catch (Exception e) {
            throw new IllegalStateException("Could not serialise an analytics list", e);
        }
    }

    public List<StrongSubject> strongSubjects(String json) {
        return decode(json, STRONG_SUBJECTS);
    }

    public List<WeakSubject> weakSubjects(String json) {
        return decode(json, WEAK_SUBJECTS);
    }

    public List<StrongTopic> strongTopics(String json) {
        return decode(json, STRONG_TOPICS);
    }

    public List<WeakTopic> weakTopics(String json) {
        return decode(json, WEAK_TOPICS);
    }

    private <T> List<T> decode(String json, TypeReference<List<T>> type) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, type);
        } catch (Exception e) {
            log.warn("Ignoring an unreadable stored analytics list; re-upload to rebuild it: {}",
                    e.getMessage());
            return List.of();
        }
    }
}
