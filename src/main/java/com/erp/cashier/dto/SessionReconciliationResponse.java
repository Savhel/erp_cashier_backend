package com.erp.cashier.dto;

import java.math.BigDecimal;
import lombok.Data;

/**
 * Reconciliation payload for session responses.
 *
 * @author ERP Cashier Team
 * @since 2025-01-02
 */
@Data
public class SessionReconciliationResponse {
    private String id;
    private BigDecimal theoricalTotal;
    private BigDecimal physicalTotal;
    private BigDecimal difference;

    /**
     * Default constructor for JSON serialization.
     */
    public SessionReconciliationResponse() {
    }

    /**
     * Creates a reconciliation response.
     *
     * @param id reconciliation identifier
     * @param theoricalTotal theorical total
     * @param physicalTotal physical total
     * @param difference difference
     */
    public SessionReconciliationResponse(
            String id,
            BigDecimal theoricalTotal,
            BigDecimal physicalTotal,
            BigDecimal difference
    ) {
        this.id = id;
        this.theoricalTotal = theoricalTotal;
        this.physicalTotal = physicalTotal;
        this.difference = difference;
    }
}
