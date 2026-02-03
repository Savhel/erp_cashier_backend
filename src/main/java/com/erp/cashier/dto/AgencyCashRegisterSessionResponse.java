package com.erp.cashier.dto;

import lombok.Data;

/**
 * Cash register session summary for agency listings.
 *
 * @author ERP Cashier Team
 * @since 2026-02-02
 */
@Data
public class AgencyCashRegisterSessionResponse {
    private String state;
    private Boolean isLocked;

    /**
     * Default constructor for JSON serialization.
     */
    public AgencyCashRegisterSessionResponse() {
    }

    /**
     * Creates a cash register session response.
     *
     * @param state session state
     * @param isLocked locked flag
     */
    public AgencyCashRegisterSessionResponse(String state, Boolean isLocked) {
        this.state = state;
        this.isLocked = isLocked;
    }
}
