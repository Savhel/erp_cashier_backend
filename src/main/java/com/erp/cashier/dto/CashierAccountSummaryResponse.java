package com.erp.cashier.dto;

import java.time.LocalDateTime;
import lombok.Data;

/**
 * Account summary for cashier customer responses.
 *
 * @author ERP Cashier Team
 * @since 2026-02-04
 */
@Data
public class CashierAccountSummaryResponse {
    private String id;
    private String accountNumber;
    private Double totalFunds;
    private Boolean isActive;
    private LocalDateTime createOn;

    /**
     * Default constructor for JSON serialization.
     */
    public CashierAccountSummaryResponse() {
    }

    /**
     * Creates a cashier account summary.
     *
     * @param id account identifier
     * @param accountNumber account number
     * @param totalFunds total funds
     * @param isActive active flag
     * @param createOn creation timestamp
     */
    public CashierAccountSummaryResponse(
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
