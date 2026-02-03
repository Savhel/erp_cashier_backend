package com.erp.cashier.dto;

import lombok.Data;

/**
 * Reconciliation response payload.
 *
 * @author ERP Cashier Team
 * @since 2025-01-20
 */
@Data
public class ReconciliationResponse {
    private ReconciliationInfoResponse reconciliation;
    private ReconciliationSessionResponse session;
    private CashRegisterSummaryResponse cashRegister;
    private ReconciliationUserResponse opener;
    private ReconciliationUserResponse closer;
    private ReconciliationUserResponse creator;

    /**
     * Default constructor for JSON serialization.
     */
    public ReconciliationResponse() {
    }

    public ReconciliationResponse(
            ReconciliationInfoResponse reconciliation,
            ReconciliationSessionResponse session,
            CashRegisterSummaryResponse cashRegister,
            ReconciliationUserResponse opener,
            ReconciliationUserResponse closer,
            ReconciliationUserResponse creator
    ) {
        this.reconciliation = reconciliation;
        this.session = session;
        this.cashRegister = cashRegister;
        this.opener = opener;
        this.closer = closer;
        this.creator = creator;
    }
}
