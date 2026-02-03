package com.erp.cashier.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Data;

/**
 * Movement payload for session responses.
 *
 * @author ERP Cashier Team
 * @since 2025-01-02
 */
@Data
public class SessionMovementResponse {
    private String id;
    private String sense;
    private BigDecimal amount;
    private String reason;
    private LocalDateTime createOn;
    private CashRegisterUserResponse creator;
    private List<CashRegisterTicketingDetailResponse> ticketingDetails;

    /**
     * Default constructor for JSON serialization.
     */
    public SessionMovementResponse() {
    }

    /**
     * Creates a movement response.
     *
     * @param id movement identifier
     * @param sense movement sense
     * @param amount movement amount
     * @param reason movement reason
     * @param createOn creation timestamp
     * @param creator creator summary
     * @param ticketingDetails ticketing details
     */
    public SessionMovementResponse(
            String id,
            String sense,
            BigDecimal amount,
            String reason,
            LocalDateTime createOn,
            CashRegisterUserResponse creator,
            List<CashRegisterTicketingDetailResponse> ticketingDetails
    ) {
        this.id = id;
        this.sense = sense;
        this.amount = amount;
        this.reason = reason;
        this.createOn = createOn;
        this.creator = creator;
        this.ticketingDetails = ticketingDetails;
    }
}
