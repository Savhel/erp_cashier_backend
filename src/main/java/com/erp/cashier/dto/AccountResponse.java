package com.erp.cashier.dto;

import java.time.LocalDateTime;
import java.util.List;
import lombok.Data;

/**
 * Account response payload.
 *
 * @author ERP Cashier Team
 * @since 2025-01-20
 */
@Data
public class AccountResponse {
    private String id;
    private String accountNumber;
    private String bankAccountNumber;
    private String accountingAccount;
    private String accountLabel;
    private Double totalFunds;
    private Boolean isActive;
    private LocalDateTime createOn;
    private String ownerId;
    private AccountOwnerResponse owner;
    private List<AccountEventResponse> events;
    private List<CashRegisterMovementResponse> operations;

    /**
     * Default constructor for JSON serialization.
     */
    public AccountResponse() {
    }

    /**
     * Creates an account response.
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
    public AccountResponse(
            String id,
            String accountNumber,
            String bankAccountNumber,
            String accountingAccount,
            String accountLabel,
            Double totalFunds,
            Boolean isActive,
            LocalDateTime createOn,
            String ownerId,
            AccountOwnerResponse owner,
            List<AccountEventResponse> events,
            List<CashRegisterMovementResponse> operations
    ) {
        this.id = id;
        this.accountNumber = accountNumber;
        this.bankAccountNumber = bankAccountNumber;
        this.accountingAccount = accountingAccount;
        this.accountLabel = accountLabel;
        this.totalFunds = totalFunds;
        this.isActive = isActive;
        this.createOn = createOn;
        this.ownerId = ownerId;
        this.owner = owner;
        this.events = events;
        this.operations = operations;
    }
}
