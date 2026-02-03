package com.erp.cashier.dto;

import java.math.BigDecimal;
import java.util.List;
import lombok.Data;

/**
 * Request payload for bill payments.
 *
 * @author ERP Cashier Team
 * @since 2025-01-20
 */
@Data
public class BillPaymentRequest {
    private String invoiceCode;
    private BigDecimal amount;
    private String paymentMode;
    private BigDecimal cashGiven;
    private List<TicketingItemRequest> ticketing;
    private List<TicketingItemRequest> changeTicketing;
    private String accountId;

    /**
     * Default constructor for JSON serialization.
     */
    public BillPaymentRequest() {
    }
}
