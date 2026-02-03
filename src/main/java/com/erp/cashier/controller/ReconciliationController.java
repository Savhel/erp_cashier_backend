package com.erp.cashier.controller;

import com.erp.cashier.dto.ReconciliationJustifyRequest;
import com.erp.cashier.dto.ReconciliationResponse;
import com.erp.cashier.dto.ReconciliationReviewRequest;
import com.erp.cashier.security.JwtPayload;
import com.erp.cashier.service.ReconciliationService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Reconciliation endpoints.
 *
 * @author ERP Cashier Team
 * @since 2025-01-20
 */
@RestController
@RequestMapping("/api")
public class ReconciliationController {
    private final ReconciliationService reconciliationService;

    /**
     * Creates the reconciliation controller.
     *
     * @param reconciliationService reconciliation service
     */
    public ReconciliationController(ReconciliationService reconciliationService) {
        this.reconciliationService = reconciliationService;
    }

    /**
     * Lists reconciliations for admins.
     *
     * @return reconciliations
     */
    @GetMapping("/admin/reconciliations")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public Flux<ReconciliationResponse> listAdminReconciliations() {
        return reconciliationService.listAdminReconciliations();
    }

    /**
     * Lists reconciliations for the authenticated cashier.
     *
     * @param authentication authentication payload
     * @return reconciliations
     */
    @GetMapping("/cashier/reconciliations")
    @PreAuthorize("hasAuthority('ROLE_CASHIER')")
    public Flux<ReconciliationResponse> listCashierReconciliations(Authentication authentication) {
        return reconciliationService.listCashierReconciliations(resolveUserId(authentication));
    }

    /**
     * Reviews a reconciliation.
     *
     * @param reconciliationId reconciliation identifier
     * @param request review request
     * @param authentication authentication payload
     * @return updated reconciliation
     */
    @PostMapping("/reconciliations/{id}/review")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public Mono<ReconciliationResponse> reviewReconciliation(
            @PathVariable("id") String reconciliationId,
            @RequestBody ReconciliationReviewRequest request,
            Authentication authentication
    ) {
        return reconciliationService.reviewReconciliation(reconciliationId, request, resolveUserId(authentication));
    }

    /**
     * Justifies a reconciliation.
     *
     * @param reconciliationId reconciliation identifier
     * @param request justify request
     * @return updated reconciliation
     */
    @PostMapping("/reconciliations/{id}/justify")
    @PreAuthorize("hasAuthority('ROLE_CASHIER')")
    public Mono<ReconciliationResponse> justifyReconciliation(
            @PathVariable("id") String reconciliationId,
            @RequestBody ReconciliationJustifyRequest request
    ) {
        return reconciliationService.justifyReconciliation(reconciliationId, request);
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
}
