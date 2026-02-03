package com.erp.cashier.dto;

import lombok.Data;

/**
 * Request payload for creating a customer.
 *
 * @author ERP Cashier Team
 * @since 2025-01-20
 */
@Data
public class CreateCustomerRequest {
    private String phone;
    private String userFirstName;
    private String userName;
    private String mail;
    private String country;
    private String profession;
    private String accountNumber;
    private Double initialBalance;

    /**
     * Default constructor for JSON serialization.
     */
    public CreateCustomerRequest() {
    }
}
