package com.erp.cashier.dto;

import java.math.BigDecimal;
import java.util.Map;
import lombok.Data;

/**
 * Ticketing payload for cash movements.
 *
 * @author ERP Cashier Team
 * @since 2026-02-04
 */
@Data
public class TicketingRequest {
    private BigDecimal total;
    private Map<String, Integer> denominations;

    /**
     * Default constructor for JSON serialization.
     */
    public TicketingRequest() {
    }
}
