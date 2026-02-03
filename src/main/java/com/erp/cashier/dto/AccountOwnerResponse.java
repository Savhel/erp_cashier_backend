package com.erp.cashier.dto;

import lombok.Data;

/**
 * Account owner payload.
 *
 * @author ERP Cashier Team
 * @since 2025-01-20
 */
@Data
public class AccountOwnerResponse {
    private String name;
    private String username;
    private String role;

    /**
     * Default constructor for JSON serialization.
     */
    public AccountOwnerResponse() {
    }

    /**
     * Creates an account owner response.
     *
     * @param name display name
     * @param username username
     * @param role role
     */
    public AccountOwnerResponse(String name, String username, String role) {
        this.name = name;
        this.username = username;
        this.role = role;
    }
}
