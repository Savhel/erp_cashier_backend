package com.erp.cashier.dto;

import lombok.Data;

/**
 * Agency response payload for cash register views.
 *
 * @author ERP Cashier Team
 * @since 2025-01-02
 */
@Data
public class CashRegisterAgencyResponse {
    private String id;
    private String name;
    private String country;
    private String town;
    private String neighborhood;

    /**
     * Default constructor for JSON serialization.
     */
    public CashRegisterAgencyResponse() {
    }

    /**
     * Creates an agency response.
     *
     * @param id agency identifier
     * @param name agency name
     * @param country agency country
     * @param town agency town
     * @param neighborhood agency neighborhood
     */
    public CashRegisterAgencyResponse(
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
