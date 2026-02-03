package com.erp.cashier.controller;

import com.erp.cashier.dto.AssignCashierRequest;
import com.erp.cashier.dto.CashRegisterDetailResponse;
import com.erp.cashier.dto.CashRegisterResponse;
import com.erp.cashier.dto.CreateCashRegisterRequest;
import com.erp.cashier.dto.UpdateCashRegisterRequest;
import com.erp.cashier.dto.SuccessResponse;
import com.erp.cashier.model.CashierManageCashRegister;
import com.erp.cashier.security.JwtPayload;
import com.erp.cashier.service.CashRegisterAdminService;
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
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Cash register endpoints for admin users.
 *
 * @author ERP Cashier Team
 * @since 2025-01-02
 */
@RestController
@RequestMapping("/api/cash-registers")
public class CashRegisterController {
    private final CashRegisterAdminService cashRegisterAdminService;

    /**
     * Creates the cash register controller.
     *
     * @param cashRegisterAdminService cash register admin service
     */
    public CashRegisterController(CashRegisterAdminService cashRegisterAdminService) {
        this.cashRegisterAdminService = cashRegisterAdminService;
    }

    /**
     * Lists cash registers for admin users.
     *
     * @param authentication authentication payload
     * @return cash registers
     */
    @GetMapping
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public Flux<CashRegisterResponse> listRegisters(Authentication authentication) {
        String organizationId = resolveOrganizationId(authentication);
        String agencyId = resolveAgencyId(authentication);
        boolean restrictToOrganization = StringUtils.hasText(organizationId);
        boolean restrictToAgency = StringUtils.hasText(agencyId);
        return cashRegisterAdminService.listRegisters(
                organizationId,
                agencyId,
                restrictToAgency,
                restrictToOrganization
        );
    }

    /**
     * Creates a new cash register.
     *
     * @param request create request
     * @param authentication authentication payload
     * @return created cash register
     */
    @PostMapping
    @PreAuthorize("hasAuthority('ROLE_MANAGER')")
    public Mono<CashRegisterResponse> createRegister(
            @RequestBody CreateCashRegisterRequest request,
            Authentication authentication
    ) {
        String organizationId = resolveOrganizationId(authentication);
        String agencyId = resolveAgencyId(authentication);
        String creatorId = resolveUserId(authentication);
        boolean restrictToOrganization = StringUtils.hasText(organizationId);
        boolean restrictToAgency = StringUtils.hasText(agencyId);
        return cashRegisterAdminService.createRegister(
                request,
                creatorId,
                organizationId,
                agencyId,
                restrictToOrganization,
                restrictToAgency
        );
    }

    /**
     * Returns details for a cash register.
     *
     * @param registerId register identifier
     * @param authentication authentication payload
     * @return detailed cash register
     */
    @GetMapping("/{registerId}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public Mono<CashRegisterDetailResponse> getRegisterDetails(
            @PathVariable("registerId") String registerId,
            Authentication authentication
    ) {
        String organizationId = resolveOrganizationId(authentication);
        String agencyId = resolveAgencyId(authentication);
        boolean restrictToOrganization = StringUtils.hasText(organizationId);
        boolean restrictToAgency = StringUtils.hasText(agencyId);
        return cashRegisterAdminService.getRegisterDetails(
                registerId,
                organizationId,
                agencyId,
                restrictToAgency,
                restrictToOrganization
        );
    }

    /**
     * Updates a cash register.
     *
     * @param registerId register identifier
     * @param request update request
     * @param authentication authentication payload
     * @return updated cash register
     */
    @PutMapping("/{registerId}")
    @PreAuthorize("hasAuthority('ROLE_MANAGER')")
    public Mono<CashRegisterResponse> updateRegister(
            @PathVariable("registerId") String registerId,
            @RequestBody UpdateCashRegisterRequest request,
            Authentication authentication
    ) {
        String organizationId = resolveOrganizationId(authentication);
        String agencyId = resolveAgencyId(authentication);
        boolean restrictToOrganization = StringUtils.hasText(organizationId);
        boolean restrictToAgency = StringUtils.hasText(agencyId);
        return cashRegisterAdminService.updateRegister(
                registerId,
                request,
                organizationId,
                agencyId,
                restrictToOrganization,
                restrictToAgency
        );
    }

    /**
     * Deletes a cash register.
     *
     * @param registerId register identifier
     * @param authentication authentication payload
     * @return completion signal
     */
    @DeleteMapping("/{registerId}")
    @PreAuthorize("hasAuthority('ROLE_MANAGER')")
    public Mono<SuccessResponse> deleteRegister(
            @PathVariable("registerId") String registerId,
            Authentication authentication
    ) {
        String organizationId = resolveOrganizationId(authentication);
        String agencyId = resolveAgencyId(authentication);
        boolean restrictToOrganization = StringUtils.hasText(organizationId);
        boolean restrictToAgency = StringUtils.hasText(agencyId);
        return cashRegisterAdminService.deleteRegister(
                        registerId,
                        organizationId,
                        agencyId,
                        restrictToOrganization,
                        restrictToAgency
                )
                .thenReturn(new SuccessResponse(true));
    }

    /**
     * Assigns a cashier to a cash register.
     *
     * @param registerId register identifier
     * @param request assign request
     * @param authentication authentication payload
     * @return assignment payload
     */
    @PostMapping("/{registerId}/assign")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public Mono<CashierManageCashRegister> assignCashier(
            @PathVariable("registerId") String registerId,
            @RequestBody AssignCashierRequest request,
            Authentication authentication
    ) {
        String organizationId = resolveOrganizationId(authentication);
        String agencyId = resolveAgencyId(authentication);
        boolean restrictToOrganization = StringUtils.hasText(organizationId);
        boolean restrictToAgency = StringUtils.hasText(agencyId);
        String adminId = resolveUserId(authentication);
        return cashRegisterAdminService.assignCashier(
                registerId,
                request,
                adminId,
                organizationId,
                agencyId,
                restrictToOrganization,
                restrictToAgency
        );
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
}
