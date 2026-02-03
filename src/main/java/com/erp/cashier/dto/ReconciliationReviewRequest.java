package com.erp.cashier.dto;

import lombok.Data;

/**
 * Request payload for reconciliation review.
 *
 * @author ERP Cashier Team
 * @since 2025-01-20
 */
@Data
public class ReconciliationReviewRequest {
    private String action;
    private String adminComment;

    /**
     * Default constructor for JSON serialization.
     */
    public ReconciliationReviewRequest() {
    }
}
