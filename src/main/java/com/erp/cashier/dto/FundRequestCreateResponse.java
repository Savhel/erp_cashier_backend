package com.erp.cashier.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Data;

/**
 * Response payload for cashier fund request creation.
 */
@Data
public class FundRequestCreateResponse {
    private boolean success;

    @JsonProperty("request_id")
    private String requestId;

    private String reference;
    private String status;
    private BigDecimal amount;
    private String reason;

    @JsonProperty("created_on")
    private Instant createdOn;

    /**
     * Default constructor for JSON serialization.
     */
    public FundRequestCreateResponse() {
    }

    /**
     * Creates a fund request creation response.
     */
    public FundRequestCreateResponse(
            boolean success,
            String requestId,
            String reference,
            String status,
            BigDecimal amount,
            String reason,
            Instant createdOn
    ) {
        this.success = success;
        this.requestId = requestId;
        this.reference = reference;
        this.status = status;
        this.amount = amount;
        this.reason = reason;
        this.createdOn = createdOn;
    }
}
