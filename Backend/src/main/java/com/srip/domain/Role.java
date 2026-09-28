package com.srip.domain;

/**
 * The three user roles of the platform. Spring Security authorities are derived
 * as {@code ROLE_<name>}, which is what {@code hasRole(...)} expects.
 *
 * <p>The names here must match the rows seeded into the {@code roles} table by
 * migration V2: {@code users.role} is a foreign key onto it, so adding a role
 * to this enum without a matching migration would fail on insert.
 */
public enum Role {
    STUDENT,
    TEACHER,
    ADMIN;

    public String authority() {
        return "ROLE_" + name();
    }
}
