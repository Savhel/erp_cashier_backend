package com.erp.cashier.controller;

import com.erp.cashier.dto.DashboardStatsResponse;
import com.erp.cashier.security.JwtPayload;
import com.erp.cashier.service.DashboardService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * Dashboard endpoints.
 *
 * @author ERP Cashier Team
 * @since 2025-01-20
 */
@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {
    private final DashboardService dashboardService;

    /**
     * Creates the dashboard controller.
     *
     * @param dashboardService dashboard service
     */
    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    /**
     * Returns dashboard stats for the authenticated user.
     *
     * @param authentication authentication payload
     * @return dashboard stats
     */
    @GetMapping("/stats")
    @PreAuthorize("isAuthenticated()")
    public Mono<DashboardStatsResponse> getStats(Authentication authentication) {
        return dashboardService.getStats(resolvePayload(authentication));
    }

    /**
     * Returns dashboard stats for the authenticated user.
     *
     * @param authentication authentication payload
     * @return dashboard stats
     */
    @GetMapping("/stat")
    @PreAuthorize("isAuthenticated()")
    public Mono<DashboardStatsResponse> getStat(Authentication authentication) {
        return dashboardService.getStats(resolvePayload(authentication));
    }

    private JwtPayload resolvePayload(Authentication authentication) {
        if (authentication == null) {
            return null;
        }
        Object details = authentication.getDetails();
        if (details instanceof JwtPayload payload) {
            return payload;
        }
        return null;
    }
}
