package com.erp.cashier.dto;

import lombok.Data;

/**
 * Request payload for updating a cashier.
 *
 * @author ERP Cashier Team
 * @since 2025-01-02
 */
@Data
public class UpdateCashierRequest {
    private String userName;
    private String userFirstName;
    private String country;
    private String townListChosen;
    private String workTown;
    private String hireDate;
    private String baseAgencyId;
    private String organizationId;

    /**
     * Default constructor for JSON serialization.
     */
    public UpdateCashierRequest() {
    }
}
