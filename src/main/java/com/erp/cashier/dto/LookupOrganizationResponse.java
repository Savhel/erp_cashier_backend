package com.erp.cashier.dto;

import lombok.Data;

/**
 * Lookup response payload for organizations.
 *
 * @author ERP Cashier Team
 * @since 2025-01-20
 */
@Data
public class LookupOrganizationResponse {
    private String id;
    private String name;
    private String country;
    private String description;
    private String telegramBotToken;
    private Boolean isActive;

    /**
     * Default constructor for JSON serialization.
     */
    public LookupOrganizationResponse() {
    }

    /**
     * Creates a lookup organization response.
     *
     * @param id organization identifier
     * @param name organization name
     * @param country country
     * @param description description
     * @param telegramBotToken bot token
     * @param isActive active flag
     */
    public LookupOrganizationResponse(
            String id,
            String name,
            String country,
            String description,
            String telegramBotToken,
            Boolean isActive
    ) {
        this.id = id;
        this.name = name;
        this.country = country;
        this.description = description;
        this.telegramBotToken = telegramBotToken;
        this.isActive = isActive;
    }
}
