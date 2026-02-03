package com.erp.cashier.dto;

import lombok.Data;

/**
 * Response payload for peer-to-peer transfers.
 *
 * @author ERP Cashier Team
 * @since 2025-01-20
 */
@Data
public class AccountP2PTransferResponse {
    private boolean success;
    private String inMovementId;
    private String outMovementId;
    private String reference;

    /**
     * Default constructor for JSON serialization.
     */
    public AccountP2PTransferResponse() {
    }

    /**
     * Creates a peer-to-peer transfer response.
     *
     * @param success success flag
     * @param inMovementId inbound movement identifier
     * @param outMovementId outbound movement identifier
     * @param reference reference
     */
    public AccountP2PTransferResponse(
            boolean success,
            String inMovementId,
            String outMovementId,
            String reference
    ) {
        this.success = success;
        this.inMovementId = inMovementId;
        this.outMovementId = outMovementId;
        this.reference = reference;
    }
}
