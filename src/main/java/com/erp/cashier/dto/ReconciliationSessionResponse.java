package com.erp.cashier.dto;

import java.time.LocalDateTime;
import lombok.Data;

/**
 * Reconciliation session payload.
 *
 * @author ERP Cashier Team
 * @since 2025-01-20
 */
@Data
public class ReconciliationSessionResponse {
    private String id;
    private String state;
    private LocalDateTime openOn;
    private LocalDateTime closeOn;

    /**
     * Default constructor for JSON serialization.
     */
    public ReconciliationSessionResponse() {
    }

    public ReconciliationSessionResponse(String id, String state, LocalDateTime openOn, LocalDateTime closeOn) {
        this.id = id;
        this.state = state;
        this.openOn = openOn;
        this.closeOn = closeOn;
    }
}
