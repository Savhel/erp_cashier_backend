package com.erp.cashier.dto;

import lombok.Data;

/**
 * Request payload for updating an organization.
 *
 * @author ERP Cashier Team
 * @since 2025-01-02
 */
@Data
public class UpdateOrganizationRequest {
    private String name;
    private String country;
    private String description;
    private String telegramBotToken;
    private Boolean isActive;

    /**
     * Default constructor for JSON serialization.
     */
    public UpdateOrganizationRequest() {
    }
}
