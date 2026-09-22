package com.srip.domain;

/**
 * The four user roles of the platform. Spring Security authorities are derived
 * as {@code ROLE_<name>}, which is what {@code hasRole(...)} expects.
 */
public enum Role {
    STUDENT,
    TEACHER,
    PARENT,
    ADMIN;

    public String authority() {
        return "ROLE_" + name();
    }
}
