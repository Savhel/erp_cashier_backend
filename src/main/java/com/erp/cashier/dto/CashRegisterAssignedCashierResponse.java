package com.erp.cashier.dto;

import lombok.Data;

/**
 * Assigned cashier response payload for cash registers.
 *
 * @author ERP Cashier Team
 * @since 2025-01-02
 */
@Data
public class CashRegisterAssignedCashierResponse {
    private String userName;
    private String userFirstName;

    /**
     * Default constructor for JSON serialization.
     */
    public CashRegisterAssignedCashierResponse() {
    }

    /**
     * Creates an assigned cashier response.
     *
     * @param userName username
     * @param userFirstName full name
     */
    public CashRegisterAssignedCashierResponse(String userName, String userFirstName) {
        this.userName = userName;
        this.userFirstName = userFirstName;
    }
}
