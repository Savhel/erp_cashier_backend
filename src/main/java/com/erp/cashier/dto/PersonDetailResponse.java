package com.erp.cashier.dto;

import lombok.Data;

/**
 * Person detail payload.
 *
 * @author ERP Cashier Team
 * @since 2025-01-20
 */
@Data
public class PersonDetailResponse {
    private String id;
    private String userName;
    private String userFirstName;
    private String mail;
    private String country;
    private String phone;
    private String accountNumber;

    /**
     * Default constructor for JSON serialization.
     */
    public PersonDetailResponse() {
    }

    /**
     * Creates a person detail response.
     *
     * @param id person identifier
     * @param userName username
     * @param userFirstName full name
     * @param mail email
     * @param country country
     * @param phone phone
     * @param accountNumber account number
     */
    public PersonDetailResponse(
            String id,
            String userName,
            String userFirstName,
            String mail,
            String country,
            String phone,
            String accountNumber
    ) {
        this.id = id;
        this.userName = userName;
        this.userFirstName = userFirstName;
        this.mail = mail;
        this.country = country;
        this.phone = phone;
        this.accountNumber = accountNumber;
    }
}
