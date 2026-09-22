package com.srip.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDate;

@Entity
@Table(name = "exams")
public class Exam {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 32)
    private String code;

    @Column(nullable = false, length = 96)
    private String name;

    @Column(nullable = false, length = 24)
    private String term;

    /** Trends are ordered by this date, not by insertion order. */
    @Column(name = "exam_date", nullable = false)
    private LocalDate examDate;

    @Column(name = "class_name", nullable = false, length = 16)
    private String className;

    @Column(name = "academic_year", nullable = false, length = 16)
    private String academicYear;

    protected Exam() {
        // required by JPA
    }

    public Exam(String code, String name, String term, LocalDate examDate, String className, String academicYear) {
        this.code = code;
        this.name = name;
        this.term = term;
        this.examDate = examDate;
        this.className = className;
        this.academicYear = academicYear;
    }

    public Long getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public String getTerm() {
        return term;
    }

    public LocalDate getExamDate() {
        return examDate;
    }

    public String getClassName() {
        return className;
    }

    public String getAcademicYear() {
        return academicYear;
    }
}
