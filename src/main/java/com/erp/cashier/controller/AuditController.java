package com.erp.cashier.controller;

import com.erp.cashier.dto.AuditLogResponse;
import com.erp.cashier.dto.AuditRequest;
import com.erp.cashier.dto.SuccessResponse;
import com.erp.cashier.security.JwtPayload;
import com.erp.cashier.service.AuditService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Audit endpoints.
 *
 * @author ERP Cashier Team
 * @since 2025-01-20
 */
@RestController
@RequestMapping("/api/audit")
public class AuditController {
    private final AuditService auditService;

    /**
     * Creates the audit controller.
     *
     * @param auditService audit service
     */
    public AuditController(AuditService auditService) {
        this.auditService = auditService;
    }

    /**
     * Returns audit logs.
     *
     * @param limit optional limit
     * @param agencyId optional agency filter
     * @param authentication authentication payload
     * @return audit logs
     */
    @GetMapping
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public Flux<AuditLogResponse> listAudit(
            @RequestParam(value = "limit", required = false) Integer limit,
            @RequestParam(value = "agencyId", required = false) String agencyId,
            Authentication authentication
    ) {
        JwtPayload payload = resolvePayload(authentication);
        String organizationId = payload != null ? payload.getOrganizationId() : null;
        String scopedAgencyId = payload != null ? payload.getAgencyId() : null;
        String effectiveAgencyId = scopedAgencyId != null ? scopedAgencyId : agencyId;
        return auditService.listAudit(limit, organizationId, effectiveAgencyId);
    }

    /**
     * Records a new audit entry.
     *
     * @param request audit request
     * @param authentication authentication payload
     * @return success response
     */
    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public Mono<SuccessResponse> createAudit(
            @RequestBody AuditRequest request,
            Authentication authentication
    ) {
        return auditService.recordAudit(request, resolveUserId(authentication))
                .thenReturn(new SuccessResponse(true));
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
