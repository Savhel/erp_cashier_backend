package com.erp.cashier.dto;

import lombok.Data;

/**
 * Response payload for the current user profile.
 *
 * @author ERP Cashier Team
 * @since 2025-01-20
 */
@Data
public class UserProfileResponse {
    private String id;
    private String userName;
    private String userFirstName;
    private String telegramChatId;
    private String telegramBotToken;

    /**
     * Default constructor for JSON serialization.
     */
    public UserProfileResponse() {
    }

    /**
     * Creates a user profile response.
     *
     * @param id user identifier
     * @param userName username
     * @param userFirstName user full name
     * @param telegramChatId telegram chat identifier
     * @param telegramBotToken telegram bot token
     */
    public UserProfileResponse(
            String id,
            String userName,
            String userFirstName,
            String telegramChatId,
            String telegramBotToken
    ) {
        this.id = id;
        this.userName = userName;
        this.userFirstName = userFirstName;
        this.telegramChatId = telegramChatId;
        this.telegramBotToken = telegramBotToken;
    }
}
