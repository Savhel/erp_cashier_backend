package com.erp.cashier.dto;

import java.math.BigDecimal;
import lombok.Data;

/**
 * Response payload for register transfers.
 *
 * @author ERP Cashier Team
 * @since 2025-01-20
 */
@Data
public class MovementTransferResponse {
    private boolean success;
    private String message;
    private BigDecimal newBalance;
    private CashRegisterSummaryResponse sourceRegister;

    /**
     * Default constructor for JSON serialization.
     */
    public MovementTransferResponse() {
    }

    public MovementTransferResponse(
            boolean success,
            String message,
            BigDecimal newBalance,
            CashRegisterSummaryResponse sourceRegister
    ) {
        this.success = success;
        this.message = message;
        this.newBalance = newBalance;
        this.sourceRegister = sourceRegister;
    }
}
