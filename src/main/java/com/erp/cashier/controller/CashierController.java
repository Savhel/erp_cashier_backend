package com.erp.cashier.controller;

import com.erp.cashier.dto.CashierListResponse;
import com.erp.cashier.dto.CashierResponse;
import com.erp.cashier.dto.CashierWithProfileResponse;
import com.erp.cashier.dto.CreateCashierRequest;
import com.erp.cashier.dto.UpdateCashierRequest;
import com.erp.cashier.security.JwtPayload;
import com.erp.cashier.service.CashierAdminService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Cashier endpoints for admin users.
 *
 * @author ERP Cashier Team
 * @since 2025-01-02
 */
@RestController
@RequestMapping("/api/cashiers")
public class CashierController {
    private final CashierAdminService cashierAdminService;

    /**
     * Creates the cashier controller.
     *
     * @param cashierAdminService cashier admin service
     */
    public CashierController(CashierAdminService cashierAdminService) {
        this.cashierAdminService = cashierAdminService;
    }

    /**
     * Lists cashiers for admin users.
     *
     * @param includeBlocked include blocked sessions
     * @param authentication authentication payload
     * @return cashiers
     */
    @GetMapping
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public Flux<CashierListResponse> listCashiers(
            @RequestParam(value = "includeBlocked", required = false, defaultValue = "false") boolean includeBlocked,
            Authentication authentication
    ) {
        String organizationId = resolveOrganizationId(authentication);
        String agencyId = resolveAgencyId(authentication);
        boolean restrictToOrganization = StringUtils.hasText(organizationId);
        boolean restrictToAgency = StringUtils.hasText(agencyId);
        return cashierAdminService.listCashiers(
                includeBlocked,
                organizationId,
                agencyId,
                restrictToAgency,
                restrictToOrganization
        ).map(CashierListResponse::from);
    }

    /**
     * Lists available cashiers for a date range.
     *
     * @param startOn start date (YYYY-MM-DD)
     * @param endOn end date (YYYY-MM-DD)
     * @param authentication authentication payload
     * @return available cashiers
     */
    @GetMapping("/available")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public Flux<CashierListResponse> listAvailableCashiers(
            @RequestParam("start_on") String startOn,
            @RequestParam("end_on") String endOn,
            Authentication authentication
    ) {
        String organizationId = resolveOrganizationId(authentication);
        String agencyId = resolveAgencyId(authentication);
        boolean restrictToOrganization = StringUtils.hasText(organizationId);
        boolean restrictToAgency = StringUtils.hasText(agencyId);
        return cashierAdminService.listAvailableCashiers(
                startOn,
                endOn,
                organizationId,
                agencyId,
                restrictToOrganization,
                restrictToAgency
        ).map(CashierListResponse::from);
    }


    /**
     * Lists cashiers with profile details for admin users.
     *
     * @param authentication authentication payload
     * @return cashiers with profile details
     */
    @GetMapping("/with-profile")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public Flux<CashierWithProfileResponse> listCashiersWithProfile(
            Authentication authentication
    ) {
        String organizationId = resolveOrganizationId(authentication);
        String agencyId = resolveAgencyId(authentication);
        boolean restrictToOrganization = StringUtils.hasText(organizationId);
        boolean restrictToAgency = StringUtils.hasText(agencyId);
        return cashierAdminService.listCashiersWithProfile(
                organizationId,
                agencyId,
                restrictToAgency,
                restrictToOrganization
        );
    }

    /**
     * Creates a new cashier.
     *
     * @param request create request
     * @return created cashier
     */
    @PostMapping
    @PreAuthorize("hasAuthority('ROLE_ORGANIZATION_ADMIN') or hasAuthority('ROLE_SUPERADMIN')")
    public Mono<CashierResponse> createCashier(@RequestBody CreateCashierRequest request) {
        return cashierAdminService.createCashier(request);
    }

    /**
     * Updates a cashier.
     *
     * @param cashierId cashier identifier
     * @param request update request
     * @param authentication authentication payload
     * @return updated cashier
     */
    @PutMapping("/{cashierId}")
    @PreAuthorize("hasAuthority('ROLE_ORGANIZATION_ADMIN') or hasAuthority('ROLE_SUPERADMIN')")
    public Mono<CashierWithProfileResponse> updateCashier(
            @PathVariable("cashierId") String cashierId,
            @RequestBody UpdateCashierRequest request,
            Authentication authentication
    ) {
        String organizationId = resolveOrganizationId(authentication);
        boolean restrictToOrganization = StringUtils.hasText(organizationId);
        return cashierAdminService.updateCashierWithProfile(
                cashierId,
                request,
                organizationId,
                restrictToOrganization
        );
    }

    /**
     * Deletes a cashier.
     *
     * @param cashierId cashier identifier
     * @param authentication authentication payload
     * @return completion signal
     */
    @DeleteMapping("/{cashierId}")
    @PreAuthorize("hasAuthority('ROLE_ORGANIZATION_ADMIN') or hasAuthority('ROLE_SUPERADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> deleteCashier(@PathVariable("cashierId") String cashierId, Authentication authentication) {
        String organizationId = resolveOrganizationId(authentication);
        boolean restrictToOrganization = StringUtils.hasText(organizationId);
        return cashierAdminService.deleteCashier(cashierId, organizationId, restrictToOrganization);
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
}
