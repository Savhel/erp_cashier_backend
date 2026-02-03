package com.erp.cashier.dto;

import lombok.Data;

/**
 * Public organization response payload.
 *
 * @author ERP Cashier Team
 * @since 2025-01-02
 */
@Data
public class PublicOrganizationResponse {
    private String id;
    private String name;
    private String country;

    /**
     * Default constructor for JSON serialization.
     */
    public PublicOrganizationResponse() {
    }

    /**
     * Creates a public organization response.
     *
     * @param id organization identifier
     * @param name organization name
     * @param country organization country
     */
    public PublicOrganizationResponse(String id, String name, String country) {
        this.id = id;
        this.name = name;
        this.country = country;
    }
}
