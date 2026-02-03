package com.erp.cashier.dto;

import lombok.Data;

/**
 * Simple message response payload.
 *
 * @author ERP Cashier Team
 * @since 2026-02-02
 */
@Data
public class MessageResponse {
    private String message;

    /**
     * Default constructor for JSON serialization.
     */
    public MessageResponse() {
    }

    /**
     * Creates a message response.
     *
     * @param message message content
     */
    public MessageResponse(String message) {
        this.message = message;
    }
}
