package com.srip.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "subjects")
public class Subject {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Slug of {@link #name}, e.g. {@code SOCIAL-SCIENCE}. The CSV names subjects
     * rather than coding them, so the slug is what makes "Social Science" on two
     * different lines resolve to one row.
     */
    @Column(nullable = false, unique = true, length = 32)
    private String code;

    @Column(nullable = false, length = 96)
    private String name;

    @Column(name = "class_name", nullable = false, length = 16)
    private String className;

    protected Subject() {
        // required by JPA
    }

    public Subject(String code, String name, String className) {
        this.code = code;
        this.name = name;
        this.className = className;
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

    public String getClassName() {
        return className;
    }
}
