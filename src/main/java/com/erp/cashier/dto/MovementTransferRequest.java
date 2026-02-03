package com.erp.cashier.dto;

import java.math.BigDecimal;
import java.util.List;
import lombok.Data;

/**
 * Request payload for register transfer movements.
 *
 * @author ERP Cashier Team
 * @since 2025-01-20
 */
@Data
public class MovementTransferRequest {
    private BigDecimal amount;
    private List<TicketingItemRequest> ticketing;

    /**
     * Default constructor for JSON serialization.
     */
    public MovementTransferRequest() {
    }
}
