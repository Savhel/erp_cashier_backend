package com.erp.cashier.dto;

import lombok.Data;

/**
 * Session response payload for authenticated users.
 *
 * @author ERP Cashier Team
 * @since 2025-01-02
 */
@Data
public class AuthSessionResponse {
    private LoginUserResponse user;
    private NamedEntityResponse organization;
    private NamedEntityResponse agency;

    /**
     * Default constructor for JSON serialization.
     */
    public AuthSessionResponse() {
    }

    /**
     * Creates a session response.
     *
     * @param user authenticated user
     * @param organization organization summary
     * @param agency agency summary
     */
    public AuthSessionResponse(
            LoginUserResponse user,
            NamedEntityResponse organization,
            NamedEntityResponse agency
    ) {
        this.user = user;
        this.organization = organization;
        this.agency = agency;
    }
}
