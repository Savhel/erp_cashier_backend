package com.erp.cashier.dto;

import java.time.LocalDateTime;
import lombok.Data;

/**
 * Cashier agency assignment response payload.
 *
 * @author ERP Cashier Team
 * @since 2025-01-20
 */
@Data
public class CashierAgencyAssignmentResponse {
    private String id;
    private PersonSummaryResponse cashier;
    private AgencySummaryResponse agency;
    private LocalDateTime startOn;
    private LocalDateTime endOn;
    private LocalDateTime assignedOn;

    /**
     * Default constructor for JSON serialization.
     */
    public CashierAgencyAssignmentResponse() {
    }

    /**
     * Creates a cashier agency assignment response.
     *
     * @param id assignment identifier
     * @param cashier cashier summary
     * @param agency agency summary
     * @param startOn start date
     * @param endOn end date
     * @param assignedOn assigned date
     */
    public CashierAgencyAssignmentResponse(
            String id,
            PersonSummaryResponse cashier,
            AgencySummaryResponse agency,
            LocalDateTime startOn,
            LocalDateTime endOn,
            LocalDateTime assignedOn
    ) {
        this.id = id;
        this.cashier = cashier;
        this.agency = agency;
        this.startOn = startOn;
        this.endOn = endOn;
        this.assignedOn = assignedOn;
    }
}
