package com.erp.cashier.dto;

import java.math.BigDecimal;
import lombok.Data;

/**
 * Ticketing detail response payload for cash register sessions.
 *
 * @author ERP Cashier Team
 * @since 2025-01-02
 */
@Data
public class CashRegisterTicketingDetailResponse {
    private String id;
    private String connectionType;
    private Integer quantity;
    private BigDecimal value;
    private BigDecimal total;
    private CashRegisterTicketingDenominationResponse denomination;

    /**
     * Default constructor for JSON serialization.
     */
    public CashRegisterTicketingDetailResponse() {
    }

    /**
     * Creates a ticketing detail response.
     *
     * @param id detail identifier
     * @param connectionType connection type
     * @param quantity quantity
     * @param value denomination value
     * @param total total amount
     * @param denomination denomination summary
     */
    public CashRegisterTicketingDetailResponse(
            String id,
            String connectionType,
            Integer quantity,
            BigDecimal value,
            BigDecimal total,
            CashRegisterTicketingDenominationResponse denomination
    ) {
        this.id = id;
        this.connectionType = connectionType;
        this.quantity = quantity;
        this.value = value;
        this.total = total;
        this.denomination = denomination;
    }
}
