package com.erp.cashier.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * Cash register summary for admin account operations.
 *
 * @author ERP Cashier Team
 * @since 2026-02-03
 */
@Data
public class AdminAccountOperationRegisterResponse {
    @JsonProperty("id")
    private String id;

    @JsonProperty("town")
    private String town;

    /**
     * Default constructor for JSON serialization.
     */
    public AdminAccountOperationRegisterResponse() {
    }

    /**
     * Creates a cash register summary.
     *
     * @param id register identifier
     * @param town register town
     */
    public AdminAccountOperationRegisterResponse(String id, String town) {
        this.id = id;
        this.town = town;
    }
}
