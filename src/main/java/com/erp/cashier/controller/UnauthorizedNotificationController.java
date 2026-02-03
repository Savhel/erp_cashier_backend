package com.erp.cashier.controller;

import com.erp.cashier.dto.NotifyUnauthorizedRequest;
import com.erp.cashier.dto.OkResponse;
import com.erp.cashier.service.AuditService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * Unauthorized access notification endpoint.
 *
 * @author ERP Cashier Team
 * @since 2025-01-20
 */
@RestController
@RequestMapping("/api/notify-unauthorized")
public class UnauthorizedNotificationController {
    private final AuditService auditService;

    /**
     * Creates the unauthorized notification controller.
     *
     * @param auditService audit service
     */
    public UnauthorizedNotificationController(AuditService auditService) {
        this.auditService = auditService;
    }

    /**
     * Records an unauthorized access event.
     *
     * @param request unauthorized request
     * @return ok response
     */
    @PostMapping
    @PreAuthorize("permitAll()")
    public Mono<OkResponse> notifyUnauthorized(@RequestBody NotifyUnauthorizedRequest request) {
        return auditService.recordUnauthorized(request)
                .thenReturn(new OkResponse(true));
    }
}
