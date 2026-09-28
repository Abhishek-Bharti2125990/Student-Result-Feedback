package com.srip.service;

import com.srip.domain.StudyResource;
import com.srip.dto.analytics.AnalyticsDtos.WeakTopic;
import com.srip.dto.dashboard.DashboardDtos.ResourceSuggestion;
import com.srip.repository.StudyResourceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Turns a list of weak topics into the books and videos to work from.
 *
 * <p>Lookup is by topic name, case-insensitively, because the library is curated
 * by hand while the topic names arrive from a spreadsheet. A topic with no
 * curated resource simply contributes nothing - an empty list is a truthful
 * answer, and better than a generic "search the internet" suggestion.
 *
 * <p>Results keep the weak-topic ordering, worst topic first, so the first
 * resource a student sees is for the topic that cost them the most marks.
 */
@Service
public class StudyResourceService {

    private final StudyResourceRepository resources;

    public StudyResourceService(StudyResourceRepository resources) {
        this.resources = resources;
    }

    /**
     * @param weakTopics the student's weak topics, worst first
     * @return one entry per curated book or video, ordered by topic severity
     */
    @Transactional(readOnly = true)
    public List<ResourceSuggestion> forWeakTopics(List<WeakTopic> weakTopics) {
        if (weakTopics == null || weakTopics.isEmpty()) {
            return List.of();
        }

        Set<String> lowercaseNames = new LinkedHashSet<>();
        for (WeakTopic topic : weakTopics) {
            if (topic.topicName() != null) {
                lowercaseNames.add(topic.topicName().toLowerCase(Locale.ROOT));
            }
        }
        if (lowercaseNames.isEmpty()) {
            return List.of();
        }

        List<StudyResource> found = resources.findByTopicNames(lowercaseNames);

        // Re-order to match the weak-topic ranking rather than the database's
        // ordering: the point of the list is that the top of it matters most.
        List<ResourceSuggestion> ordered = new ArrayList<>(found.size());
        for (WeakTopic topic : weakTopics) {
            found.stream()
                    .filter(resource -> topic.topicName() != null
                            && topic.topicName().equalsIgnoreCase(resource.getTopic()))
                    .map(StudyResourceService::toSuggestion)
                    .forEach(ordered::add);
        }
        return ordered;
    }

    @Transactional(readOnly = true)
    public List<ResourceSuggestion> forSubject(String subject) {
        return resources.findBySubjectIgnoreCaseOrderByTopicAsc(subject).stream()
                .map(StudyResourceService::toSuggestion)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ResourceSuggestion> all() {
        return resources.findAll().stream()
                .map(StudyResourceService::toSuggestion)
                .toList();
    }

    private static ResourceSuggestion toSuggestion(StudyResource resource) {
        return new ResourceSuggestion(
                resource.getSubject(),
                resource.getTopic(),
                resource.getResourceType().name(),
                resource.getTitle(),
                resource.getUrl());
    }
}
