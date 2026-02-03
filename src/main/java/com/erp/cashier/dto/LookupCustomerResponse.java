package com.erp.cashier.dto;

import lombok.Data;

/**
 * Lookup response payload for customers.
 *
 * @author ERP Cashier Team
 * @since 2025-01-20
 */
@Data
public class LookupCustomerResponse {
    private String phone;
    private String userName;
    private String userFirstName;
    private String mail;
    private String country;
    private String profession;
    private String source;

    /**
     * Default constructor for JSON serialization.
     */
    public LookupCustomerResponse() {
    }

    /**
     * Creates a lookup customer response.
     *
     * @param phone phone
     * @param userName username
     * @param userFirstName full name
     * @param mail email
     * @param country country
     * @param profession profession
     * @param source source
     */
    public LookupCustomerResponse(
            String phone,
            String userName,
            String userFirstName,
            String mail,
            String country,
            String profession,
            String source
    ) {
        this.phone = phone;
        this.userName = userName;
        this.userFirstName = userFirstName;
        this.mail = mail;
        this.country = country;
        this.profession = profession;
        this.source = source;
    }
}
