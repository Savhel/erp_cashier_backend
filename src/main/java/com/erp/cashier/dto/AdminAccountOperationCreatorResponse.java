package com.erp.cashier.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * Creator payload for admin account operations.
 *
 * @author ERP Cashier Team
 * @since 2026-02-04
 */
@Data
public class AdminAccountOperationCreatorResponse {
    @JsonProperty("user_first_name")
    private String userFirstName;

    @JsonProperty("user_name")
    private String userName;

    /**
     * Default constructor for JSON serialization.
     */
    public AdminAccountOperationCreatorResponse() {
    }

    /**
     * Creates a creator response.
     *
     * @param userFirstName display name
     * @param userName username
     */
    public AdminAccountOperationCreatorResponse(String userFirstName, String userName) {
        this.userFirstName = userFirstName;
        this.userName = userName;
    }
}
