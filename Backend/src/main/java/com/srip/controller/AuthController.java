package com.srip.controller;

import com.srip.dto.auth.AuthDtos.LoginRequest;
import com.srip.dto.auth.AuthDtos.LogoutRequest;
import com.srip.dto.auth.AuthDtos.RefreshRequest;
import com.srip.dto.auth.AuthDtos.RegisterRequest;
import com.srip.dto.auth.AuthDtos.TokenResponse;
import com.srip.dto.auth.AuthDtos.UserProfile;
import com.srip.service.AccessGuard;
import com.srip.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Authentication endpoints.
 *
 * <p>{@code /login}, {@code /refresh} and {@code /logout} are public; the
 * refresh and logout bodies are themselves the credential. {@code /register} is
 * restricted to ADMIN in {@code SecurityConfig} - this is a school system, so
 * accounts are issued by the office, not self-registered.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final AccessGuard accessGuard;

    public AuthController(AuthService authService, AccessGuard accessGuard) {
        this.authService = authService;
        this.accessGuard = accessGuard;
    }

    @PostMapping("/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @PostMapping("/refresh")
    public TokenResponse refresh(@Valid @RequestBody RefreshRequest request) {
        return authService.refresh(request.refreshToken());
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody LogoutRequest request) {
        authService.logout(request.refreshToken());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/register")
    public ResponseEntity<UserProfile> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
    }

    /** The caller's own profile, used by clients to resolve their student id. */
    @GetMapping("/me")
    public UserProfile me() {
        return authService.profileOf(accessGuard.currentUserId());
    }
}
