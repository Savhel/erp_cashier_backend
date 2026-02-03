package com.erp.cashier.dto;

import lombok.Data;

/**
 * Minimal cashier profile response payload.
 *
 * @author ERP Cashier Team
 * @since 2026-02-02
 */
@Data
public class CashierListProfileResponse {
    private String workTown;
    private String townListChosen;

    /**
     * Default constructor for JSON serialization.
     */
    public CashierListProfileResponse() {
    }

    /**
     * Creates a minimal cashier profile response.
     *
     * @param workTown work town
     * @param townListChosen towns list
     */
    public CashierListProfileResponse(String workTown, String townListChosen) {
        this.workTown = workTown;
        this.townListChosen = townListChosen;
    }
}
