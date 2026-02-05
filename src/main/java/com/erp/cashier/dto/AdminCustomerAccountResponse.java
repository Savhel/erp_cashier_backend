package com.erp.cashier.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * Account summary for admin customer responses.
 *
 * @author ERP Cashier Team
 * @since 2026-02-04
 */
@Data
public class AdminCustomerAccountResponse {
    @JsonProperty("id")
    private String id;

    @JsonProperty("account_number")
    private String accountNumber;

    @JsonProperty("total_funds")
    private Double totalFunds;

    @JsonProperty("is_active")
    private Boolean isActive;

    @JsonProperty("create_on")
    private LocalDateTime createOn;

    /**
     * Default constructor for JSON serialization.
     */
    public AdminCustomerAccountResponse() {
    }

    /**
     * Creates an admin customer account response.
     *
     * @param id account identifier
     * @param accountNumber account number
     * @param totalFunds total funds
     * @param isActive active flag
     * @param createOn creation timestamp
     */
    public AdminCustomerAccountResponse(
            String id,
            String accountNumber,
            Double totalFunds,
            Boolean isActive,
            LocalDateTime createOn
    ) {
        this.id = id;
        this.accountNumber = accountNumber;
        this.totalFunds = totalFunds;
        this.isActive = isActive;
        this.createOn = createOn;
    }
}
