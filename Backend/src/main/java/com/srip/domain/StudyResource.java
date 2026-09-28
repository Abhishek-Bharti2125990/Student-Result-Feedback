package com.srip.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * A curated book or video for one topic.
 *
 * <p>Subject and topic are plain text rather than foreign keys. This library is
 * maintained independently of what any particular upload contains, and a
 * resource for a topic nobody has been examined on yet is still a valid row.
 *
 * <p>The AI prompt is built from this table, which is the point: the model can
 * recommend a resource, but only one the school has actually vetted. Letting it
 * invent a book title or a video link would be the fastest way to lose a
 * teacher's trust in the whole feature.
 */
@Entity
@Table(name = "study_resources")
public class StudyResource {

    public enum ResourceType {
        BOOK,
        VIDEO
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 96)
    private String subject;

    @Column(nullable = false, length = 96)
    private String topic;

    @Enumerated(EnumType.STRING)
    @Column(name = "resource_type", nullable = false, length = 16)
    private ResourceType resourceType;

    @Column(nullable = false, length = 200)
    private String title;

    /** Null for a printed book, which has no link. */
    @Column(length = 512)
    private String url;

    protected StudyResource() {
        // required by JPA
    }

    public StudyResource(String subject, String topic, ResourceType resourceType, String title, String url) {
        this.subject = subject;
        this.topic = topic;
        this.resourceType = resourceType;
        this.title = title;
        this.url = url;
    }

    public Long getId() {
        return id;
    }

    public String getSubject() {
        return subject;
    }

    public String getTopic() {
        return topic;
    }

    public ResourceType getResourceType() {
        return resourceType;
    }

    public String getTitle() {
        return title;
    }

    public String getUrl() {
        return url;
    }
}
