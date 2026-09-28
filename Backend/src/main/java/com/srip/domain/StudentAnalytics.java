package com.srip.domain;

import com.srip.analytics.ScoreCategory;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * The computed standing of one student in one exam, written by the analytics
 * step of the import job.
 *
 * <p>Percentage, grade, rank and category are stored rather than derived per
 * request. The teacher dashboard's whole job is to bucket a class by category,
 * and deriving that on read would mean re-ranking the entire class on every
 * page load; with this table it is one indexed query per bucket.
 *
 * <p>The four strength and weakness lists are JSON, not child tables. They are
 * only ever read back whole, as part of one student's card, so a table would add
 * four joins and buy nothing. {@code AnalyticsWriteService} owns the encoding.
 */
@Entity
@Table(name = "analytics")
public class StudentAnalytics {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "student_id", nullable = false)
    private Long studentId;

    @Column(name = "exam_id", nullable = false)
    private Long examId;

    @Column(name = "class_name", nullable = false, length = 16)
    private String className;

    @Column(length = 8)
    private String section;

    @Column(name = "total_marks", nullable = false, precision = 8, scale = 2)
    private BigDecimal totalMarks;

    @Column(name = "max_marks", nullable = false, precision = 8, scale = 2)
    private BigDecimal maxMarks;

    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal percentage;

    @Column(nullable = false, length = 4)
    private String grade;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ScoreCategory category;

    @Column(name = "rank_in_class", nullable = false)
    private int rankInClass;

    @Column(name = "class_size", nullable = false)
    private int classSize;

    @Lob
    @Column(name = "strong_subjects")
    private String strongSubjectsJson;

    @Lob
    @Column(name = "weak_subjects")
    private String weakSubjectsJson;

    @Lob
    @Column(name = "strong_topics")
    private String strongTopicsJson;

    @Lob
    @Column(name = "weak_topics")
    private String weakTopicsJson;

    @Column(name = "upload_job_id")
    private Long uploadJobId;

    @Column(name = "computed_at", nullable = false)
    private Instant computedAt;

    protected StudentAnalytics() {
        // required by JPA
    }

    public StudentAnalytics(Long studentId, Long examId, String className, String section) {
        this.studentId = studentId;
        this.examId = examId;
        this.className = className;
        this.section = section;
    }

    public Long getId() {
        return id;
    }

    public Long getStudentId() {
        return studentId;
    }

    public Long getExamId() {
        return examId;
    }

    public String getClassName() {
        return className;
    }

    public void setClassName(String className) {
        this.className = className;
    }

    public String getSection() {
        return section;
    }

    public void setSection(String section) {
        this.section = section;
    }

    public BigDecimal getTotalMarks() {
        return totalMarks;
    }

    public void setTotalMarks(BigDecimal totalMarks) {
        this.totalMarks = totalMarks;
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

    public String getGrade() {
        return grade;
    }

    public void setGrade(String grade) {
        this.grade = grade;
    }

    public ScoreCategory getCategory() {
        return category;
    }

    public void setCategory(ScoreCategory category) {
        this.category = category;
    }

    public int getRankInClass() {
        return rankInClass;
    }

    public void setRankInClass(int rankInClass) {
        this.rankInClass = rankInClass;
    }

    public int getClassSize() {
        return classSize;
    }

    public void setClassSize(int classSize) {
        this.classSize = classSize;
    }

    public String getStrongSubjectsJson() {
        return strongSubjectsJson;
    }

    public void setStrongSubjectsJson(String strongSubjectsJson) {
        this.strongSubjectsJson = strongSubjectsJson;
    }

    public String getWeakSubjectsJson() {
        return weakSubjectsJson;
    }

    public void setWeakSubjectsJson(String weakSubjectsJson) {
        this.weakSubjectsJson = weakSubjectsJson;
    }

    public String getStrongTopicsJson() {
        return strongTopicsJson;
    }

    public void setStrongTopicsJson(String strongTopicsJson) {
        this.strongTopicsJson = strongTopicsJson;
    }

    public String getWeakTopicsJson() {
        return weakTopicsJson;
    }

    public void setWeakTopicsJson(String weakTopicsJson) {
        this.weakTopicsJson = weakTopicsJson;
    }

    public Long getUploadJobId() {
        return uploadJobId;
    }

    public void setUploadJobId(Long uploadJobId) {
        this.uploadJobId = uploadJobId;
    }

    public Instant getComputedAt() {
        return computedAt;
    }

    public void setComputedAt(Instant computedAt) {
        this.computedAt = computedAt;
    }
}
