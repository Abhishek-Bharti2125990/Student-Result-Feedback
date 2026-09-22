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
 * Links a parent login to a child. This link is the authorisation boundary for
 * the PARENT role: a parent can only read data for students listed here.
 */
@Entity
@Table(name = "parent_students")
public class ParentStudent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "parent_user_id", nullable = false)
    private UserAccount parent;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    @Column(nullable = false, length = 24)
    private String relationship;

    protected ParentStudent() {
        // required by JPA
    }

    public ParentStudent(UserAccount parent, Student student, String relationship) {
        this.parent = parent;
        this.student = student;
        this.relationship = relationship;
    }

    public Long getId() {
        return id;
    }

    public UserAccount getParent() {
        return parent;
    }

    public Student getStudent() {
        return student;
    }

    public String getRelationship() {
        return relationship;
    }
}
