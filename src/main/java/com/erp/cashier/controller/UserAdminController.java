package com.erp.cashier.controller;

import com.erp.cashier.dto.AdminUserResponse;
import com.erp.cashier.dto.CreateOrganizationAdminRequest;
import com.erp.cashier.dto.SuccessResponse;
import com.erp.cashier.dto.UpdateOrganizationAdminRequest;
import com.erp.cashier.security.JwtPayload;
import com.erp.cashier.service.SuperAdminService;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * User admin endpoints for managing admin users.
 *
 * @author ERP Cashier Team
 * @since 2025-01-20
 */
@RestController
@RequestMapping("/api/users/admins")
public class UserAdminController {
    private final SuperAdminService superAdminService;

    /**
     * Creates the user admin controller.
     *
     * @param superAdminService super admin service
     */
    public UserAdminController(SuperAdminService superAdminService) {
        this.superAdminService = superAdminService;
    }

    /**
     * Lists admin users.
     *
     * @param authentication authentication payload
     * @return admin users
     */
    @GetMapping
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public Flux<AdminUserResponse> listAdmins(Authentication authentication) {
        String organizationId = resolveOrganizationId(authentication);
        String agencyId = resolveAgencyId(authentication);
        if (StringUtils.hasText(organizationId)) {
            return superAdminService.listAgencyAdmins(organizationId, agencyId);
        }
        if (hasRole(authentication, "ROLE_SUPERADMIN")) {
            return superAdminService.listAllAdmins();
        }
        return Flux.error(new ResponseStatusException(HttpStatus.FORBIDDEN, "Not authorized to list admins."));
    }

    /**
     * Creates an admin user.
     *
     * @param request create request
     * @param authentication authentication payload
     * @return created admin
     */
    @PostMapping
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public Mono<AdminUserResponse> createAdmin(
            @RequestBody CreateOrganizationAdminRequest request,
            Authentication authentication
    ) {
        if (hasRole(authentication, "ROLE_SUPERADMIN")) {
            return superAdminService.createOrganizationAdmin(request);
        }
        if (hasRole(authentication, "ROLE_ORGANIZATION_ADMIN")) {
            return superAdminService.createAgencyAdmin(request, resolveOrganizationId(authentication));
        }
        return Mono.error(new ResponseStatusException(HttpStatus.FORBIDDEN, "Not authorized to create admins."));
    }

    /**
     * Updates an admin user.
     *
     * @param adminId admin identifier
     * @param request update request
     * @param authentication authentication payload
     * @return updated admin
     */
    @PutMapping("/{adminId}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public Mono<AdminUserResponse> updateAdmin(
            @PathVariable("adminId") String adminId,
            @RequestBody UpdateOrganizationAdminRequest request,
            Authentication authentication
    ) {
        if (hasRole(authentication, "ROLE_SUPERADMIN")) {
            return superAdminService.updateOrganizationAdmin(adminId, request);
        }
        if (hasRole(authentication, "ROLE_ORGANIZATION_ADMIN")) {
            return superAdminService.updateAgencyAdmin(
                    adminId,
                    request,
                    resolveOrganizationId(authentication),
                    resolveAgencyId(authentication)
            );
        }
        return Mono.error(new ResponseStatusException(HttpStatus.FORBIDDEN, "Not authorized to update admins."));
    }

    /**
     * Deletes an admin user.
     *
     * @param adminId admin identifier
     * @param authentication authentication payload
     * @return completion signal
     */
    @DeleteMapping("/{adminId}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public Mono<SuccessResponse> deleteAdmin(
            @PathVariable("adminId") String adminId,
            Authentication authentication
    ) {
        if (hasRole(authentication, "ROLE_SUPERADMIN")) {
            return superAdminService.deleteOrganizationAdmin(adminId)
                    .thenReturn(new SuccessResponse(true));
        }
        if (hasRole(authentication, "ROLE_ORGANIZATION_ADMIN")) {
            return superAdminService.deleteAgencyAdmin(
                            adminId,
                            resolveOrganizationId(authentication),
                            resolveAgencyId(authentication)
                    )
                    .thenReturn(new SuccessResponse(true));
        }
        return Mono.error(new ResponseStatusException(HttpStatus.FORBIDDEN, "Not authorized to delete admins."));
    }

    private boolean hasRole(Authentication authentication, String role) {
        if (authentication == null || authentication.getAuthorities() == null) {
            return false;
        }
        return authentication.getAuthorities().stream()
                .anyMatch(authority -> role.equals(authority.getAuthority()));
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
