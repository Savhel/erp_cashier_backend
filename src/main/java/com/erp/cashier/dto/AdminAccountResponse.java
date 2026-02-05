package com.erp.cashier.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Data;

/**
 * Account response payload for admin endpoints.
 *
 * @author ERP Cashier Team
 * @since 2026-02-03
 */
@Data
public class AdminAccountResponse {
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

    @JsonProperty("ownerId")
    private String ownerId;

    @JsonProperty("owner")
    private AccountOwnerResponse owner;

    @JsonProperty("events")
    private List<AdminAccountEventResponse> events;

    @JsonProperty("operations")
    private List<AdminAccountOperationResponse> operations;

    /**
     * Default constructor for JSON serialization.
     */
    public AdminAccountResponse() {
    }

    /**
     * Creates an admin account response.
     *
     * @param id account identifier
     * @param accountNumber account number
     * @param totalFunds total funds
     * @param isActive active flag
     * @param createOn creation timestamp
     * @param ownerId owner identifier
     * @param owner owner payload
     * @param events events list
     * @param operations operations list
     */
    public AdminAccountResponse(
            String id,
            String accountNumber,
            Double totalFunds,
            Boolean isActive,
            LocalDateTime createOn,
            String ownerId,
            AccountOwnerResponse owner,
            List<AdminAccountEventResponse> events,
            List<AdminAccountOperationResponse> operations
    ) {
        this.id = id;
        this.accountNumber = accountNumber;
        this.totalFunds = totalFunds;
        this.isActive = isActive;
        this.createOn = createOn;
        this.ownerId = ownerId;
        this.owner = owner;
        this.events = events;
        this.operations = operations;
    }
}
