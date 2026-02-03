package com.erp.cashier.dto;

import java.util.List;
import lombok.Data;

/**
 * Customer response payload.
 *
 * @author ERP Cashier Team
 * @since 2025-01-20
 */
@Data
public class CustomerResponse {
    private String id;
    private PersonDetailResponse person;
    private String phone;
    private List<AccountResponse> accounts;
    private Double totalBalance;
    private int accountsCount;

    /**
     * Default constructor for JSON serialization.
     */
    public CustomerResponse() {
    }

    /**
     * Creates a customer response.
     *
     * @param id customer identifier
     * @param person person payload
     * @param phone phone
     * @param accounts accounts list
     * @param totalBalance total balance
     * @param accountsCount accounts count
     */
    public CustomerResponse(
            String id,
            PersonDetailResponse person,
            String phone,
            List<AccountResponse> accounts,
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
