package com.erp.cashier.dto;

import lombok.Data;

/**
 * Account payload for bill responses.
 *
 * @author ERP Cashier Team
 * @since 2026-02-05
 */
@Data
public class BillAccountResponse {
    private String id;
    private String accountNumber;
    private Double totalFunds;
    private Boolean isActive;
    private String customerName;
    private String customerPhone;

    /**
     * Default constructor for JSON serialization.
     */
    public BillAccountResponse() {
    }

    public BillAccountResponse(
            String id,
            String accountNumber,
            Double totalFunds,
            Boolean isActive,
            String customerName,
            String customerPhone
    ) {
        this.id = id;
        this.accountNumber = accountNumber;
        this.totalFunds = totalFunds;
        this.isActive = isActive;
        this.customerName = customerName;
        this.customerPhone = customerPhone;
    }
}
