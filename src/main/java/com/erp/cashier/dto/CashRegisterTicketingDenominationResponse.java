package com.erp.cashier.dto;

import java.math.BigDecimal;
import lombok.Data;

/**
 * Denomination response payload for ticketing details.
 *
 * @author ERP Cashier Team
 * @since 2025-01-02
 */
@Data
public class CashRegisterTicketingDenominationResponse {
    private BigDecimal value;
    private String label;

    /**
     * Default constructor for JSON serialization.
     */
    public CashRegisterTicketingDenominationResponse() {
    }

    /**
     * Creates a denomination response.
     *
     * @param value denomination value
     * @param label denomination label
     */
    public CashRegisterTicketingDenominationResponse(BigDecimal value, String label) {
        this.value = value;
        this.label = label;
    }
}
