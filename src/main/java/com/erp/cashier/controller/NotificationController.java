package com.erp.cashier.controller;

import com.erp.cashier.dto.NotificationTestRequest;
import com.erp.cashier.dto.NotificationsResponse;
import com.erp.cashier.dto.OkResponse;
import com.erp.cashier.service.NotificationService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * Notification endpoints.
 *
 * @author ERP Cashier Team
 * @since 2025-01-20
 */
@RestController
@RequestMapping("/api/notifications")
public class NotificationController {
    private final NotificationService notificationService;

    /**
     * Creates the notification controller.
     *
     * @param notificationService notification service
     */
    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    /**
     * Returns notifications for the authenticated user.
     *
     * @return notifications
     */
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public Mono<NotificationsResponse> getNotifications() {
        return notificationService.getNotifications();
    }

    /**
     * Tests a notification target.
     *
     * @param request test request
     * @return ok response
     */
    @PostMapping("/test")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public Mono<OkResponse> testNotification(@RequestBody NotificationTestRequest request) {
        return notificationService.testNotification(request);
    }
}
