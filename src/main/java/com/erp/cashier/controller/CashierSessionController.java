package com.erp.cashier.controller;

import com.erp.cashier.dto.SessionResponse;
import com.erp.cashier.security.JwtPayload;
import com.erp.cashier.service.SessionAdminService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

/**
 * Cashier session endpoints.
 *
 * @author ERP Cashier Team
 * @since 2025-01-20
 */
@RestController
@RequestMapping("/api/cashier/sessions")
public class CashierSessionController {
    private final SessionAdminService sessionAdminService;

    /**
     * Creates the cashier session controller.
     *
     * @param sessionAdminService session admin service
     */
    public CashierSessionController(SessionAdminService sessionAdminService) {
        this.sessionAdminService = sessionAdminService;
    }

    /**
     * Lists sessions for the authenticated cashier.
     *
     * @param authentication authentication payload
     * @return sessions
     */
    @GetMapping
    @PreAuthorize("hasAuthority('ROLE_CASHIER')")
    public Flux<SessionResponse> listCashierSessions(Authentication authentication) {
        return sessionAdminService.listSessionsByCashier(
                resolveUserId(authentication),
                resolveOrganizationId(authentication),
                resolveAgencyId(authentication)
        );
    }

    private String resolveUserId(Authentication authentication) {
        if (authentication == null) {
            return null;
        }
        Object details = authentication.getDetails();
        if (details instanceof JwtPayload payload) {
            return payload.getUserId();
        }
        return null;
    }

    private String resolveOrganizationId(Authentication authentication) {
        if (authentication == null) {
            return null;
        }
        Object details = authentication.getDetails();
        if (details instanceof JwtPayload payload) {
            return payload.getOrganizationId();
        }
        return null;
    }

    private String resolveAgencyId(Authentication authentication) {
        if (authentication == null) {
            return null;
        }
        Object details = authentication.getDetails();
        if (details instanceof JwtPayload payload) {
            return payload.getAgencyId();
        }
        return null;
    }
}
