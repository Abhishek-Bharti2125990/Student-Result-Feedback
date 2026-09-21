package com.srip.dto.auth;

import com.srip.domain.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Request and response payloads for the authentication endpoints. */
public final class AuthDtos {

    private AuthDtos() {
    }

    /** Either username or e-mail is accepted as the identifier. */
    public record LoginRequest(
            @NotBlank String username,
            @NotBlank String password
    ) {
    }

    /**
     * Admin-only account creation. {@code admissionNo} links a STUDENT login to
     * an existing student record; {@code staffNo} creates the TEACHER record.
     */
    public record RegisterRequest(
            @NotBlank @Size(min = 3, max = 64) String username,
            @NotBlank @Email String email,
            @NotBlank @Size(min = 8, max = 72) String password,
            @NotBlank String fullName,
            @NotNull Role role,
            String admissionNo,
            String staffNo,
            String department
    ) {
    }

    public record RefreshRequest(@NotBlank String refreshToken) {
    }

    public record LogoutRequest(@NotBlank String refreshToken) {
    }

    public record TokenResponse(
            String accessToken,
            String refreshToken,
            String tokenType,
            long expiresInSeconds,
            UserProfile user
    ) {
    }

    /**
     * @param studentId for a STUDENT login, the student record they own; null otherwise
     */
    public record UserProfile(
            Long id,
            String username,
            String email,
            String fullName,
            Role role,
            Long studentId
    ) {
    }
}
