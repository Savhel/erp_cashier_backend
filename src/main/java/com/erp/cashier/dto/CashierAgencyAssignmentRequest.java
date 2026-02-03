package com.erp.cashier.dto;

import lombok.Data;

/**
 * Request payload for cashier agency assignments.
 *
 * @author ERP Cashier Team
 * @since 2025-01-20
 */
@Data
public class CashierAgencyAssignmentRequest {
    private String cashierId;
    private String agencyId;
    private String startOn;
    private String endOn;

    /**
     * Default constructor for JSON serialization.
     */
    public CashierAgencyAssignmentRequest() {
    }
}
