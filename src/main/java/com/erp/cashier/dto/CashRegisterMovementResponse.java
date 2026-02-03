package com.erp.cashier.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * Cash register movement response payload.
 *
 * @author ERP Cashier Team
 * @since 2025-01-02
 */
@Data
public class CashRegisterMovementResponse {
    private String id;
    private String sense;
    private BigDecimal amount;
    private String reason;
    private LocalDateTime createOn;
    private CashRegisterUserResponse creator;

    /**
     * Default constructor for JSON serialization.
     */
    public CashRegisterMovementResponse() {
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
     */
    public CashRegisterMovementResponse(
            String id,
            String sense,
            BigDecimal amount,
            String reason,
            LocalDateTime createOn,
            CashRegisterUserResponse creator
    ) {
        this.id = id;
        this.sense = sense;
        this.amount = amount;
        this.reason = reason;
        this.createOn = createOn;
        this.creator = creator;
    }
}
