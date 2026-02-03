package com.erp.cashier.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * Session summary payload for cash registers.
 *
 * @author ERP Cashier Team
 * @since 2025-01-02
 */
@Data
public class CashRegisterSessionSummaryResponse {
    private String id;
    private String state;
    private LocalDateTime openOn;
    private BigDecimal theoricalInitialFunds;
    private BigDecimal theoricalCloseFunds;

    /**
     * Default constructor for JSON serialization.
     */
    public CashRegisterSessionSummaryResponse() {
    }

    /**
     * Creates a session summary response.
     *
     * @param id session identifier
     * @param state session state
     * @param openOn open timestamp
     * @param theoricalInitialFunds initial funds
     * @param theoricalCloseFunds close funds
     */
    public CashRegisterSessionSummaryResponse(
            String id,
            String state,
            LocalDateTime openOn,
            BigDecimal theoricalInitialFunds,
            BigDecimal theoricalCloseFunds
    ) {
        this.id = id;
        this.state = state;
        this.openOn = openOn;
        this.theoricalInitialFunds = theoricalInitialFunds;
        this.theoricalCloseFunds = theoricalCloseFunds;
    }
}
