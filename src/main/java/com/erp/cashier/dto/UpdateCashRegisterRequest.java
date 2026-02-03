package com.erp.cashier.dto;

import lombok.Data;

/**
 * Request payload for updating cash registers.
 *
 * @author ERP Cashier Team
 * @since 2025-01-02
 */
@Data
public class UpdateCashRegisterRequest {
    private String ipAddress;
    private String macAddress;
    private String neighborhood;
    private String town;
    private String country;
    private String minOpenTime;
    private String maxCloseTime;
    private String saleAgentBankAccount;
    private String saleAgentAccountingAccount;
    private Boolean isActive;

    /**
     * Default constructor for JSON serialization.
     */
    public UpdateCashRegisterRequest() {
    }
}
