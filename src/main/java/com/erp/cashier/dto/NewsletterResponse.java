package com.erp.cashier.dto;

import lombok.Data;

/**
 * Newsletter payload.
 *
 * @author ERP Cashier Team
 * @since 2025-01-20
 */
@Data
public class NewsletterResponse {
    private String id;
    private String title;
    private String content;

    /**
     * Default constructor for JSON serialization.
     */
    public NewsletterResponse() {
    }

    /**
     * Creates a newsletter response.
     *
     * @param id newsletter identifier
     * @param title title
     * @param content content
     */
    public NewsletterResponse(String id, String title, String content) {
        this.id = id;
        this.title = title;
        this.content = content;
    }
}
