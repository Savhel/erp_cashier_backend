package com.erp.cashier.dto;

import lombok.Data;

/**
 * Reconciliation user payload.
 *
 * @author ERP Cashier Team
 * @since 2025-01-20
 */
@Data
public class ReconciliationUserResponse {
    private String id;
    private String userName;
    private String userFirstName;

    /**
     * Default constructor for JSON serialization.
     */
    public ReconciliationUserResponse() {
    }

    public ReconciliationUserResponse(String id, String userName, String userFirstName) {
        this.id = id;
        this.userName = userName;
        this.userFirstName = userFirstName;
    }
}
