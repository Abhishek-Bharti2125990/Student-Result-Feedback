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

    @Column(name = "marks_obtained", nullable = false, precision = 6, scale = 2)
    private BigDecimal marksObtained;

    @Column(name = "max_marks", nullable = false, precision = 6, scale = 2)
    private BigDecimal maxMarks;

    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal percentage;

    protected TopicScore() {
        // required by JPA
    }

    public TopicScore(Topic topic, BigDecimal marksObtained, BigDecimal maxMarks, BigDecimal percentage) {
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

    void setExamResult(ExamResult examResult) {
        this.examResult = examResult;
    }

    public Topic getTopic() {
        return topic;
    }

    public BigDecimal getMarksObtained() {
        return marksObtained;
    }

    public BigDecimal getMaxMarks() {
        return maxMarks;
    }

    public BigDecimal getPercentage() {
        return percentage;
    }
}
