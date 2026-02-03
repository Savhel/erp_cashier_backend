package com.erp.cashier.dto;

import java.util.List;
import lombok.Data;

/**
 * Response payload for creating a customer.
 *
 * @author ERP Cashier Team
 * @since 2025-01-20
 */
@Data
public class CreateCustomerResponse {
    private String id;
    private PersonDetailResponse person;
    private List<AccountResponse> accounts;

    /**
     * Default constructor for JSON serialization.
     */
    public CreateCustomerResponse() {
    }

    /**
     * Creates a customer response.
     *
     * @param id customer identifier
     * @param person person payload
     * @param accounts accounts list
     */
    public CreateCustomerResponse(String id, PersonDetailResponse person, List<AccountResponse> accounts) {
        this.id = id;
        this.person = person;
        this.accounts = accounts;
    }
}
