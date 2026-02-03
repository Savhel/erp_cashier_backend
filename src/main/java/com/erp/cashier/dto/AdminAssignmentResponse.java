package com.erp.cashier.dto;

import java.time.LocalDateTime;
import lombok.Data;

/**
 * Admin assignment response payload.
 *
 * @author ERP Cashier Team
 * @since 2025-01-20
 */
@Data
public class AdminAssignmentResponse {
    private String id;
    private LocalDateTime day;
    private PersonSummaryResponse person;
    private CashRegisterSummaryResponse cashRegister;

    /**
     * Default constructor for JSON serialization.
     */
    public AdminAssignmentResponse() {
    }

    /**
     * Creates an admin assignment response.
     *
     * @param id assignment identifier
     * @param day assignment day
     * @param person person summary
     * @param cashRegister cash register summary
     */
    public AdminAssignmentResponse(
            String id,
            LocalDateTime day,
            PersonSummaryResponse person,
            CashRegisterSummaryResponse cashRegister
    ) {
        this.id = id;
        this.day = day;
        this.person = person;
        this.cashRegister = cashRegister;
    }
}
