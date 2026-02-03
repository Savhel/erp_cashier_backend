package com.erp.cashier.dto;

import lombok.Data;

/**
 * Public agency response payload.
 *
 * @author ERP Cashier Team
 * @since 2025-01-02
 */
@Data
public class PublicAgencyResponse {
    private String id;
    private String name;
    private String country;
    private String town;
    private String neighborhood;

    /**
     * Default constructor for JSON serialization.
     */
    public PublicAgencyResponse() {
    }

    /**
     * Creates a public agency response.
     *
     * @param id agency identifier
     * @param name agency name
     * @param country agency country
     * @param town agency town
     * @param neighborhood agency neighborhood
     */
    public PublicAgencyResponse(
            String id,
            String name,
            String country,
            String town,
            String neighborhood
    ) {
        this.id = id;
        this.name = name;
        this.country = country;
        this.town = town;
        this.neighborhood = neighborhood;
    }
}
