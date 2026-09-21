package com.srip.security;

import com.srip.config.JwtProperties;
import com.srip.domain.Role;
import com.srip.domain.UserAccount;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.HexFormat;

/**
 * Issues and verifies tokens.
 *
 * <p>Access tokens are self-contained HS256 JWTs, so verifying one is a
 * signature check with no database hit. Refresh tokens are the opposite by
 * design: opaque random strings whose SHA-256 hash is stored server-side, which
 * is what makes logout and rotation actually revoke access. A self-contained
 * refresh JWT could not be revoked before its expiry.
 */
@Service
public class JwtService {

    private static final String CLAIM_USER_ID = "uid";
    private static final String CLAIM_ROLE = "role";
    private static final int REFRESH_TOKEN_BYTES = 48;

    private final JwtProperties properties;
    private final SecretKey signingKey;
    private final SecureRandom random = new SecureRandom();

    public JwtService(JwtProperties properties) {
        this.properties = properties;
        this.signingKey = Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8));
    }

    public String createAccessToken(UserAccount user) {
        Instant now = Instant.now();
        Instant expiry = now.plus(properties.accessTokenTtl());
        return Jwts.builder()
                .subject(user.getUsername())
                .issuer(properties.issuer())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .claim(CLAIM_USER_ID, user.getId())
                .claim(CLAIM_ROLE, user.getRole().name())
                .signWith(signingKey, Jwts.SIG.HS256)
                .compact();
    }

    /** @return the parsed claims, or null when the token is invalid or expired */
    public Claims parseAccessToken(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(signingKey)
                    .requireIssuer(properties.issuer())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (JwtException | IllegalArgumentException e) {
            return null;
        }
    }

    public Long userIdFrom(Claims claims) {
        Object raw = claims.get(CLAIM_USER_ID);
        return raw instanceof Number number ? number.longValue() : null;
    }

    public Role roleFrom(Claims claims) {
        Object raw = claims.get(CLAIM_ROLE);
        return raw == null ? null : Role.valueOf(raw.toString());
    }

    public Duration accessTokenTtl() {
        return properties.accessTokenTtl();
    }

    public Duration refreshTokenTtl() {
        return properties.refreshTokenTtl();
    }

    /** A fresh opaque refresh token. The plain value is returned to the client once. */
    public String generateRefreshToken() {
        byte[] bytes = new byte[REFRESH_TOKEN_BYTES];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** SHA-256 is correct here, not BCrypt: the input is already 384 bits of
     *  entropy, so it needs no work factor, and lookup must be by exact hash. */
    public String hashRefreshToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required but unavailable", e);
        }
    }
}
