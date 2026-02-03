package com.erp.cashier.dto;

import lombok.Data;

/**
 * Organization summary payload embedded in admin responses.
 *
 * @author ERP Cashier Team
 * @since 2025-01-02
 */
@Data
public class OrganizationSummaryResponse {
    private String id;
    private String name;
    private String country;
    private String telegramBotToken;

    /**
     * Default constructor for JSON serialization.
     */
    public OrganizationSummaryResponse() {
    }

    /**
     * Creates an organization summary response.
     *
     * @param id organization identifier
     * @param name organization name
     * @param country organization country
     * @param telegramBotToken telegram bot token
     */
    public OrganizationSummaryResponse(String id, String name, String country, String telegramBotToken) {
        this.id = id;
        this.name = name;
        this.country = country;
        this.telegramBotToken = telegramBotToken;
    }
}
