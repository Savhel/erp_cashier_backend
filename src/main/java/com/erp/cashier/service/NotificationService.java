package com.erp.cashier.service;

import com.erp.cashier.dto.ForumResponse;
import com.erp.cashier.dto.NewsletterResponse;
import com.erp.cashier.dto.NotificationTestRequest;
import com.erp.cashier.dto.NotificationsResponse;
import com.erp.cashier.dto.OkResponse;
import java.util.Collections;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * Service for notifications.
 *
 * @author ERP Cashier Team
 * @since 2025-01-20
 */
@Service
public class NotificationService {
    /**
     * Returns notifications for the current user.
     *
     * @return notifications
     */
    public Mono<NotificationsResponse> getNotifications() {
        return Mono.just(new NotificationsResponse(
                Collections.<NewsletterResponse>emptyList(),
                Collections.<ForumResponse>emptyList()
        ));
    }

    /**
     * Tests a notification target.
     *
     * @param request test request
     * @return ok response
     */
    public Mono<OkResponse> testNotification(NotificationTestRequest request) {
        return Mono.just(new OkResponse(true));
    }
}
