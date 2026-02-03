package com.erp.cashier.dto;

import lombok.Data;

/**
 * Cashier response payload.
 *
 * @author ERP Cashier Team
 * @since 2025-01-02
 */
@Data
public class CashierResponse {
    private String id;
    private String userName;
    private String userFirstName;
    private String phone;
    private String accountNumber;
    private String country;
    private CashierProfileResponse cashierProfile;

    /**
     * Default constructor for JSON serialization.
     */
    public CashierResponse() {
    }

    /**
     * Creates a cashier response.
     *
     * @param id cashier identifier
     * @param userName username
     * @param userFirstName full name
     * @param phone phone number
     * @param accountNumber account number
     * @param country country
     * @param cashierProfile cashier profile
     */
    public CashierResponse(
            String id,
            String userName,
            String userFirstName,
            String phone,
            String accountNumber,
            String country,
            CashierProfileResponse cashierProfile
    ) {
        this.id = id;
        this.userName = userName;
        this.userFirstName = userFirstName;
        this.phone = phone;
        this.accountNumber = accountNumber;
        this.country = country;
        this.cashierProfile = cashierProfile;
    }
}
