package com.srip.service;

import com.srip.domain.StudyResource;
import com.srip.dto.analytics.AnalyticsDtos.WeakTopic;
import com.srip.dto.dashboard.DashboardDtos.ResourceSuggestion;
import com.srip.repository.StudyResourceRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Checks the seeded resource library and how weak topics are matched to it.
 *
 * <p>Runs against the real {@code study_resources} rows from migration V2 rather
 * than a mock, because the thing most likely to break here is a topic name in
 * the library drifting from the topic names the curriculum uses - and only the
 * real data can catch that.
 */
@SpringBootTest
@ActiveProfiles("test")
class StudyResourceServiceTest {

    @Autowired
    private StudyResourceService studyResources;

    @Autowired
    private StudyResourceRepository repository;

    @Test
    void theLibraryIsSeededWithBothABookAndAVideoForEveryTopic() {
        List<StudyResource> all = repository.findAll();

        assertThat(all).isNotEmpty();
        assertThat(all).extracting(StudyResource::getResourceType)
                .contains(StudyResource.ResourceType.BOOK, StudyResource.ResourceType.VIDEO);

        // A weak topic with only a book is a worse recommendation than one with
        // both, so the library should not have half-covered topics.
        assertThat(all).allSatisfy(resource -> {
            long forSameTopic = all.stream()
                    .filter(other -> other.getTopic().equals(resource.getTopic())
                            && other.getSubject().equals(resource.getSubject()))
                    .count();
            assertThat(forSameTopic).isEqualTo(2);
        });
    }

    @Test
    void everyVideoHasALinkAndEveryBookHasATitle() {
        assertThat(repository.findAll()).allSatisfy(resource -> {
            assertThat(resource.getTitle()).isNotBlank();
            if (resource.getResourceType() == StudyResource.ResourceType.VIDEO) {
                assertThat(resource.getUrl()).startsWith("https://");
            }
        });
    }

    @Test
    void weakTopicsAreReturnedWorstFirst() {
        List<ResourceSuggestion> suggestions = studyResources.forWeakTopics(List.of(
                weakTopic("Trigonometry", "42.00"),
                weakTopic("Quadratic Equations", "38.00")));

        // The caller supplies the ranking; the service must preserve it rather
        // than reordering by whatever the database returned.
        assertThat(suggestions).isNotEmpty();
        assertThat(suggestions.get(0).topic()).isEqualTo("Trigonometry");
        assertThat(suggestions).extracting(ResourceSuggestion::topic)
                .containsOnly("Trigonometry", "Quadratic Equations");
    }

    @Test
    void matchingIsCaseInsensitive() {
        // The library is curated by hand; the topic names come from a
        // spreadsheet. They will not always agree on capitalisation.
        assertThat(studyResources.forWeakTopics(List.of(weakTopic("quadratic equations", "40.00"))))
                .isNotEmpty();
    }

    @Test
    void aTopicWithNoCuratedResourceContributesNothing() {
        // An empty list is a truthful answer. Padding it with a generic
        // "search online" suggestion would be worse than saying nothing.
        assertThat(studyResources.forWeakTopics(List.of(weakTopic("Quantum Field Theory", "10.00"))))
                .isEmpty();
    }

    @Test
    void noWeakTopicsMeansNoSuggestions() {
        assertThat(studyResources.forWeakTopics(List.of())).isEmpty();
        assertThat(studyResources.forWeakTopics(null)).isEmpty();
    }

    @Test
    void oneSubjectsSliceOfTheLibraryCanBeListedOnItsOwn() {
        List<ResourceSuggestion> maths = studyResources.forSubject("mathematics");

        assertThat(maths).isNotEmpty();
        assertThat(maths).allSatisfy(resource ->
                assertThat(resource.subject()).isEqualTo("Mathematics"));
        assertThat(maths).extracting(ResourceSuggestion::topic).contains("Quadratic Equations");
    }

    private static WeakTopic weakTopic(String name, String percentage) {
        return new WeakTopic("MATHEMATICS", name, new BigDecimal(percentage), 1);
    }
}
