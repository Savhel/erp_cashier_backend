package com.erp.cashier.dto;

import lombok.Data;

/**
 * Cash register summary for session responses.
 *
 * @author ERP Cashier Team
 * @since 2025-01-02
 */
@Data
public class SessionCashRegisterResponse {
    private String id;
    private String town;
    private String country;
    private String agencyId;

    /**
     * Default constructor for JSON serialization.
     */
    public SessionCashRegisterResponse() {
    }

    /**
     * Creates a cash register summary response.
     *
     * @param id register identifier
     * @param town register town
     * @param country register country
     * @param agencyId agency identifier
     */
    public SessionCashRegisterResponse(String id, String town, String country, String agencyId) {
        this.id = id;
        this.town = town;
        this.country = country;
        this.agencyId = agencyId;
    }
}
