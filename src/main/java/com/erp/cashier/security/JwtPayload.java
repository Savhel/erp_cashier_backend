package com.erp.cashier.security;

import java.time.Instant;

/**
 * Decoded JWT payload for authenticated users.
 *
 * @author ERP Cashier Team
 * @since 2025-01-02
 */
public class JwtPayload {
    private final String userId;
    private final String username;
    private final String role;
    private final String roleType;
    private final String organizationId;
    private final String agencyId;
    private final Instant expiresAt;

    /**
     * Creates a JWT payload.
     *
     * @param userId user identifier
     * @param username username
     * @param role role name
     * @param roleType role type
     * @param organizationId organization identifier
     * @param agencyId agency identifier
     * @param expiresAt expiration instant
     */
    public JwtPayload(
            String userId,
            String username,
            String role,
            String roleType,
            String organizationId,
            String agencyId,
            Instant expiresAt
    ) {
        this.userId = userId;
        this.username = username;
        this.role = role;
        this.roleType = roleType;
        this.organizationId = organizationId;
        this.agencyId = agencyId;
        this.expiresAt = expiresAt;
    }

    /**
     * Returns the user identifier.
     *
     * @return user identifier
     */
    public String getUserId() {
        return userId;
    }

    /**
     * Returns the username.
     *
     * @return username
     */
    public String getUsername() {
        return username;
    }

    /**
     * Returns the role name.
     *
     * @return role name
     */
    public String getRole() {
        return role;
    }

    /**
     * Returns the role type.
     *
     * @return role type
     */
    public String getRoleType() {
        return roleType;
    }

    /**
     * Returns the organization identifier.
     *
     * @return organization identifier
     */
    public String getOrganizationId() {
        return organizationId;
    }

    /**
     * Returns the agency identifier.
     *
     * @return agency identifier
     */
    public String getAgencyId() {
        return agencyId;
    }

    /**
     * Returns the expiration instant.
     *
     * @return expiration instant
     */
    public Instant getExpiresAt() {
        return expiresAt;
    }
}
