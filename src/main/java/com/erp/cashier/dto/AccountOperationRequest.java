package com.erp.cashier.dto;

import java.math.BigDecimal;
import java.util.List;
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
    private List<TicketingItemRequest> ticketing;
    private String reason;
    private String reference;

    /**
     * Default constructor for JSON serialization.
     */
    public AccountOperationRequest() {
    }
}
