package com.erp.cashier.dto;

import java.time.LocalDateTime;
import lombok.Data;

/**
 * Organization response payload for admin endpoints.
 *
 * @author ERP Cashier Team
 * @since 2025-01-02
 */
@Data
public class OrganizationResponse {
    private String id;
    private String name;
    private String country;
    private String description;
    private Boolean isActive;
    private LocalDateTime createOn;
    private String createBy;
    private String telegramBotToken;
    private OrganizationCreatorResponse creator;

    /**
     * Default constructor for JSON serialization.
     */
    public OrganizationResponse() {
    }

    /**
     * Creates an organization response.
     *
     * @param id organization identifier
     * @param name organization name
     * @param country organization country
     * @param description organization description
     * @param isActive active flag
     * @param createOn creation timestamp
     * @param createBy creator identifier
     * @param telegramBotToken telegram bot token
     * @param creator creator payload
     */
    public OrganizationResponse(
            String id,
            String name,
            String country,
            String description,
            Boolean isActive,
            LocalDateTime createOn,
            String createBy,
            String telegramBotToken,
            OrganizationCreatorResponse creator
    ) {
        this.id = id;
        this.name = name;
        this.country = country;
        this.description = description;
        this.isActive = isActive;
        this.createOn = createOn;
        this.createBy = createBy;
        this.telegramBotToken = telegramBotToken;
        this.creator = creator;
    }
}
