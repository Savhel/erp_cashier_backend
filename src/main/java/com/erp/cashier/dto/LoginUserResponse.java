package com.erp.cashier.dto;

import lombok.Data;

/**
 * User payload returned after a successful login.
 *
 * @author ERP Cashier Team
 * @since 2025-01-02
 */
@Data
public class LoginUserResponse {
    private String id;
    private String username;
    private String role;
    private String roleType;
    private String agencyId;
    private String organizationId;

    /**
     * Default constructor for JSON serialization.
     */
    public LoginUserResponse() {
    }

    /**
     * Creates a login user response.
     *
     * @param id user identifier
     * @param username username
     * @param role user role
     * @param roleType admin role type
     * @param agencyId agency identifier
     * @param organizationId organization identifier
     */
    public LoginUserResponse(
            String id,
            String username,
            String role,
            String roleType,
            String agencyId,
            String organizationId
    ) {
        this.id = id;
        this.username = username;
        this.role = role;
        this.roleType = roleType;
        this.agencyId = agencyId;
        this.organizationId = organizationId;
    }
}
