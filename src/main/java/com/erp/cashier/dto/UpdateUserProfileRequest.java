package com.erp.cashier.dto;

import lombok.Data;

/**
 * Request payload for updating the current user profile.
 *
 * @author ERP Cashier Team
 * @since 2025-01-20
 */
@Data
public class UpdateUserProfileRequest {
    private String telegramChatId;
    private String telegramBotToken;

    /**
     * Default constructor for JSON serialization.
     */
    public UpdateUserProfileRequest() {
    }
}
