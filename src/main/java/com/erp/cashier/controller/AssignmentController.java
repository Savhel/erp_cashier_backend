package com.erp.cashier.controller;

import com.erp.cashier.dto.AdminAssignmentResponse;
import com.erp.cashier.dto.AssignmentDeleteRequest;
import com.erp.cashier.dto.CashierAgencyAssignmentRequest;
import com.erp.cashier.dto.CashierAgencyAssignmentResponse;
import com.erp.cashier.dto.SuccessResponse;
import com.erp.cashier.security.JwtPayload;
import com.erp.cashier.service.AssignmentService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Assignment endpoints for admin users.
 *
 * @author ERP Cashier Team
 * @since 2025-01-20
 */
@RestController
@RequestMapping("/api/admin")
public class AssignmentController {
    private final AssignmentService assignmentService;

    /**
     * Creates the assignment controller.
     *
     * @param assignmentService assignment service
     */
    public AssignmentController(AssignmentService assignmentService) {
        this.assignmentService = assignmentService;
    }

    /**
     * Lists register assignments.
     *
     * @param authentication authentication payload
     * @return assignments
     */
    @GetMapping("/assignments")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public Flux<AdminAssignmentResponse> listAssignments(Authentication authentication) {
        String organizationId = resolveOrganizationId(authentication);
        String agencyId = resolveAgencyId(authentication);
        boolean restrictToOrganization = StringUtils.hasText(organizationId);
        boolean restrictToAgency = StringUtils.hasText(agencyId);
        return assignmentService.listRegisterAssignments(
                organizationId,
                agencyId,
                restrictToOrganization,
                restrictToAgency
        );
    }

    /**
     * Lists cashier agency assignments.
     *
     * @param authentication authentication payload
     * @return assignments
     */
    @GetMapping("/cashier-agency-assignments")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public Flux<CashierAgencyAssignmentResponse> listCashierAgencyAssignments(Authentication authentication) {
        String organizationId = resolveOrganizationId(authentication);
        String agencyId = resolveAgencyId(authentication);
        boolean restrictToOrganization = StringUtils.hasText(organizationId);
        boolean restrictToAgency = StringUtils.hasText(agencyId);
        return assignmentService.listCashierAgencyAssignments(
                organizationId,
                agencyId,
                restrictToOrganization,
                restrictToAgency
        );
    }

    /**
     * Creates a cashier agency assignment.
     *
     * @param request create request
     * @param authentication authentication payload
     * @return created assignment
     */
    @PostMapping("/cashier-agency-assignments")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public Mono<CashierAgencyAssignmentResponse> createCashierAgencyAssignment(
            @RequestBody CashierAgencyAssignmentRequest request,
            Authentication authentication
    ) {
        String organizationId = resolveOrganizationId(authentication);
        String agencyId = resolveAgencyId(authentication);
        boolean restrictToOrganization = StringUtils.hasText(organizationId);
        boolean restrictToAgency = StringUtils.hasText(agencyId);
        return assignmentService.createCashierAgencyAssignment(
                request,
                resolveUserId(authentication),
                organizationId,
                agencyId,
                restrictToOrganization,
                restrictToAgency
        );
    }

    /**
     * Ends a cashier agency assignment.
     *
     * @param request delete request
     * @param authentication authentication payload
     * @return updated assignment
     */
    @DeleteMapping("/cashier-agency-assignments")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public Mono<SuccessResponse> deleteCashierAgencyAssignment(
            @RequestBody AssignmentDeleteRequest request,
            Authentication authentication
    ) {
        String assignmentId = request != null ? request.getId() : null;
        String organizationId = resolveOrganizationId(authentication);
        String agencyId = resolveAgencyId(authentication);
        boolean restrictToOrganization = StringUtils.hasText(organizationId);
        boolean restrictToAgency = StringUtils.hasText(agencyId);
        return assignmentService.endCashierAgencyAssignment(
                        assignmentId,
                        resolveUserId(authentication),
                        organizationId,
                        agencyId,
                        restrictToOrganization,
                        restrictToAgency
                )
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
