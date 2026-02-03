package com.erp.cashier.dto;

import lombok.Data;

/**
 * Base agency payload for cashier listings.
 *
 * @author ERP Cashier Team
 * @since 2026-02-01
 */
@Data
public class BaseAgencyResponse {
    private String id;
    private String name;
    private String town;

    /**
     * Default constructor for JSON serialization.
     */
    public BaseAgencyResponse() {
    }

    /**
     * Creates a base agency response.
     *
     * @param id agency identifier
     * @param name agency name
     * @param town agency town
     */
    public BaseAgencyResponse(String id, String name, String town) {
        this.id = id;
        this.name = name;
        this.town = town;
    }
}
