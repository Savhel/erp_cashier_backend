package com.erp.cashier.dto;

import lombok.Data;

/**
 * Customer wrapper for cashier account responses.
 *
 * @author ERP Cashier Team
 * @since 2026-02-04
 */
@Data
public class CashierAccountCustomerResponse {
    private String id;
    private CashierPersonResponse person;

    /**
     * Default constructor for JSON serialization.
     */
    public CashierAccountCustomerResponse() {
    }

    /**
     * Creates a cashier account customer response.
     *
     * @param id customer identifier
     * @param person person payload
     */
    public CashierAccountCustomerResponse(String id, CashierPersonResponse person) {
        this.id = id;
        this.person = person;
    }
}
