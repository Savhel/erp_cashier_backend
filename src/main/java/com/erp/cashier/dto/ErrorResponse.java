package com.erp.cashier.dto;

import lombok.Data;

/**
 * Error response payload.
 *
 * @author ERP Cashier Team
 * @since 2025-01-02
 */
@Data
public class ErrorResponse {
    private String error;

    /**
     * Default constructor for JSON serialization.
     */
    public ErrorResponse() {
    }

    /**
     * Creates an error response.
     *
     * @param error error message
     */
    public ErrorResponse(String error) {
        this.error = error;
    }
}
