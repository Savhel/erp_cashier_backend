package com.erp.cashier.security;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * In-memory storage for external access tokens.
 *
 * @author ERP Cashier Team
 * @since 2026-01-30
 */
@Component
public class ExternalTokenStore {
    private final Map<String, ExternalUserContext> tokens = new ConcurrentHashMap<>();

    public enum TokenStatus {
        VALID,
        EXPIRED,
        MISSING
    }

    /**
     * Stores the token context.
     *
     * @param token access token
     * @param context user context
     */
    public void store(String token, ExternalUserContext context) {
        if (!StringUtils.hasText(token) || context == null) {
            return;
        }
        tokens.put(token, context);
    }

    /**
     * Finds an existing token context.
     *
     * @param token access token
     * @return user context if present
     */
    public Optional<ExternalUserContext> find(String token) {
        return status(token) == TokenStatus.VALID
                ? Optional.ofNullable(tokens.get(token))
                : Optional.empty();
    }

    /**
     * Returns the status of a token.
     *
     * @param token access token
     * @return token status
     */
    public TokenStatus status(String token) {
        if (!StringUtils.hasText(token)) {
            return TokenStatus.MISSING;
        }
        ExternalUserContext context = tokens.get(token);
        if (context == null) {
            return TokenStatus.MISSING;
        }
        Instant expiresAt = context.getExpiresAt();
        if (expiresAt != null && Instant.now().isAfter(expiresAt)) {
            tokens.remove(token);
            return TokenStatus.EXPIRED;
        }
        return TokenStatus.VALID;
    }

    /**
     * Updates the organization identifier for a token.
     *
     * @param token access token
     * @param organizationId organization identifier
     */
    public void updateOrganization(String token, String organizationId) {
        if (!StringUtils.hasText(token) || !StringUtils.hasText(organizationId)) {
            return;
        }
        ExternalUserContext context = tokens.get(token);
        if (context != null) {
            context.setOrganizationId(organizationId);
        }
    }
}
