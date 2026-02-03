package com.erp.cashier.dto;

import lombok.Data;

/**
 * Request payload for creating a cashier.
 *
 * @author ERP Cashier Team
 * @since 2025-01-02
 */
@Data
public class CreateCashierRequest {
    private String userName;
    private String userFirstName;
    private String password;
    private String townListChosen;
    private String workTown;
    private String hireDate;
    private String mail;
    private String accountNumber;
    private String country;
    private String phone;

    /**
     * Default constructor for JSON serialization.
     */
    public CreateCashierRequest() {
    }
}
