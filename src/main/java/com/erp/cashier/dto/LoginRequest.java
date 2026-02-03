package com.erp.cashier.dto;

import lombok.Data;

/**
 * Login request payload.
 *
 * @author ERP Cashier Team
 * @since 2025-01-02
 */
@Data
public class LoginRequest {
    private String email;
    private String username;
    private String password;
    private String organizationId;
    private String agencyId;

    /**
     * Default constructor for JSON deserialization.
     */
    public LoginRequest() {
    }
}
