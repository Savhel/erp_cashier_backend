package com.erp.cashier.dto;

import java.math.BigDecimal;
import lombok.Data;

/**
 * Request payload for account operations.
 *
 * @author ERP Cashier Team
 * @since 2025-01-20
 */
@Data
public class AccountOperationRequest {
    private String accountId;
    private BigDecimal amount;
    private TicketingRequest ticketing;
    private String reason;
    private String reference;
    private PaymentMethod paymentMethod;

    /**
     * Default constructor for JSON serialization.
     */
    public AccountOperationRequest() {
    }
}
