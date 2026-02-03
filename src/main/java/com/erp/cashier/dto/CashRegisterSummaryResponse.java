package com.erp.cashier.dto;

import lombok.Data;

/**
 * Cash register summary payload.
 *
 * @author ERP Cashier Team
 * @since 2025-01-20
 */
@Data
public class CashRegisterSummaryResponse {
    private String id;
    private String town;
    private String country;
    private String neighborhood;

    /**
     * Default constructor for JSON serialization.
     */
    public CashRegisterSummaryResponse() {
    }

    /**
     * Creates a cash register summary response.
     *
     * @param id register identifier
     * @param town town
     * @param country country
     * @param neighborhood neighborhood
     */
    public CashRegisterSummaryResponse(String id, String town, String country, String neighborhood) {
        this.id = id;
        this.town = town;
        this.country = country;
        this.neighborhood = neighborhood;
    }
}
