package com.erp.cashier.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * Reconciliation info payload.
 *
 * @author ERP Cashier Team
 * @since 2025-01-20
 */
@Data
public class ReconciliationInfoResponse {
    private String id;
    private BigDecimal physicalTotal;
    private BigDecimal theoricalTotal;
    private BigDecimal difference;
    private String statut;
    private String justification;
    private LocalDateTime createOn;
    private LocalDateTime checkOn;

    /**
     * Default constructor for JSON serialization.
     */
    public ReconciliationInfoResponse() {
    }

    public ReconciliationInfoResponse(
            String id,
            BigDecimal physicalTotal,
            BigDecimal theoricalTotal,
            BigDecimal difference,
            String statut,
            String justification,
            LocalDateTime createOn,
            LocalDateTime checkOn
    ) {
        this.id = id;
        this.physicalTotal = physicalTotal;
        this.theoricalTotal = theoricalTotal;
        this.difference = difference;
        this.statut = statut;
        this.justification = justification;
        this.createOn = createOn;
        this.checkOn = checkOn;
    }
}
