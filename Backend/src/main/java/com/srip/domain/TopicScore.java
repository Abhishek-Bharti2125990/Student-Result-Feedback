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

import java.math.BigDecimal;

/**
 * One row of the uploaded CSV: a student's marks on a single topic.
 *
 * <p>This is the grain the file is delivered at, and the grain weak-topic
 * detection needs. The subject-level {@link ExamResult} is derived from these
 * rows, not the other way round.
 *
 * <p>Marks are mutable because a corrected file is re-uploaded against the same
 * student, exam and topic, and that must update the mark rather than add a
 * second one.
 */
@Entity
@Table(name = "topic_scores")
public class TopicScore {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "exam_result_id", nullable = false)
    private ExamResult examResult;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "topic_id", nullable = false)
    private Topic topic;

    @Column(name = "marks_obtained", nullable = false, precision = 8, scale = 2)
    private BigDecimal marksObtained;

    @Column(name = "max_marks", nullable = false, precision = 8, scale = 2)
    private BigDecimal maxMarks;

    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal percentage;

    protected TopicScore() {
        // required by JPA
    }

    /**
     * @param examResult the subject paper this row belongs to; set here rather
     *                   than by adding to the paper's collection, so writing one
     *                   topic never has to load the other twenty
     */
    public TopicScore(ExamResult examResult, Topic topic,
                      BigDecimal marksObtained, BigDecimal maxMarks, BigDecimal percentage) {
        this.examResult = examResult;
        this.topic = topic;
        this.marksObtained = marksObtained;
        this.maxMarks = maxMarks;
        this.percentage = percentage;
    }

    public Long getId() {
        return id;
    }

    public ExamResult getExamResult() {
        return examResult;
    }

    public Topic getTopic() {
        return topic;
    }

    public BigDecimal getMarksObtained() {
        return marksObtained;
    }

    public void setMarksObtained(BigDecimal marksObtained) {
        this.marksObtained = marksObtained;
    }

    public BigDecimal getMaxMarks() {
        return maxMarks;
    }

    public void setMaxMarks(BigDecimal maxMarks) {
        this.maxMarks = maxMarks;
    }

    public BigDecimal getPercentage() {
        return percentage;
    }

    public void setPercentage(BigDecimal percentage) {
        this.percentage = percentage;
    }
}
