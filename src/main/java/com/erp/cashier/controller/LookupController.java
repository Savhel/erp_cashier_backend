package com.erp.cashier.controller;

import com.erp.cashier.dto.AdminUserResponse;
import com.erp.cashier.dto.LookupCashierResponse;
import com.erp.cashier.dto.LookupCustomerResponse;
import com.erp.cashier.dto.LookupOrganizationResponse;
import com.erp.cashier.service.LookupService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * Lookup endpoints for external integrations.
 *
 * @author ERP Cashier Team
 * @since 2025-01-20
 */
@RestController
@RequestMapping("/api/lookup")
public class LookupController {
    private final LookupService lookupService;

    /**
     * Creates the lookup controller.
     *
     * @param lookupService lookup service
     */
    public LookupController(LookupService lookupService) {
        this.lookupService = lookupService;
    }

    /**
     * Looks up an admin by phone.
     *
     * @param phone phone number
     * @return admin response
     */
    @GetMapping("/admin")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public Mono<AdminUserResponse> lookupAdmin(@RequestParam("phone") String phone) {
        return lookupService.lookupAdmin(phone);
    }

    /**
     * Looks up a cashier by identifier.
     *
     * @param cashierId cashier identifier
     * @return cashier response
     */
    @GetMapping("/cashier")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public Mono<LookupCashierResponse> lookupCashier(@RequestParam("id") String cashierId) {
        return lookupService.lookupCashier(cashierId);
    }

    /**
     * Looks up a customer by phone.
     *
     * @param phone phone number
     * @return customer response
     */
    @GetMapping("/customer")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public Mono<LookupCustomerResponse> lookupCustomer(@RequestParam("phone") String phone) {
        return lookupService.lookupCustomer(phone);
    }

    /**
     * Looks up an organization by code.
     *
     * @param code organization code
     * @return organization response
     */
    @GetMapping("/organization")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public Mono<LookupOrganizationResponse> lookupOrganization(@RequestParam("code") String code) {
        return lookupService.lookupOrganization(code);
    }
}
