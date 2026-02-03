package com.erp.cashier.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * Cashier response with profile details.
 *
 * @author ERP Cashier Team
 * @since 2026-02-01
 */
@Data
public class CashierWithProfileResponse {
    private String id;
    private String userName;
    private String userFirstName;
    private String phone;
    private String accountNumber;
    private String country;

    @JsonProperty("cashierProfile")
    private CashierProfileDetailResponse cashierProfile;

    /**
     * Default constructor for JSON serialization.
     */
    public CashierWithProfileResponse() {
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
    public CashierWithProfileResponse(
            String id,
            String userName,
            String userFirstName,
            String phone,
            String accountNumber,
            String country,
            CashierProfileDetailResponse cashierProfile
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
