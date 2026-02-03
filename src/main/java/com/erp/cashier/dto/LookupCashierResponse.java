package com.erp.cashier.dto;

import lombok.Data;

/**
 * Lookup response payload for cashiers.
 *
 * @author ERP Cashier Team
 * @since 2025-01-20
 */
@Data
public class LookupCashierResponse {
    private String id;
    private String userName;
    private String userFirstName;
    private String accountNumber;
    private String country;
    private String mail;
    private String phone;
    private String password;
    private String source;

    /**
     * Default constructor for JSON serialization.
     */
    public LookupCashierResponse() {
    }

    /**
     * Creates a lookup cashier response.
     *
     * @param id cashier identifier
     * @param userName username
     * @param userFirstName full name
     * @param accountNumber account number
     * @param country country
     * @param mail email
     * @param phone phone
     * @param password password
     * @param source source
     */
    public LookupCashierResponse(
            String id,
            String userName,
            String userFirstName,
            String accountNumber,
            String country,
            String mail,
            String phone,
            String password,
            String source
    ) {
        this.id = id;
        this.userName = userName;
        this.userFirstName = userFirstName;
        this.accountNumber = accountNumber;
        this.country = country;
        this.mail = mail;
        this.phone = phone;
        this.password = password;
        this.source = source;
    }
}
