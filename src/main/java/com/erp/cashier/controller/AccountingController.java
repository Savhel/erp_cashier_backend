package com.erp.cashier.controller;

import com.erp.cashier.dto.AccountingCashMovementRequest;
import com.erp.cashier.dto.AccountingCashMovementResponse;
import com.erp.cashier.service.AccountingCashMovementService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * Accounting integration endpoints.
 *
 * @author ERP Cashier Team
 * @since 2026-02-05
 */
@RestController
@RequestMapping("/api/v1/accounting")
public class AccountingController {
    private final AccountingCashMovementService accountingService;

    /**
     * Creates the accounting controller.
     *
     * @param accountingService accounting service
     */
    public AccountingController(AccountingCashMovementService accountingService) {
        this.accountingService = accountingService;
    }

    /**
     * Pushes a cash movement to the external accounting system.
     *
     * @param request cash movement payload
     * @return external response
     */
    @PostMapping("/cash-movements")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_CASHIER')")
    public Mono<AccountingCashMovementResponse> pushCashMovement(
            @RequestBody AccountingCashMovementRequest request
    ) {
        return accountingService.syncCashMovement(request);
    }
}
