package com.finance.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import java.security.Key;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.Date;
import java.util.Map;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class JwtService {

    private static final Logger log = LoggerFactory.getLogger(JwtService.class);
    private static final String DEV_ACCESS_SECRET = "finance-tracker-dev-access-secret-key-2026-strong";
    private static final String DEV_REFRESH_SECRET = "finance-tracker-dev-refresh-secret-key-2026-strong";

    private final SecretKey accessTokenKey;
    private final SecretKey refreshTokenKey;
    private final long accessTokenExpirationSeconds;
    private final long refreshTokenExpirationSeconds;

    public JwtService(
            @Value("${app.jwt.access-token-secret}") String accessTokenSecret,
            @Value("${app.jwt.refresh-token-secret}") String refreshTokenSecret,
            @Value("${app.jwt.access-token-expiration-seconds}") long accessTokenExpirationSeconds,
            @Value("${app.jwt.refresh-token-expiration-seconds}") long refreshTokenExpirationSeconds,
            Environment environment
    ) {
        this.accessTokenKey = buildSigningKey(resolveSecret(accessTokenSecret, false, environment));
        this.refreshTokenKey = buildSigningKey(resolveSecret(refreshTokenSecret, true, environment));
        this.accessTokenExpirationSeconds = accessTokenExpirationSeconds;
        this.refreshTokenExpirationSeconds = refreshTokenExpirationSeconds;
    }

    public String generateAccessToken(FinanceUserPrincipal principal) {
        return buildToken(principal, accessTokenKey, accessTokenExpirationSeconds, Map.of("uid", principal.userId().toString()));
    }

    public String generateRefreshToken(FinanceUserPrincipal principal) {
        return buildToken(principal, refreshTokenKey, refreshTokenExpirationSeconds, Map.of("uid", principal.userId().toString(), "type", "refresh"));
    }

    public String extractUsername(String token, boolean refreshToken) {
        return parseClaims(token, refreshToken ? refreshTokenKey : accessTokenKey).getSubject();
    }

    public UUID extractUserId(String token, boolean refreshToken) {
        return UUID.fromString(parseClaims(token, refreshToken ? refreshTokenKey : accessTokenKey).get("uid", String.class));
    }

    public boolean isTokenValid(String token, FinanceUserPrincipal principal, boolean refreshToken) {
        Claims claims = parseClaims(token, refreshToken ? refreshTokenKey : accessTokenKey);
        return principal.getUsername().equals(claims.getSubject()) && claims.getExpiration().after(new Date());
    }

    public long getAccessTokenExpirationSeconds() {
        return accessTokenExpirationSeconds;
    }

    public long getRefreshTokenExpirationSeconds() {
        return refreshTokenExpirationSeconds;
    }

    private String buildToken(FinanceUserPrincipal principal, Key key, long expiresInSeconds, Map<String, Object> claims) {
        Instant now = Instant.now();
        return Jwts.builder()
                .claims(claims)
                .subject(principal.getUsername())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(expiresInSeconds)))
                .signWith(key)
                .compact();
    }

    private Claims parseClaims(String token, Key key) {
        return Jwts.parser()
                .verifyWith((SecretKey) key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private String resolveSecret(String secret, boolean refreshSecret, Environment environment) {
        if (secret != null && !secret.trim().isEmpty()) {
            if (secret.trim().length() < 32) {
                throw new IllegalStateException("JWT secret must be at least 32 characters long");
            }
            return secret.trim();
        }

        if (isDevProfile(environment)) {
            return refreshSecret ? DEV_REFRESH_SECRET : DEV_ACCESS_SECRET;
        }

        throw new IllegalStateException("JWT secret is missing. Please configure environment variables.");
    }

    private SecretKey buildSigningKey(String secret) {
        byte[] keyBytes = deriveKeyBytes(secret);
        if (keyBytes.length < 32) {
            throw new IllegalStateException("JWT secret must be at least 32 characters long");
        }
        SecretKey key = Keys.hmacShaKeyFor(keyBytes);
        log.info("JWT secret loaded successfully");
        return key;
    }

    private byte[] deriveKeyBytes(String secret) {
        byte[] utf8Bytes = secret.getBytes(StandardCharsets.UTF_8);
        try {
            byte[] decoded = Decoders.BASE64.decode(secret);
            if (decoded.length >= 32) {
                return decoded;
            }
        } catch (Exception ignored) {
            // Fallback to UTF-8 bytes when secret is not valid Base64.
        }
        return utf8Bytes;
    }

    private boolean isDevProfile(Environment environment) {
        return Arrays.stream(environment.getActiveProfiles())
                .anyMatch(profile -> "dev".equalsIgnoreCase(profile));
    }
}
