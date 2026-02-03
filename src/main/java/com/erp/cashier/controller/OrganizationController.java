package com.erp.cashier.controller;

import com.erp.cashier.dto.CreateOrganizationRequest;
import com.erp.cashier.dto.OrganizationResponse;
import com.erp.cashier.dto.SuccessResponse;
import com.erp.cashier.dto.UpdateOrganizationRequest;
import com.erp.cashier.service.SuperAdminService;
import java.security.Principal;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Organization endpoints for ERP admins.
 *
 * @author ERP Cashier Team
 * @since 2025-01-20
 */
@RestController
@RequestMapping("/api/organizations")
public class OrganizationController {
    private final SuperAdminService superAdminService;

    /**
     * Creates the organization controller.
     *
     * @param superAdminService super admin service
     */
    public OrganizationController(SuperAdminService superAdminService) {
        this.superAdminService = superAdminService;
    }

    /**
     * Lists organizations.
     *
     * @return organizations
     */
    @GetMapping
    @PreAuthorize("hasAuthority('ROLE_SUPERADMIN')")
    public Flux<OrganizationResponse> listOrganizations() {
        return superAdminService.listOrganizations();
    }

    /**
     * Creates a new organization.
     *
     * @param request create request
     * @param principal authenticated principal
     * @return created organization
     */
    @PostMapping
    @PreAuthorize("hasAuthority('ROLE_SUPERADMIN')")
    public Mono<OrganizationResponse> createOrganization(
            @RequestBody CreateOrganizationRequest request,
            Principal principal
    ) {
        String createdBy = principal != null ? principal.getName() : null;
        return superAdminService.createOrganization(request, createdBy);
    }

    /**
     * Updates an organization.
     *
     * @param organizationId organization identifier
     * @param request update request
     * @return updated organization
     */
    @PutMapping("/{organizationId}")
    @PreAuthorize("hasAuthority('ROLE_SUPERADMIN')")
    public Mono<OrganizationResponse> updateOrganization(
            @PathVariable("organizationId") String organizationId,
            @RequestBody UpdateOrganizationRequest request
    ) {
        return superAdminService.updateOrganization(organizationId, request);
    }

    /**
     * Deletes an organization.
     *
     * @param organizationId organization identifier
     * @return completion signal
     */
    @DeleteMapping("/{organizationId}")
    @PreAuthorize("hasAuthority('ROLE_SUPERADMIN')")
    public Mono<SuccessResponse> deleteOrganization(@PathVariable("organizationId") String organizationId) {
        return superAdminService.deleteOrganization(organizationId)
                .thenReturn(new SuccessResponse(true));
    }
}
