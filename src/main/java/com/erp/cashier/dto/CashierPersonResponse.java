package com.erp.cashier.dto;

import lombok.Data;

/**
 * Person payload for cashier responses.
 *
 * @author ERP Cashier Team
 * @since 2026-02-04
 */
@Data
public class CashierPersonResponse {
    private String id;
    private String userName;
    private String userFirstName;
    private String phone;
    private String mail;
    private String country;

    /**
     * Default constructor for JSON serialization.
     */
    public CashierPersonResponse() {
    }

    /**
     * Creates a cashier person response.
     *
     * @param id person identifier
     * @param userName username
     * @param userFirstName display name
     * @param phone phone number
     * @param mail email
     * @param country country
     */
    public CashierPersonResponse(
            String id,
            String userName,
            String userFirstName,
            String phone,
            String mail,
            String country
    ) {
        this.id = id;
        this.userName = userName;
        this.userFirstName = userFirstName;
        this.phone = phone;
        this.mail = mail;
        this.country = country;
    }
}
