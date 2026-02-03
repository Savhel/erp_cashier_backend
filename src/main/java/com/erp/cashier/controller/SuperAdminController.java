package com.erp.cashier.controller;

import com.erp.cashier.dto.AdminUserResponse;
import com.erp.cashier.dto.CreateOrganizationAdminRequest;
import com.erp.cashier.dto.CreateOrganizationRequest;
import com.erp.cashier.dto.OrganizationResponse;
import com.erp.cashier.dto.UpdateOrganizationAdminRequest;
import com.erp.cashier.dto.UpdateOrganizationRequest;
import com.erp.cashier.service.SuperAdminService;
import java.security.Principal;
import org.springframework.security.core.Authentication;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.HttpStatus;
import com.erp.cashier.security.JwtPayload;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Admin endpoints for managing organizations and admins.
 *
 * @author ERP Cashier Team
 * @since 2025-01-02
 */
@RestController
@RequestMapping("/api/admin")
public class SuperAdminController {
    private final SuperAdminService superAdminService;

    /**
     * Creates the super admin controller.
     *
     * @param superAdminService super admin service
     */
    public SuperAdminController(SuperAdminService superAdminService) {
        this.superAdminService = superAdminService;
    }

    /**
     * Lists all organizations.
     *
     * @return organizations
     */
    @GetMapping("/organizations")
    @PreAuthorize("hasAuthority('ROLE_SUPERADMIN')")
    public Flux<OrganizationResponse> listOrganizations() {
        return superAdminService.listOrganizations();
    }

    /**
     * Creates a new organization.
     *
     * @param request create organization request
     * @param principal authenticated principal
     * @return created organization
     */
    @PostMapping("/organizations")
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
    @PutMapping("/organizations/{organizationId}")
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
    @DeleteMapping("/organizations/{organizationId}")
    @PreAuthorize("hasAuthority('ROLE_SUPERADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> deleteOrganization(@PathVariable("organizationId") String organizationId) {
        return superAdminService.deleteOrganization(organizationId);
    }

    /**
     * Lists organization admins.
     *
     * @return organization admins
     */
    @GetMapping("/organization-admins")
    @PreAuthorize("hasAuthority('ROLE_SUPERADMIN')")
    public Flux<AdminUserResponse> listOrganizationAdmins() {
        return superAdminService.listOrganizationAdmins();
    }

    /**
     * Gets an organization admin.
     *
     * @param personId person identifier
     * @return organization admin
     */
    @GetMapping("/organization-admins/{personId}")
    @PreAuthorize("hasAuthority('ROLE_SUPERADMIN')")
    public Mono<AdminUserResponse> getOrganizationAdmin(@PathVariable("personId") String personId) {
        return superAdminService.getOrganizationAdmin(personId);
    }

    /**
     * Creates a new organization admin.
     *
     * @param request create admin request
     * @return created admin
     */
    @PostMapping("/organization-admins")
    @PreAuthorize("hasAuthority('ROLE_SUPERADMIN')")
    public Mono<AdminUserResponse> createOrganizationAdmin(@RequestBody CreateOrganizationAdminRequest request) {
        return superAdminService.createOrganizationAdmin(request);
    }

    /**
     * Updates an organization admin.
     *
     * @param personId person identifier
     * @param request update request
     * @return updated admin
     */
    @PutMapping("/organization-admins/{personId}")
    @PreAuthorize("hasAuthority('ROLE_SUPERADMIN')")
    public Mono<AdminUserResponse> updateOrganizationAdmin(
            @PathVariable("personId") String personId,
            @RequestBody UpdateOrganizationAdminRequest request
    ) {
        return superAdminService.updateOrganizationAdmin(personId, request);
    }

    /**
     * Deletes an organization admin.
     *
     * @param personId person identifier
     * @return completion signal
     */
    @DeleteMapping("/organization-admins/{personId}")
    @PreAuthorize("hasAuthority('ROLE_SUPERADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> deleteOrganizationAdmin(@PathVariable("personId") String personId) {
        return superAdminService.deleteOrganizationAdmin(personId);
    }

    /**
     * Lists agency admins for the authenticated organization admin.
     *
     * @param authentication authentication payload
     * @return agency admins
     */
    @GetMapping("/agency-admins")
    @PreAuthorize("hasAuthority('ROLE_ORGANIZATION_ADMIN')")
    public Flux<AdminUserResponse> listAgencyAdmins(Authentication authentication) {
        return superAdminService.listAgencyAdmins(
                resolveOrganizationId(authentication),
                resolveAgencyId(authentication)
        );
    }

    /**
     * Gets an agency admin for the authenticated organization admin.
     *
     * @param personId person identifier
     * @param authentication authentication payload
     * @return agency admin
     */
    @GetMapping("/agency-admins/{personId}")
    @PreAuthorize("hasAuthority('ROLE_ORGANIZATION_ADMIN')")
    public Mono<AdminUserResponse> getAgencyAdmin(
            @PathVariable("personId") String personId,
            Authentication authentication
    ) {
        return superAdminService.getAgencyAdmin(personId, resolveOrganizationId(authentication));
    }

    /**
     * Creates a new agency admin.
     *
     * @param request create admin request
     * @param authentication authentication payload
     * @return created admin
     */
    @PostMapping("/agency-admins")
    @PreAuthorize("hasAuthority('ROLE_ORGANIZATION_ADMIN')")
    public Mono<AdminUserResponse> createAgencyAdmin(
            @RequestBody CreateOrganizationAdminRequest request,
            Authentication authentication
    ) {
        return superAdminService.createAgencyAdmin(request, resolveOrganizationId(authentication));
    }

    /**
     * Updates an agency admin.
     *
     * @param personId person identifier
     * @param request update request
     * @param authentication authentication payload
     * @return updated admin
     */
    @PutMapping("/agency-admins/{personId}")
    @PreAuthorize("hasAuthority('ROLE_ORGANIZATION_ADMIN')")
    public Mono<AdminUserResponse> updateAgencyAdmin(
            @PathVariable("personId") String personId,
            @RequestBody UpdateOrganizationAdminRequest request,
            Authentication authentication
    ) {
        return superAdminService.updateAgencyAdmin(
                personId,
                request,
                resolveOrganizationId(authentication),
                resolveAgencyId(authentication)
        );
    }

    /**
     * Deletes an agency admin.
     *
     * @param personId person identifier
     * @param authentication authentication payload
     * @return completion signal
     */
    @DeleteMapping("/agency-admins/{personId}")
    @PreAuthorize("hasAuthority('ROLE_ORGANIZATION_ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> deleteAgencyAdmin(
            @PathVariable("personId") String personId,
            Authentication authentication
    ) {
        return superAdminService.deleteAgencyAdmin(
                personId,
                resolveOrganizationId(authentication),
                resolveAgencyId(authentication)
        );
    }

    /**
     * Looks up an admin by phone number.
     *
     * @param phone phone number
     * @return admin response
     */
    @GetMapping("/admins/lookup")
    @PreAuthorize("hasAuthority('ROLE_SUPERADMIN') or hasAuthority('ROLE_ORGANIZATION_ADMIN')")
    public Mono<AdminUserResponse> lookupAdminByPhone(@RequestParam("phone") String phone) {
        return superAdminService.lookupAdminByPhone(phone);
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
