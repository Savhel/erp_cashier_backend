package com.erp.cashier.dto;

import lombok.Data;

/**
 * Account payload for cashier bill list.
 *
 * @author ERP Cashier Team
 * @since 2026-02-05
 */
@Data
public class BillListAccountResponse {
    private String accountNumber;
    private String customerPhone;

    /**
     * Default constructor for JSON serialization.
     */
    public BillListAccountResponse() {
    }

    public BillListAccountResponse(String accountNumber, String customerPhone) {
        this.accountNumber = accountNumber;
        this.customerPhone = customerPhone;
    }
}
