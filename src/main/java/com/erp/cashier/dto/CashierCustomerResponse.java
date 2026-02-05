package com.erp.cashier.dto;

import java.util.List;
import lombok.Data;

/**
 * Customer response payload for cashier endpoints.
 *
 * @author ERP Cashier Team
 * @since 2026-02-04
 */
@Data
public class CashierCustomerResponse {
    private String id;
    private CashierPersonResponse person;
    private List<CashierAccountSummaryResponse> accounts;
    private Double totalBalance;
    private int accountsCount;

    /**
     * Default constructor for JSON serialization.
     */
    public CashierCustomerResponse() {
    }

    /**
     * Creates a cashier customer response.
     *
     * @param id customer identifier
     * @param person person payload
     * @param accounts accounts list
     * @param totalBalance total balance
     * @param accountsCount accounts count
     */
    public CashierCustomerResponse(
            String id,
            CashierPersonResponse person,
            List<CashierAccountSummaryResponse> accounts,
            Double totalBalance,
            int accountsCount
    ) {
        this.id = id;
        this.person = person;
        this.accounts = accounts;
        this.totalBalance = totalBalance;
        this.accountsCount = accountsCount;
    }
}
