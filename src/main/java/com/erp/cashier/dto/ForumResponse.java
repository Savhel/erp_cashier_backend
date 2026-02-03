package com.erp.cashier.dto;

import java.util.List;
import lombok.Data;

/**
 * Forum payload.
 *
 * @author ERP Cashier Team
 * @since 2025-01-20
 */
@Data
public class ForumResponse {
    private String id;
    private String title;
    private List<ForumMessageResponse> messages;

    /**
     * Default constructor for JSON serialization.
     */
    public ForumResponse() {
    }

    /**
     * Creates a forum response.
     *
     * @param id forum identifier
     * @param title title
     * @param messages messages list
     */
    public ForumResponse(String id, String title, List<ForumMessageResponse> messages) {
        this.id = id;
        this.title = title;
        this.messages = messages;
    }
}
