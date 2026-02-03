package com.erp.cashier.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Data;

/**
 * Session response payload.
 *
 * @author ERP Cashier Team
 * @since 2025-01-02
 */
@Data
public class SessionResponse {
    private String id;
    private String state;
    private LocalDateTime openOn;
    private LocalDateTime closeOn;
    private String openBy;
    private BigDecimal theoricalInitialFunds;
    private BigDecimal theoricalCloseFunds;
    private Boolean isLocked;
    private SessionCashRegisterResponse cashRegister;
    private CashRegisterUserResponse opener;
    private CashRegisterUserResponse closer;
    private List<SessionMovementResponse> movements;
    private List<CashRegisterTicketingDetailResponse> ticketingDetails;
    private SessionReconciliationResponse reconciliation;

    /**
     * Default constructor for JSON serialization.
     */
    public SessionResponse() {
    }

    /**
     * Creates a session response.
     *
     * @param id session identifier
     * @param state session state
     * @param openOn open timestamp
     * @param closeOn close timestamp
     * @param openBy opener identifier
     * @param theoricalInitialFunds initial funds
     * @param theoricalCloseFunds close funds
     * @param isLocked locked flag
     * @param cashRegister cash register summary
     * @param opener opener summary
     * @param closer closer summary
     * @param movements movement list
     * @param ticketingDetails ticketing details
     * @param reconciliation reconciliation summary
     */
    public SessionResponse(
            String id,
            String state,
            LocalDateTime openOn,
            LocalDateTime closeOn,
            String openBy,
            BigDecimal theoricalInitialFunds,
            BigDecimal theoricalCloseFunds,
            Boolean isLocked,
            SessionCashRegisterResponse cashRegister,
            CashRegisterUserResponse opener,
            CashRegisterUserResponse closer,
            List<SessionMovementResponse> movements,
            List<CashRegisterTicketingDetailResponse> ticketingDetails,
            SessionReconciliationResponse reconciliation
    ) {
        this.id = id;
        this.state = state;
        this.openOn = openOn;
        this.closeOn = closeOn;
        this.openBy = openBy;
        this.theoricalInitialFunds = theoricalInitialFunds;
        this.theoricalCloseFunds = theoricalCloseFunds;
        this.isLocked = isLocked;
        this.cashRegister = cashRegister;
        this.opener = opener;
        this.closer = closer;
        this.movements = movements;
        this.ticketingDetails = ticketingDetails;
        this.reconciliation = reconciliation;
    }
}
