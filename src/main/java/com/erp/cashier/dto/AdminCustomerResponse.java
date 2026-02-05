package com.erp.cashier.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.Data;

/**
 * Customer response payload for admin endpoints.
 *
 * @author ERP Cashier Team
 * @since 2026-02-03
 */
@Data
public class AdminCustomerResponse {
    @JsonProperty("id")
    private String id;

    @JsonProperty("person")
    private AdminCustomerPersonResponse person;

    @JsonProperty("phone")
    private String phone;

    @JsonProperty("accounts")
    private List<AdminCustomerAccountResponse> accounts;

    @JsonProperty("totalBalance")
    private Double totalBalance;

    @JsonProperty("accountsCount")
    private int accountsCount;

    /**
     * Default constructor for JSON serialization.
     */
    public AdminCustomerResponse() {
    }

    /**
     * Creates an admin customer response.
     *
     * @param id customer identifier
     * @param person person payload
     * @param phone phone number
     * @param accounts accounts list
     * @param totalBalance total balance
     * @param accountsCount accounts count
     */
    public AdminCustomerResponse(
            String id,
            AdminCustomerPersonResponse person,
            String phone,
            List<AdminCustomerAccountResponse> accounts,
            Double totalBalance,
            int accountsCount
    ) {
        this.id = id;
        this.person = person;
        this.phone = phone;
        this.accounts = accounts;
        this.totalBalance = totalBalance;
        this.accountsCount = accountsCount;
    }
}
