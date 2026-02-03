package com.erp.cashier.dto;

import lombok.Data;

/**
 * Request payload for reconciliation justification.
 *
 * @author ERP Cashier Team
 * @since 2025-01-20
 */
@Data
public class ReconciliationJustifyRequest {
    private String justification;

    /**
     * Default constructor for JSON serialization.
     */
    public ReconciliationJustifyRequest() {
    }
}
