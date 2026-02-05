package com.erp.cashier.dto;

import java.math.BigDecimal;
import lombok.Data;

/**
 * Request payload for cashier fund requests.
 */
@Data
public class CashierFundRequest {
    private BigDecimal amount;
    private String reason;
    private String reference;
    private TicketingRequest ticketing;

    /**
     * Default constructor for JSON serialization.
     */
    public CashierFundRequest() {
    }
}
