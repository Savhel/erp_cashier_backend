package com.erp.cashier.dto;

import lombok.Data;

/**
 * Response payload for locking or unlocking a session.
 *
 * @author ERP Cashier Team
 * @since 2025-01-20
 */
@Data
public class SessionLockResponse {
    private boolean success;
    private String message;
    private SessionResponse session;

    /**
     * Default constructor for JSON serialization.
     */
    public SessionLockResponse() {
    }

    /**
     * Creates a lock response.
     *
     * @param success success flag
     * @param message message
     * @param session session payload
     */
    public SessionLockResponse(boolean success, String message, SessionResponse session) {
        this.success = success;
        this.message = message;
        this.session = session;
    }
}
