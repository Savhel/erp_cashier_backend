package com.erp.cashier.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Data;

/**
 * Response payload for fund request funding.
 */
@Data
public class FundRequestFundingResponse {
    private boolean success;

    @JsonProperty("request_id")
    private String requestId;

    private String status;
    private String reference;

    @JsonProperty("in_movement_id")
    private String inMovementId;

    @JsonProperty("out_movement_id")
    private String outMovementId;

    @JsonProperty("session_id")
    private String sessionId;

    @JsonProperty("funded_on")
    private Instant fundedOn;

    @JsonProperty("opening_funds_before")
    private BigDecimal openingFundsBefore;

    @JsonProperty("opening_funds_after")
    private BigDecimal openingFundsAfter;

    @JsonProperty("ticketing_applied")
    private Boolean ticketingApplied;

    @JsonProperty("ticketing_total")
    private BigDecimal ticketingTotal;

    /**
     * Default constructor for JSON serialization.
     */
    public FundRequestFundingResponse() {
    }

    /**
     * Creates a fund request funding response.
     */
    public FundRequestFundingResponse(
            boolean success,
            String requestId,
            String status,
            String reference,
            String inMovementId,
            String outMovementId,
            String sessionId,
            Instant fundedOn,
            BigDecimal openingFundsBefore,
            BigDecimal openingFundsAfter,
            Boolean ticketingApplied,
            BigDecimal ticketingTotal
    ) {
        this.success = success;
        this.requestId = requestId;
        this.status = status;
        this.reference = reference;
        this.inMovementId = inMovementId;
        this.outMovementId = outMovementId;
        this.sessionId = sessionId;
        this.fundedOn = fundedOn;
        this.openingFundsBefore = openingFundsBefore;
        this.openingFundsAfter = openingFundsAfter;
        this.ticketingApplied = ticketingApplied;
        this.ticketingTotal = ticketingTotal;
    }
}
