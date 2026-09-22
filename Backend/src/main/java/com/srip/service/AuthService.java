package com.srip.service;

import com.srip.domain.RefreshToken;
import com.srip.domain.Role;
import com.srip.domain.Student;
import com.srip.domain.Teacher;
import com.srip.domain.UserAccount;
import com.srip.dto.auth.AuthDtos.LoginRequest;
import com.srip.dto.auth.AuthDtos.RegisterRequest;
import com.srip.dto.auth.AuthDtos.TokenResponse;
import com.srip.dto.auth.AuthDtos.UserProfile;
import com.srip.exception.ApiExceptions;
import com.srip.repository.RefreshTokenRepository;
import com.srip.repository.StudentRepository;
import com.srip.repository.TeacherRepository;
import com.srip.repository.UserAccountRepository;
import com.srip.security.JwtService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Login, refresh, logout and account creation.
 *
 * <p>Refresh tokens are <em>rotated</em>: presenting one revokes it and issues a
 * replacement. If a token is ever used twice - the signature of a stolen token
 * being replayed - the second attempt fails because the first use revoked it.
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private static final String TOKEN_TYPE = "Bearer";

    private final AuthenticationManager authenticationManager;
    private final UserAccountRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final StudentRepository students;
    private final TeacherRepository teachers;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(AuthenticationManager authenticationManager,
                       UserAccountRepository users,
                       RefreshTokenRepository refreshTokens,
                       StudentRepository students,
                       TeacherRepository teachers,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService) {
        this.authenticationManager = authenticationManager;
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.students = students;
        this.teachers = teachers;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Transactional
    public TokenResponse login(LoginRequest request) {
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.username(), request.password()));
        } catch (DisabledException e) {
            throw new ApiExceptions.AuthenticationFailedException("This account is disabled");
        } catch (BadCredentialsException e) {
            throw new ApiExceptions.AuthenticationFailedException("Invalid credentials");
        }

        UserAccount user = users.findByUsernameOrEmail(request.username())
                .orElseThrow(() -> new ApiExceptions.AuthenticationFailedException("Invalid credentials"));

        log.info("Login: {} as {}", user.getUsername(), user.getRole());
        return issueTokens(user);
    }

    @Transactional
    public TokenResponse refresh(String presentedToken) {
        String hash = jwtService.hashRefreshToken(presentedToken);
        RefreshToken existing = refreshTokens.findByTokenHash(hash)
                .orElseThrow(() -> new ApiExceptions.AuthenticationFailedException("Unknown refresh token"));

        if (!existing.isUsable(Instant.now())) {
            // A revoked token being presented again is worth acting on: kill
            // every session for this user rather than just refusing this one.
            refreshTokens.revokeAllForUser(existing.getUser().getId());
            throw new ApiExceptions.AuthenticationFailedException(
                    "This refresh token is expired or has already been used");
        }

        existing.revoke();
        refreshTokens.save(existing);

        return issueTokens(existing.getUser());
    }

    @Transactional
    public void logout(String presentedToken) {
        refreshTokens.findByTokenHash(jwtService.hashRefreshToken(presentedToken))
                .ifPresent(token -> {
                    refreshTokens.revokeAllForUser(token.getUser().getId());
                    log.info("Logout: all sessions revoked for user {}", token.getUser().getUsername());
                });
        // A missing token is treated as success: logout must be idempotent, and
        // reporting "unknown token" would let a caller probe for valid ones.
    }

    /**
     * Creates an account. Admin-only at the controller.
     *
     * <p>A STUDENT account must name an existing {@code admissionNo}: results
     * are loaded by admission number long before logins exist, and silently
     * creating a second student record would split one child's history in two.
     */
    @Transactional
    public UserProfile register(RegisterRequest request) {
        if (users.existsByUsername(request.username())) {
            throw new ApiExceptions.ConflictException("Username already taken: " + request.username());
        }
        if (users.existsByEmail(request.email())) {
            throw new ApiExceptions.ConflictException("Email already registered: " + request.email());
        }

        UserAccount user = users.save(new UserAccount(
                request.username(),
                request.email(),
                passwordEncoder.encode(request.password()),
                request.fullName(),
                request.role()));

        Long studentId = null;
        switch (request.role()) {
            case STUDENT -> studentId = linkStudent(user, request);
            case TEACHER -> createTeacher(user, request);
            case PARENT, ADMIN -> {
                // A parent is linked to children separately, via the admin API;
                // an admin needs no companion record.
            }
        }

        log.info("Created {} account '{}'", request.role(), request.username());
        return toProfile(user, studentId);
    }

    public UserProfile profileOf(Long userId) {
        UserAccount user = users.findById(userId)
                .orElseThrow(() -> ApiExceptions.NotFoundException.of("User", userId));
        Long studentId = user.getRole() == Role.STUDENT
                ? students.findByUserId(userId).map(Student::getId).orElse(null)
                : null;
        return toProfile(user, studentId);
    }

    private Long linkStudent(UserAccount user, RegisterRequest request) {
        if (request.admissionNo() == null || request.admissionNo().isBlank()) {
            throw new ApiExceptions.BadRequestException(
                    "admissionNo is required when creating a STUDENT account");
        }
        Student student = students.findByAdmissionNo(request.admissionNo())
                .orElseThrow(() -> new ApiExceptions.NotFoundException(
                        "No student record with admission number " + request.admissionNo()));
        if (student.getUser() != null) {
            throw new ApiExceptions.ConflictException(
                    "Student " + request.admissionNo() + " already has a login");
        }
        student.setUser(user);
        students.save(student);
        return student.getId();
    }

    private void createTeacher(UserAccount user, RegisterRequest request) {
        if (request.staffNo() == null || request.staffNo().isBlank()) {
            throw new ApiExceptions.BadRequestException(
                    "staffNo is required when creating a TEACHER account");
        }
        if (teachers.findByStaffNo(request.staffNo()).isPresent()) {
            throw new ApiExceptions.ConflictException("Staff number already in use: " + request.staffNo());
        }
        teachers.save(new Teacher(user, request.staffNo(), request.fullName(), request.department()));
    }

    private TokenResponse issueTokens(UserAccount user) {
        String accessToken = jwtService.createAccessToken(user);
        String refreshToken = jwtService.generateRefreshToken();

        Instant now = Instant.now();
        refreshTokens.save(new RefreshToken(
                user,
                jwtService.hashRefreshToken(refreshToken),
                now,
                now.plus(jwtService.refreshTokenTtl())));

        Long studentId = user.getRole() == Role.STUDENT
                ? students.findByUserId(user.getId()).map(Student::getId).orElse(null)
                : null;

        return new TokenResponse(
                accessToken,
                refreshToken,
                TOKEN_TYPE,
                jwtService.accessTokenTtl().toSeconds(),
                toProfile(user, studentId));
    }

    private UserProfile toProfile(UserAccount user, Long studentId) {
        return new UserProfile(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getFullName(),
                user.getRole(),
                studentId);
    }
}
