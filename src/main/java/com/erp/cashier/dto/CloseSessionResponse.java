package com.erp.cashier.dto;

import lombok.Data;

/**
 * Response payload for closing a session.
 *
 * @author ERP Cashier Team
 * @since 2025-01-20
 */
@Data
public class CloseSessionResponse {
    private boolean success;
    private String message;
    private CloseSessionReconciliationResponse reconciliation;

    /**
     * Default constructor for JSON serialization.
     */
    public CloseSessionResponse() {
    }

    /**
     * Creates a close session response.
     *
     * @param success success flag
     * @param message message
     * @param reconciliation reconciliation payload
     */
    public CloseSessionResponse(
            boolean success,
            String message,
            CloseSessionReconciliationResponse reconciliation
    ) {
        this.success = success;
        this.message = message;
        this.reconciliation = reconciliation;
    }

    /**
     * Reconciliation payload returned by close session.
     */
    @Data
    public static class CloseSessionReconciliationResponse {
        private SessionResponse sessionData;
        private SessionReconciliationResponse reconciliation;

        /**
         * Default constructor for JSON serialization.
         */
        public CloseSessionReconciliationResponse() {
        }

        /**
         * Creates a reconciliation response payload.
         *
         * @param sessionData session payload
         * @param reconciliation reconciliation payload
         */
        public CloseSessionReconciliationResponse(
                SessionResponse sessionData,
                SessionReconciliationResponse reconciliation
        ) {
            this.sessionData = sessionData;
            this.reconciliation = reconciliation;
        }
    }
}
