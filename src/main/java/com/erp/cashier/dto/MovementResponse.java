package com.erp.cashier.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * Movement response payload.
 *
 * @author ERP Cashier Team
 * @since 2025-01-20
 */
@Data
public class MovementResponse {
    private String id;
    private BigDecimal amount;
    private String sense;
    private String reason;
    private String externalReference;
    private LocalDateTime createOn;
    private MovementPartyResponse recipient;
    private MovementPartyResponse emitter;
    private CashRegisterSummaryResponse sourceRegister;
    private CashRegisterSummaryResponse destinationRegister;

    /**
     * Default constructor for JSON serialization.
     */
    public MovementResponse() {
    }

    /**
     * Creates a movement response.
     *
     * @param id movement identifier
     * @param amount amount
     * @param sense sense
     * @param reason reason
     * @param externalReference external reference
     * @param createOn creation timestamp
     * @param recipient recipient
     * @param emitter emitter
     * @param sourceRegister source register
     * @param destinationRegister destination register
     */
    public MovementResponse(
            String id,
            BigDecimal amount,
            String sense,
            String reason,
            String externalReference,
            LocalDateTime createOn,
            MovementPartyResponse recipient,
            MovementPartyResponse emitter,
            CashRegisterSummaryResponse sourceRegister,
            CashRegisterSummaryResponse destinationRegister
    ) {
        this.id = id;
        this.amount = amount;
        this.sense = sense;
        this.reason = reason;
        this.externalReference = externalReference;
        this.createOn = createOn;
        this.recipient = recipient;
        this.emitter = emitter;
        this.sourceRegister = sourceRegister;
        this.destinationRegister = destinationRegister;
    }
}
