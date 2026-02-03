package com.erp.cashier.dto;

import java.time.LocalDateTime;
import lombok.Data;

/**
 * Cashier profile response payload.
 *
 * @author ERP Cashier Team
 * @since 2025-01-02
 */
@Data
public class CashierProfileResponse {
    private String townListChosen;
    private String workTown;
    private LocalDateTime hireDate;

    /**
     * Default constructor for JSON serialization.
     */
    public CashierProfileResponse() {
    }

    /**
     * Creates a cashier profile response.
     *
     * @param townListChosen town list payload
     * @param workTown work town
     * @param hireDate hire date
     */
    public CashierProfileResponse(
            String townListChosen,
            String workTown,
            LocalDateTime hireDate
    ) {
        this.townListChosen = townListChosen;
        this.workTown = workTown;
        this.hireDate = hireDate;
    }
}
