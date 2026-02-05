package com.erp.cashier.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * Account operation response for admin endpoints.
 *
 * @author ERP Cashier Team
 * @since 2026-02-03
 */
@Data
public class AdminAccountOperationResponse {
    @JsonProperty("id")
    private String id;

    @JsonProperty("amount")
    private BigDecimal amount;

    @JsonProperty("sense")
    private String sense;

    @JsonProperty("reason")
    private String reason;

    @JsonProperty("external_reference")
    private String externalReference;

    @JsonProperty("create_on")
    private LocalDateTime createOn;

    @JsonProperty("recipient_id")
    private String recipientId;

    @JsonProperty("emitter_id")
    private String emitterId;

    @JsonProperty("session")
    private AdminAccountOperationSessionResponse session;

    @JsonProperty("creator")
    private AdminAccountOperationCreatorResponse creator;

    /**
     * Default constructor for JSON serialization.
     */
    public AdminAccountOperationResponse() {
    }

    /**
     * Creates an admin account operation response.
     *
     * @param id movement identifier
     * @param amount movement amount
     * @param sense movement sense
     * @param reason movement reason
     * @param externalReference external reference
     * @param createOn creation timestamp
     * @param recipientId recipient account
     * @param emitterId emitter account
     * @param session session payload
     * @param creator creator payload
     */
    public AdminAccountOperationResponse(
            String id,
            BigDecimal amount,
            String sense,
            String reason,
            String externalReference,
            LocalDateTime createOn,
            String recipientId,
            String emitterId,
            AdminAccountOperationSessionResponse session,
            AdminAccountOperationCreatorResponse creator
    ) {
        this.id = id;
        this.amount = amount;
        this.sense = sense;
        this.reason = reason;
        this.externalReference = externalReference;
        this.createOn = createOn;
        this.recipientId = recipientId;
        this.emitterId = emitterId;
        this.session = session;
        this.creator = creator;
    }
}
