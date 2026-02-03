package com.erp.cashier.dto;

import java.time.LocalDateTime;
import java.util.List;
import lombok.Data;

/**
 * Cash register response payload.
 *
 * @author ERP Cashier Team
 * @since 2025-01-02
 */
@Data
public class CashRegisterResponse {
    private String id;
    private String town;
    private String country;
    private String neighborhood;
    private String adress;
    private LocalDateTime createOn;
    private String ipAddress;
    private String macAddress;
    private String minOpenTime;
    private String maxCloseTime;
    private String saleAgentBankAccount;
    private String saleAgentAccountingAccount;
    private Boolean isActive;
    private CashRegisterAgencyResponse agency;
    private CashRegisterAssignedCashierResponse assignedCashier;
    private List<CashRegisterSessionSummaryResponse> sessions;

    /**
     * Default constructor for JSON serialization.
     */
    public CashRegisterResponse() {
    }

    /**
     * Creates a cash register response.
     *
     * @param id register identifier
     * @param town register town
     * @param country register country
     * @param neighborhood register neighborhood
     * @param adress register address
     * @param createOn creation timestamp
     * @param ipAddress IP address
     * @param macAddress MAC address
     * @param minOpenTime min open time
     * @param maxCloseTime max close time
     * @param saleAgentBankAccount sale agent bank account
     * @param saleAgentAccountingAccount sale agent accounting account
     * @param isActive active flag
     * @param agency agency summary
     * @param assignedCashier assigned cashier
     * @param sessions session summary list
     */
    public CashRegisterResponse(
            String id,
            String town,
            String country,
            String neighborhood,
            String adress,
            LocalDateTime createOn,
            String ipAddress,
            String macAddress,
            String minOpenTime,
            String maxCloseTime,
            String saleAgentBankAccount,
            String saleAgentAccountingAccount,
            Boolean isActive,
            CashRegisterAgencyResponse agency,
            CashRegisterAssignedCashierResponse assignedCashier,
            List<CashRegisterSessionSummaryResponse> sessions
    ) {
        this.id = id;
        this.town = town;
        this.country = country;
        this.neighborhood = neighborhood;
        this.adress = adress;
        this.createOn = createOn;
        this.ipAddress = ipAddress;
        this.macAddress = macAddress;
        this.minOpenTime = minOpenTime;
        this.maxCloseTime = maxCloseTime;
        this.saleAgentBankAccount = saleAgentBankAccount;
        this.saleAgentAccountingAccount = saleAgentAccountingAccount;
        this.isActive = isActive;
        this.agency = agency;
        this.assignedCashier = assignedCashier;
        this.sessions = sessions;
    }
}
