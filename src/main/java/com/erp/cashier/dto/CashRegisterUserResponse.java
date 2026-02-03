package com.erp.cashier.dto;

import lombok.Data;

/**
 * User response payload for cash register details.
 *
 * @author ERP Cashier Team
 * @since 2025-01-02
 */
@Data
public class CashRegisterUserResponse {
    private String userName;
    private String userFirstName;

    /**
     * Default constructor for JSON serialization.
     */
    public CashRegisterUserResponse() {
    }

    /**
     * Creates a user response.
     *
     * @param userName username
     * @param userFirstName full name
     */
    public CashRegisterUserResponse(String userName, String userFirstName) {
        this.userName = userName;
        this.userFirstName = userFirstName;
    }
}
