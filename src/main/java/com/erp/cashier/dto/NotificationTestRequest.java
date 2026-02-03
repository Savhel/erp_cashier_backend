package com.erp.cashier.dto;

import lombok.Data;

/**
 * Request payload for testing notifications.
 *
 * @author ERP Cashier Team
 * @since 2025-01-20
 */
@Data
public class NotificationTestRequest {
    private String chatId;
    private String botToken;

    /**
     * Default constructor for JSON serialization.
     */
    public NotificationTestRequest() {
    }
}
