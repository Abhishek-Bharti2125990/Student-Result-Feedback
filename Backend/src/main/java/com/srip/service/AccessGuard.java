package com.srip.service;

import com.srip.domain.Role;
import com.srip.exception.ApiExceptions;
import com.srip.repository.ParentStudentRepository;
import com.srip.security.AppUserPrincipal;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Row-level authorisation.
 *
 * <p>URL rules cannot answer "may this caller read student 42?", so every
 * student-scoped service call passes through here. Centralising it means a new
 * endpoint cannot accidentally omit the check by forgetting an annotation - it
 * has to ask for the student id, and asking runs the check.
 */
@Service
public class AccessGuard {

    private final ParentStudentRepository parentLinks;

    public AccessGuard(ParentStudentRepository parentLinks) {
        this.parentLinks = parentLinks;
    }

    /** @throws AccessDeniedException if the current caller may not read this student */
    @Transactional(readOnly = true)
    public void assertCanReadStudent(Long studentId) {
        AppUserPrincipal principal = currentPrincipal();
        Role role = principal.getRole();

        switch (role) {
            case ADMIN, TEACHER -> {
                // Staff may read any student in the school.
            }
            case STUDENT -> {
                if (!studentId.equals(principal.getStudentId())) {
                    throw new AccessDeniedException("A student may only read their own results");
                }
            }
            case PARENT -> {
                if (!parentLinks.existsByParentIdAndStudentId(principal.getUserId(), studentId)) {
                    throw new AccessDeniedException("This student is not linked to your account");
                }
            }
        }
    }

    /** The student record owned by the caller, for "my results" style endpoints. */
    public Long currentStudentIdOrFail() {
        AppUserPrincipal principal = currentPrincipal();
        Long studentId = principal.getStudentId();
        if (studentId == null) {
            throw new ApiExceptions.BadRequestException(
                    "This login is not linked to a student record; call the student-specific endpoint instead");
        }
        return studentId;
    }

    public AppUserPrincipal currentPrincipal() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof AppUserPrincipal principal)) {
            throw new AccessDeniedException("No authenticated user in context");
        }
        return principal;
    }

    public Long currentUserId() {
        return currentPrincipal().getUserId();
    }
}
