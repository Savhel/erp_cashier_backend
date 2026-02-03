package com.erp.cashier.security;

import com.erp.cashier.dto.LoginUserResponse;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Service for generating and validating JWT access tokens.
 *
 * @author ERP Cashier Team
 * @since 2025-01-02
 */
@Component
public class JwtService {
    private static final String CLAIM_USERNAME = "username";
    private static final String CLAIM_ROLE = "role";
    private static final String CLAIM_ROLE_TYPE = "role_type";
    private static final String CLAIM_ORGANIZATION_ID = "organization_id";
    private static final String CLAIM_AGENCY_ID = "agency_id";

    private final SecretKey secretKey;
    private final String issuer;
    private final Duration accessTokenTtl;

    /**
     * Creates the JWT service.
     *
     * @param secret JWT secret
     * @param issuer JWT issuer
     * @param accessTokenMinutes access token lifetime in minutes
     * @throws IllegalStateException when the JWT secret is missing or too short
     */
    public JwtService(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.issuer:erp-cashier}") String issuer,
            @Value("${app.jwt.access-token-minutes:1440}") long accessTokenMinutes
    ) {
        if (!StringUtils.hasText(secret) || secret.length() < 32) {
            throw new IllegalStateException("JWT secret is not configured.");
        }
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.issuer = issuer;
        this.accessTokenTtl = Duration.ofMinutes(accessTokenMinutes);
    }

    /**
     * Generates a signed access token for the given user.
     *
     * @param user login user payload
     * @return signed JWT access token
     */
    public String generateAccessToken(LoginUserResponse user) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(accessTokenTtl);

        return Jwts.builder()
                .setSubject(user.getId())
                .setIssuer(issuer)
                .setIssuedAt(Date.from(now))
                .setExpiration(Date.from(expiresAt))
                .claim(CLAIM_USERNAME, user.getUsername())
                .claim(CLAIM_ROLE, user.getRole())
                .claim(CLAIM_ROLE_TYPE, user.getRoleType())
                .claim(CLAIM_ORGANIZATION_ID, user.getOrganizationId())
                .claim(CLAIM_AGENCY_ID, user.getAgencyId())
                .signWith(secretKey, SignatureAlgorithm.HS256)
                .compact();
    }

    /**
     * Parses and validates an access token.
     *
     * @param token JWT access token
     * @return decoded payload
     */
    public JwtPayload parseToken(String token) {
        Jws<Claims> claims = Jwts.parserBuilder()
                .setSigningKey(secretKey)
                .build()
                .parseClaimsJws(token);
        Claims body = claims.getBody();
        String userId = body.getSubject();
        Instant expiresAt = body.getExpiration() != null ? body.getExpiration().toInstant() : null;

        return new JwtPayload(
                userId,
                body.get(CLAIM_USERNAME, String.class),
                body.get(CLAIM_ROLE, String.class),
                body.get(CLAIM_ROLE_TYPE, String.class),
                body.get(CLAIM_ORGANIZATION_ID, String.class),
                body.get(CLAIM_AGENCY_ID, String.class),
                expiresAt
        );
    }

    /**
     * Returns the access token time-to-live in seconds.
     *
     * @return access token TTL in seconds
     */
    public long getAccessTokenTtlSeconds() {
        return accessTokenTtl.getSeconds();
    }
}
