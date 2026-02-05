package com.erp.cashier.controller;

import com.erp.cashier.dto.AgencyResponse;
import com.erp.cashier.dto.CreateAgencyRequest;
import com.erp.cashier.dto.SuccessResponse;
import com.erp.cashier.dto.UpdateAgencyRequest;
import com.erp.cashier.security.JwtPayload;
import com.erp.cashier.service.AgencyAdminService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Agency endpoints for admin users.
 *
 * @author ERP Cashier Team
 * @since 2025-01-02
 */
@RestController
@RequestMapping("/api/agencies")
public class AgencyController {
    private final AgencyAdminService agencyAdminService;

    /**
     * Creates the agency controller.
     *
     * @param agencyAdminService agency admin service
     */
    public AgencyController(AgencyAdminService agencyAdminService) {
        this.agencyAdminService = agencyAdminService;
    }

    /**
     * Lists agencies for the authenticated admin.
     *
     * @param country optional country filter
     * @param town optional town filter
     * @param authentication authentication payload
     * @return agencies
     */
    @GetMapping
    @PreAuthorize("hasAuthority('ROLE_SUPERADMIN') or hasAuthority('ROLE_ORGANIZATION_ADMIN') or hasAuthority('ROLE_MANAGER')")
    public Flux<AgencyResponse> listAgencies(
            @RequestParam(value = "country", required = false) String country,
            @RequestParam(value = "town", required = false) String town,
            Authentication authentication
    ) {
        String organizationId = resolveOrganizationId(authentication);
        String agencyId = resolveAgencyId(authentication);
        boolean restrictToOrganization = StringUtils.hasText(organizationId);
        boolean restrictToAgency = StringUtils.hasText(agencyId);
        return agencyAdminService.listAgencies(
                organizationId,
                agencyId,
                restrictToOrganization,
                restrictToAgency,
                country,
                town
        );
    }

    /**
     * Gets an agency by identifier.
     *
     * @param agencyId agency identifier
     * @param authentication authentication payload
     * @return agency
     */
    @GetMapping("/{agencyId}")
    @PreAuthorize("hasAuthority('ROLE_SUPERADMIN') or hasAuthority('ROLE_ORGANIZATION_ADMIN') or hasAuthority('ROLE_MANAGER')")
    public Mono<AgencyResponse> getAgency(
            @PathVariable("agencyId") String agencyId,
            Authentication authentication
    ) {
        String organizationId = resolveOrganizationId(authentication);
        String agencyScopeId = resolveAgencyId(authentication);
        boolean restrictToOrganization = StringUtils.hasText(organizationId);
        boolean restrictToAgency = StringUtils.hasText(agencyScopeId);
        return agencyAdminService.getAgency(
                agencyId,
                organizationId,
                agencyScopeId,
                restrictToOrganization,
                restrictToAgency
        );
    }

    /**
     * Creates an agency for the authenticated admin.
     *
     * @param request create request
     * @param authentication authentication payload
     * @return created agency
     */
    @PostMapping
    @PreAuthorize("hasAuthority('ROLE_SUPERADMIN') or hasAuthority('ROLE_ORGANIZATION_ADMIN')")
    public Mono<AgencyResponse> createAgency(
            @RequestBody CreateAgencyRequest request,
            Authentication authentication
    ) {
        String organizationId = resolveOrganizationId(authentication);
        String agencyScopeId = resolveAgencyId(authentication);
        boolean restrictToOrganization = StringUtils.hasText(organizationId);
        boolean restrictToAgency = StringUtils.hasText(agencyScopeId);
        return agencyAdminService.createAgency(
                request,
                organizationId,
                restrictToOrganization,
                restrictToAgency
        );
    }

    /**
     * Updates an agency for the authenticated admin.
     *
     * @param agencyId agency identifier
     * @param request update request
     * @param authentication authentication payload
     * @return updated agency
     */
    @PatchMapping("/{agencyId}")
    @PreAuthorize("hasAuthority('ROLE_SUPERADMIN') or hasAuthority('ROLE_ORGANIZATION_ADMIN')")
    public Mono<AgencyResponse> updateAgency(
            @PathVariable("agencyId") String agencyId,
            @RequestBody UpdateAgencyRequest request,
            Authentication authentication
    ) {
        String organizationId = resolveOrganizationId(authentication);
        String agencyScopeId = resolveAgencyId(authentication);
        boolean restrictToOrganization = StringUtils.hasText(organizationId);
        boolean restrictToAgency = StringUtils.hasText(agencyScopeId);
        return agencyAdminService.updateAgency(
                agencyId,
                request,
                organizationId,
                agencyScopeId,
                restrictToOrganization,
                restrictToAgency
        );
    }

    /**
     * Deletes an agency for the authenticated admin.
     *
     * @param agencyId agency identifier
     * @param authentication authentication payload
     * @return completion signal
     */
    @DeleteMapping("/{agencyId}")
    @PreAuthorize("hasAuthority('ROLE_SUPERADMIN') or hasAuthority('ROLE_ORGANIZATION_ADMIN')")
    public Mono<SuccessResponse> deleteAgency(
            @PathVariable("agencyId") String agencyId,
            Authentication authentication
    ) {
        String organizationId = resolveOrganizationId(authentication);
        String agencyScopeId = resolveAgencyId(authentication);
        boolean restrictToOrganization = StringUtils.hasText(organizationId);
        boolean restrictToAgency = StringUtils.hasText(agencyScopeId);
        return agencyAdminService.deleteAgency(
                        agencyId,
                        organizationId,
                        agencyScopeId,
                        restrictToOrganization,
                        restrictToAgency
                )
                .thenReturn(new SuccessResponse(true));
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
