package com.erp.cashier.dto;

import lombok.Data;

/**
 * Person payload for movement parties.
 *
 * @author ERP Cashier Team
 * @since 2026-02-04
 */
@Data
public class MovementPersonResponse {
    private String userFirstName;
    private String userName;

    /**
     * Default constructor for JSON serialization.
     */
    public MovementPersonResponse() {
    }

    /**
     * Creates a person payload.
     *
     * @param userFirstName first name
     * @param userName username
     */
    public MovementPersonResponse(String userFirstName, String userName) {
        this.userFirstName = userFirstName;
        this.userName = userName;
    }
}
