package com.erp.cashier.dto;

import java.math.BigDecimal;
import lombok.Data;

/**
 * Response payload for bill payments.
 *
 * @author ERP Cashier Team
 * @since 2025-01-20
 */
@Data
public class BillPaymentResponse {
    private boolean success;
    private String movementId;
    private String inMovementId;
    private String outMovementId;
    private BigDecimal change;
    private String reference;

    /**
     * Default constructor for JSON serialization.
     */
    public BillPaymentResponse() {
    }
}
