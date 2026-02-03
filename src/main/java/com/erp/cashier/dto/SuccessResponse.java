package com.erp.cashier.dto;

import lombok.Data;

/**
 * Simple success response payload.
 *
 * @author ERP Cashier Team
 * @since 2025-01-02
 */
@Data
public class SuccessResponse {
    private boolean success;

    /**
     * Default constructor for JSON serialization.
     */
    public SuccessResponse() {
    }

    /**
     * Creates a success response.
     *
     * @param success success flag
     */
    public SuccessResponse(boolean success) {
        this.success = success;
    }
}
