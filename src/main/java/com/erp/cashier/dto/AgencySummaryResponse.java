package com.erp.cashier.dto;

import lombok.Data;

/**
 * Agency summary payload embedded in admin responses.
 *
 * @author ERP Cashier Team
 * @since 2025-01-02
 */
@Data
public class AgencySummaryResponse {
    private String id;
    private String name;
    private String country;
    private String town;
    private String neighborhood;
    private String organizationId;

    /**
     * Default constructor for JSON serialization.
     */
    public AgencySummaryResponse() {
    }

    /**
     * Creates an agency summary response.
     *
     * @param id agency identifier
     * @param name agency name
     * @param country agency country
     * @param town agency town
     * @param neighborhood agency neighborhood
     * @param organizationId organization identifier
     */
    public AgencySummaryResponse(
            String id,
            String name,
            String country,
            String town,
            String neighborhood,
            String organizationId
    ) {
        this.id = id;
        this.name = name;
        this.country = country;
        this.town = town;
        this.neighborhood = neighborhood;
        this.organizationId = organizationId;
    }
}
