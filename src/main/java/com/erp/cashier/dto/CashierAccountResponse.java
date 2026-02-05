package com.erp.cashier.dto;

import java.time.LocalDateTime;
import java.util.List;
import lombok.Data;

/**
 * Account response payload for cashier endpoints.
 *
 * @author ERP Cashier Team
 * @since 2026-02-04
 */
@Data
public class CashierAccountResponse {
    private String id;
    private String accountNumber;
    private Double totalFunds;
    private Boolean isActive;
    private LocalDateTime createOn;
    private CashierAccountCustomerResponse customer;
    private List<AccountEventResponse> events;

    /**
     * Default constructor for JSON serialization.
     */
    public CashierAccountResponse() {
    }

    /**
     * Creates a cashier account response.
     *
     * @param id account identifier
     * @param accountNumber account number
     * @param totalFunds total funds
     * @param isActive active flag
     * @param createOn creation timestamp
     * @param customer customer payload
     * @param events events list
     */
    public CashierAccountResponse(
            String id,
            String accountNumber,
            Double totalFunds,
            Boolean isActive,
            LocalDateTime createOn,
            CashierAccountCustomerResponse customer,
            List<AccountEventResponse> events
    ) {
        this.id = id;
        this.accountNumber = accountNumber;
        this.totalFunds = totalFunds;
        this.isActive = isActive;
        this.createOn = createOn;
        this.customer = customer;
        this.events = events;
    }
}
