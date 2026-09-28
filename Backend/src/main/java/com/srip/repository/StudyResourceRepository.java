package com.srip.repository;

import com.srip.domain.StudyResource;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface StudyResourceRepository extends JpaRepository<StudyResource, Long> {

    /**
     * Resources for a set of topics in one query.
     *
     * <p>Matching is case-insensitive on the topic name because the library is
     * curated by hand while the topic names come from a spreadsheet, and
     * "Quadratic equations" must not miss a row filed as "Quadratic Equations".
     *
     * <p>Only the topic is filtered here, in one query rather than one per topic.
     * A topic name shared by two subjects - "Statistics" in both Maths and
     * Science - would match both, so the caller pairs each row back to the
     * subject it asked about before returning anything.
     */
    @Query("""
            select r from StudyResource r
            where lower(r.topic) in :topics
            order by r.subject asc, r.topic asc, r.resourceType asc
            """)
    List<StudyResource> findByTopicNames(@Param("topics") Collection<String> lowercaseTopics);

    List<StudyResource> findBySubjectIgnoreCaseOrderByTopicAsc(String subject);
}
