package com.erp.cashier.dto;

import lombok.Data;

/**
 * Generic ok response payload.
 *
 * @author ERP Cashier Team
 * @since 2025-01-20
 */
@Data
public class OkResponse {
    private boolean ok;

    /**
     * Default constructor for JSON serialization.
     */
    public OkResponse() {
    }

    /**
     * Creates an ok response.
     *
     * @param ok ok flag
     */
    public OkResponse(boolean ok) {
        this.ok = ok;
    }
}
