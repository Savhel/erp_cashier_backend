package com.erp.cashier.dto;

import java.util.List;
import lombok.Data;

/**
 * Notifications payload.
 *
 * @author ERP Cashier Team
 * @since 2025-01-20
 */
@Data
public class NotificationsResponse {
    private List<NewsletterResponse> newsletters;
    private List<ForumResponse> forums;

    /**
     * Default constructor for JSON serialization.
     */
    public NotificationsResponse() {
    }

    /**
     * Creates a notifications response.
     *
     * @param newsletters newsletters list
     * @param forums forums list
     */
    public NotificationsResponse(List<NewsletterResponse> newsletters, List<ForumResponse> forums) {
        this.newsletters = newsletters;
        this.forums = forums;
    }
}
