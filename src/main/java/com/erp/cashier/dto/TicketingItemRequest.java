package com.erp.cashier.dto;

import lombok.Data;

/**
 * Ticketing item request payload.
 *
 * @author ERP Cashier Team
 * @since 2025-01-20
 */
@Data
public class TicketingItemRequest {
    private String denominationId;
    private Integer quantity;

    /**
     * Default constructor for JSON serialization.
     */
    public TicketingItemRequest() {
    }
}
