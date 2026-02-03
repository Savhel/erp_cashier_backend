package com.erp.cashier.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Data;

/**
 * Detailed cash register session response payload.
 *
 * @author ERP Cashier Team
 * @since 2025-01-02
 */
@Data
public class CashRegisterSessionDetailResponse {
    private String id;
    private String state;
    private LocalDateTime openOn;
    private LocalDateTime closeOn;
    private BigDecimal theoricalInitialFunds;
    private BigDecimal theoricalCloseFunds;
    private Boolean isLocked;
    private CashRegisterUserResponse opener;
    private CashRegisterUserResponse closer;
    private List<CashRegisterMovementResponse> movements;
    private List<CashRegisterTicketingDetailResponse> ticketingDetails;
    private CashRegisterReconciliationResponse reconciliation;

    /**
     * Default constructor for JSON serialization.
     */
    public CashRegisterSessionDetailResponse() {
    }

    /**
     * Creates a detailed session response.
     *
     * @param id session identifier
     * @param state session state
     * @param openOn open timestamp
     * @param closeOn close timestamp
     * @param theoricalInitialFunds initial funds
     * @param theoricalCloseFunds close funds
     * @param isLocked locked flag
     * @param opener opener summary
     * @param closer closer summary
     * @param movements movement list
     * @param ticketingDetails ticketing details list
     * @param reconciliation reconciliation summary
     */
    public CashRegisterSessionDetailResponse(
            String id,
            String state,
            LocalDateTime openOn,
            LocalDateTime closeOn,
            BigDecimal theoricalInitialFunds,
            BigDecimal theoricalCloseFunds,
            Boolean isLocked,
            CashRegisterUserResponse opener,
            CashRegisterUserResponse closer,
            List<CashRegisterMovementResponse> movements,
            List<CashRegisterTicketingDetailResponse> ticketingDetails,
            CashRegisterReconciliationResponse reconciliation
    ) {
        this.id = id;
        this.state = state;
        this.openOn = openOn;
        this.closeOn = closeOn;
        this.theoricalInitialFunds = theoricalInitialFunds;
        this.theoricalCloseFunds = theoricalCloseFunds;
        this.isLocked = isLocked;
        this.opener = opener;
        this.closer = closer;
        this.movements = movements;
        this.ticketingDetails = ticketingDetails;
        this.reconciliation = reconciliation;
    }
}
