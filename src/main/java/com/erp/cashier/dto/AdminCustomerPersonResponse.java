package com.erp.cashier.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * Customer person summary for admin endpoints.
 *
 * @author ERP Cashier Team
 * @since 2026-02-03
 */
@Data
public class AdminCustomerPersonResponse {
    @JsonProperty("id")
    private String id;

    @JsonProperty("user_name")
    private String userName;

    @JsonProperty("user_first_name")
    private String userFirstName;

    @JsonProperty("phone")
    private String phone;

    @JsonProperty("mail")
    private String mail;

    @JsonProperty("country")
    private String country;

    /**
     * Default constructor for JSON serialization.
     */
    public AdminCustomerPersonResponse() {
    }

    /**
     * Creates a customer person response.
     *
     * @param id person identifier
     * @param userName username
     * @param userFirstName display name
     * @param phone phone number
     * @param mail email
     * @param country country
     */
    public AdminCustomerPersonResponse(
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
