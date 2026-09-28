package com.srip.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * An examinable topic inside a subject.
 *
 * <p>{@code chapterName} is the textbook unit the topic belongs to, taken from
 * the {@code chapter_name} column of the upload. It is carried so advice can
 * name the chapter to revise rather than only the topic, which is the
 * difference between "revise quadratic equations" and "revise NCERT chapter 4".
 */
@Entity
@Table(name = "topics")
public class Topic {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "subject_id", nullable = false)
    private Subject subject;

    @Column(name = "chapter_name", length = 96)
    private String chapterName;

    @Column(nullable = false, length = 96)
    private String name;

    protected Topic() {
        // required by JPA
    }

    public Topic(Subject subject, String chapterName, String name) {
        this.subject = subject;
        this.chapterName = chapterName;
        this.name = name;
    }

    public Long getId() {
        return id;
    }

    public Subject getSubject() {
        return subject;
    }

    public String getChapterName() {
        return chapterName;
    }

    public void setChapterName(String chapterName) {
        this.chapterName = chapterName;
    }

    public String getName() {
        return name;
    }
}
