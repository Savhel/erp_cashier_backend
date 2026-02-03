package com.erp.cashier.dto;

import lombok.Data;

/**
 * Forum message payload.
 *
 * @author ERP Cashier Team
 * @since 2025-01-20
 */
@Data
public class ForumMessageResponse {
    private String id;
    private String content;

    /**
     * Default constructor for JSON serialization.
     */
    public ForumMessageResponse() {
    }

    /**
     * Creates a forum message response.
     *
     * @param id message identifier
     * @param content message content
     */
    public ForumMessageResponse(String id, String content) {
        this.id = id;
        this.content = content;
    }
}
