package com.erp.cashier.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import lombok.Data;

/**
 * Payload forwarded to the external accounting system.
 *
 * @author ERP Cashier Team
 * @since 2026-02-05
 */
@Data
public class AccountingCashMovementRequest {
    private String id;

    @JsonProperty("session_id")
    private String sessionId;

    private String sense;
    private BigDecimal amount;
    private String reason;

    @JsonProperty("recipient_id")
    private String recipientId;

    @JsonProperty("emitter_id")
    private String emitterId;

    @JsonProperty("is_accounted")
    private Boolean isAccounted;

    @JsonProperty("event_ticketing_details")
    private Boolean eventTicketingDetails;

    @JsonProperty("external_reference")
    private String externalReference;

    @JsonProperty("create_on")
    private String createOn;

    @JsonProperty("create_by")
    private String createBy;

    @JsonProperty("emitter_accounting_account")
    private String emitterAccountingAccount;

    @JsonProperty("recipient_accounting_account")
    private String recipientAccountingAccount;

    @JsonProperty("is_deleted")
    private Boolean isDeleted;
}
