package com.erp.cashier.dto;

import lombok.Data;

/**
 * Person summary payload.
 *
 * @author ERP Cashier Team
 * @since 2025-01-20
 */
@Data
public class PersonSummaryResponse {
    private String id;
    private String userName;
    private String userFirstName;

    /**
     * Default constructor for JSON serialization.
     */
    public PersonSummaryResponse() {
    }

    /**
     * Creates a person summary response.
     *
     * @param id person identifier
     * @param userName username
     * @param userFirstName full name
     */
    public PersonSummaryResponse(String id, String userName, String userFirstName) {
        this.id = id;
        this.userName = userName;
        this.userFirstName = userFirstName;
    }
}
