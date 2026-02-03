package com.erp.cashier.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Data;

/**
 * Recent transaction response payload.
 *
 * @author ERP Cashier Team
 * @since 2025-01-20
 */
@Data
public class RecentTransactionResponse {
    @JsonProperty("id")
    private String id;

    @JsonProperty("amount")
    private BigDecimal amount;

    @JsonProperty("sense")
    private String sense;

    @JsonProperty("reason")
    private String reason;

    @JsonProperty("createdAt")
    private Instant createdAt;

    @JsonProperty("cashier")
    private String cashier;

    @JsonProperty("register")
    private String register;

    @JsonProperty("customer")
    private String customer;

    @JsonProperty("externalReference")
    private String externalReference;

    /**
     * Default constructor for JSON serialization.
     */
    public RecentTransactionResponse() {
    }

    /**
     * Creates a recent transaction response.
     *
     * @param id movement identifier
     * @param amount amount
     * @param sense sense
     * @param reason reason
     * @param createdAt creation timestamp
     * @param cashier cashier name
     * @param register register name
     * @param customer customer name
     * @param externalReference external reference
     */
    public RecentTransactionResponse(
            String id,
            BigDecimal amount,
            String sense,
            String reason,
            Instant createdAt,
            String cashier,
            String register,
            String customer,
            String externalReference
    ) {
        this.id = id;
        this.amount = amount;
        this.sense = sense;
        this.reason = reason;
        this.createdAt = createdAt;
        this.cashier = cashier;
        this.register = register;
        this.customer = customer;
        this.externalReference = externalReference;
    }
}
