package com.erp.cashier.dto;

import java.util.List;
import lombok.Data;

/**
 * Login response payload.
 *
 * @author ERP Cashier Team
 * @since 2025-01-02
 */
@Data
public class LoginResponse {
    private boolean success;
    private LoginUserResponse user;
    private String accessToken;
    private String tokenType;
    private long expiresIn;
    private List<OrganizationMembershipResponse> organizations;

    /**
     * Default constructor for JSON serialization.
     */
    public LoginResponse() {
    }

    /**
     * Creates a login response.
     *
     * @param success whether login succeeded
     * @param user login user payload
     */
    public LoginResponse(boolean success, LoginUserResponse user) {
        this.success = success;
        this.user = user;
    }

    /**
     * Creates a login response with a token.
     *
     * @param success whether login succeeded
     * @param user login user payload
     * @param accessToken access token
     * @param tokenType token type
     * @param expiresIn token expiry in seconds
     */
    public LoginResponse(
            boolean success,
            LoginUserResponse user,
            String accessToken,
            String tokenType,
            long expiresIn
    ) {
        this.success = success;
        this.user = user;
        this.accessToken = accessToken;
        this.tokenType = tokenType;
        this.expiresIn = expiresIn;
    }
}
