package com.erp.cashier.dto;

import lombok.Data;

/**
 * Response payload for account transfers.
 *
 * @author ERP Cashier Team
 * @since 2025-01-20
 */
@Data
public class AccountTransferResponse {
    private boolean success;
    private Double newBalance;
    private String movementId;
    private String reference;

    /**
     * Default constructor for JSON serialization.
     */
    public AccountTransferResponse() {
    }

    /**
     * Creates an account transfer response.
     *
     * @param success success flag
     * @param newBalance new balance
     * @param movementId movement identifier
     * @param reference reference
     */
    public AccountTransferResponse(
            boolean success,
            Double newBalance,
            String movementId,
            String reference
    ) {
        this.success = success;
        this.newBalance = newBalance;
        this.movementId = movementId;
        this.reference = reference;
    }
}
