package com.erp.cashier.dto;

import lombok.Data;

/**
 * Request payload for creating cash registers.
 *
 * @author ERP Cashier Team
 * @since 2025-01-02
 */
@Data
public class CreateCashRegisterRequest {
    private String createBy;
    private String adress;
    private String country;
    private String town;
    private String neighborhood;
    private String agencyId;
    private String ipAddress;
    private String macAddress;
    private String imageUrl;
    private String minOpenTime;
    private String maxCloseTime;
    private String saleAgentBankAccount;
    private String saleAgentAccountingAccount;
    private Boolean isActive;

    /**
     * Default constructor for JSON serialization.
     */
    public CreateCashRegisterRequest() {
    }
}
