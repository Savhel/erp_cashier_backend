package com.erp.cashier.dto;

import java.math.BigDecimal;
import java.util.List;
import lombok.Data;

/**
 * Request payload for peer-to-peer transfers.
 *
 * @author ERP Cashier Team
 * @since 2025-01-20
 */
@Data
public class AccountP2PTransferRequest {
    private String sourceAccountId;
    private String destAccountId;
    private BigDecimal amount;
    private List<TicketingItemRequest> ticketing;
    private String reference;

    /**
     * Default constructor for JSON serialization.
     */
    public AccountP2PTransferRequest() {
    }
}
