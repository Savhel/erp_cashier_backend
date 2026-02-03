package com.erp.cashier.controller;

import com.erp.cashier.dto.CashierListResponse;
import com.erp.cashier.dto.CashierResponse;
import com.erp.cashier.dto.CashierWithProfileResponse;
import com.erp.cashier.dto.CreateCashierRequest;
import com.erp.cashier.dto.SuccessResponse;
import com.erp.cashier.dto.UpdateCashierRequest;
import com.erp.cashier.security.JwtPayload;
import com.erp.cashier.service.CashierAdminService;
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
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * User cashier endpoints for admin users.
 *
 * @author ERP Cashier Team
 * @since 2025-01-20
 */
@RestController
@RequestMapping("/api/users/cashiers")
public class UserCashierController {
    private final CashierAdminService cashierAdminService;

    /**
     * Creates the user cashier controller.
     *
     * @param cashierAdminService cashier admin service
     */
    public UserCashierController(CashierAdminService cashierAdminService) {
        this.cashierAdminService = cashierAdminService;
    }

    /**
     * Lists cashiers.
     *
     * @param includeBlocked include blocked sessions
     * @param available availability flag
     * @param startOn availability start date
     * @param endOn availability end date
     * @param authentication authentication payload
     * @return cashiers
     */
    @GetMapping
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public Flux<CashierListResponse> listCashiers(
            @RequestParam(value = "includeBlocked", required = false, defaultValue = "false") boolean includeBlocked,
            @RequestParam(value = "available", required = false) String available,
            @RequestParam(value = "start_on", required = false) String startOn,
            @RequestParam(value = "end_on", required = false) String endOn,
            Authentication authentication
    ) {
        if (isAvailableFlag(available)) {
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
     * Creates a cashier.
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
    public Mono<SuccessResponse> deleteCashier(
            @PathVariable("cashierId") String cashierId,
            Authentication authentication
    ) {
        String organizationId = resolveOrganizationId(authentication);
        boolean restrictToOrganization = StringUtils.hasText(organizationId);
        return cashierAdminService.deleteCashier(cashierId, organizationId, restrictToOrganization)
                .thenReturn(new SuccessResponse(true));
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

    private boolean isAvailableFlag(String value) {
        if (value == null) {
            return false;
        }
        String trimmed = value.trim().toLowerCase();
        return "1".equals(trimmed) || "true".equals(trimmed) || "yes".equals(trimmed);
    }
}
