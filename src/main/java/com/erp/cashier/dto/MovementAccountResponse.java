package com.erp.cashier.dto;

import lombok.Data;

/**
 * Response payload for movement accounting.
 *
 * @author ERP Cashier Team
 * @since 2025-01-20
 */
@Data
public class MovementAccountResponse {
    private boolean success;
    private MovementResponse movement;

    /**
     * Default constructor for JSON serialization.
     */
    public MovementAccountResponse() {
    }

    public MovementAccountResponse(boolean success, MovementResponse movement) {
        this.success = success;
        this.movement = movement;
    }
}
