package com.erp.cashier.dto;

import java.math.BigDecimal;
import lombok.Data;

/**
 * Cash reconciliation response payload for sessions.
 *
 * @author ERP Cashier Team
 * @since 2025-01-02
 */
@Data
public class CashRegisterReconciliationResponse {
    private BigDecimal theoricalTotal;
    private BigDecimal physicalTotal;
    private BigDecimal difference;
    private String justification;

    /**
     * Default constructor for JSON serialization.
     */
    public CashRegisterReconciliationResponse() {
    }

    /**
     * Creates a reconciliation response.
     *
     * @param theoricalTotal theorical total
     * @param physicalTotal physical total
     * @param difference difference
     * @param justification justification
     */
    public CashRegisterReconciliationResponse(
            BigDecimal theoricalTotal,
            BigDecimal physicalTotal,
            BigDecimal difference,
            String justification
    ) {
        this.theoricalTotal = theoricalTotal;
        this.physicalTotal = physicalTotal;
        this.difference = difference;
        this.justification = justification;
    }
}
