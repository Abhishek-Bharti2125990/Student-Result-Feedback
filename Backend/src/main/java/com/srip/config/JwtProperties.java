package com.srip.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * JWT signing and lifetime settings, bound from {@code app.jwt.*}.
 *
 * @param secret          HS256 signing key; must be at least 32 characters
 * @param issuer          value placed in the {@code iss} claim
 * @param accessTokenTtl  how long an access token stays valid
 * @param refreshTokenTtl how long a refresh token stays valid
 */
@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(
        String secret,
        String issuer,
        Duration accessTokenTtl,
        Duration refreshTokenTtl
) {

    public JwtProperties {
        issuer = issuer == null ? "srip" : issuer;
        accessTokenTtl = accessTokenTtl == null ? Duration.ofMinutes(30) : accessTokenTtl;
        refreshTokenTtl = refreshTokenTtl == null ? Duration.ofDays(7) : refreshTokenTtl;
        if (secret == null || secret.getBytes(java.nio.charset.StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException(
                    "app.jwt.secret must be set and at least 32 bytes long for HS256 signing");
        }
    }
}
