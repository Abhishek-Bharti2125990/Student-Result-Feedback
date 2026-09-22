package com.srip.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "students")
public class Student {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Null until the student is given a login. Results can be loaded before that. */
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", unique = true)
    private UserAccount user;

    @Column(name = "admission_no", nullable = false, unique = true, length = 32)
    private String admissionNo;

    @Column(name = "full_name", nullable = false, length = 120)
    private String fullName;

    @Column(name = "class_name", nullable = false, length = 16)
    private String className;

    @Column(length = 8)
    private String section;

    @Column(name = "academic_year", nullable = false, length = 16)
    private String academicYear;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Student() {
        // required by JPA
    }

    public Student(String admissionNo, String fullName, String className, String section, String academicYear) {
        this.admissionNo = admissionNo;
        this.fullName = fullName;
        this.className = className;
        this.section = section;
        this.academicYear = academicYear;
    }

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public UserAccount getUser() {
        return user;
    }

    public void setUser(UserAccount user) {
        this.user = user;
    }

    public String getAdmissionNo() {
        return admissionNo;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
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

    public String getAcademicYear() {
        return academicYear;
    }

    public void setAcademicYear(String academicYear) {
        this.academicYear = academicYear;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
