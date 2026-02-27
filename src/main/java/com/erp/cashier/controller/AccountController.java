package com.erp.cashier.controller;

import com.erp.cashier.dto.AccountOperationRequest;
import com.erp.cashier.dto.AccountP2PTransferRequest;
import com.erp.cashier.dto.AccountP2PTransferResponse;
import com.erp.cashier.dto.AccountTransferResponse;
import com.erp.cashier.dto.AdminAccountResponse;
import com.erp.cashier.dto.AdminCustomerResponse;
import com.erp.cashier.dto.CashierFundRequest;
import com.erp.cashier.dto.CashierAccountResponse;
import com.erp.cashier.dto.CashierCustomerResponse;
import com.erp.cashier.dto.CreateCustomerRequest;
import com.erp.cashier.dto.CreateCustomerResponse;
import com.erp.cashier.dto.CustomerResponse;
import com.erp.cashier.dto.FundRequestCreateResponse;
import com.erp.cashier.dto.FundRequestFundingResponse;
import com.erp.cashier.dto.FundRequestProvisionRequest;
import com.erp.cashier.dto.FundRequestResponse;
import com.erp.cashier.security.JwtPayload;
import com.erp.cashier.service.AccountService;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Account and customer endpoints.
 *
 * @author ERP Cashier Team
 * @since 2025-01-20
 */
@RestController
@RequestMapping("/api")
public class AccountController {
    private final AccountService accountService;

    /**
     * Creates the account controller.
     *
     * @param accountService account service
     */
    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    /**
     * Lists accounts for admin users.
     *
     * @return accounts
     */
    @GetMapping("/admin/accounts")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public Flux<AdminAccountResponse> listAdminAccounts(Authentication authentication) {
        return accountService.listAdminAccounts(
                resolveOrganizationId(authentication),
                resolveAgencyId(authentication)
        );
    }

    /**
     * Lists customers for admin users.
     *
     * @return customers
     */
    @GetMapping("/admin/customers")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public Flux<AdminCustomerResponse> listAdminCustomers(Authentication authentication) {
        return accountService.listAdminCustomers(
                resolveOrganizationId(authentication),
                resolveAgencyId(authentication)
        );
    }

    /**
     * Creates a customer.
     *
     * @param request create request
     * @param authentication authentication payload
     * @return created customer
     */
    @PostMapping("/admin/customers")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public Mono<CreateCustomerResponse> createCustomer(
            @RequestBody CreateCustomerRequest request,
            Authentication authentication
    ) {
        return accountService.createCustomer(
                request,
                resolveUserId(authentication),
                resolveOrganizationId(authentication)
        );
    }

    /**
     * Lists accounts for cashiers.
     *
     * @return accounts
     */
    @GetMapping("/cashier/accounts")
    @PreAuthorize("hasAuthority('ROLE_CASHIER')")
    public Flux<CashierAccountResponse> listCashierAccounts(Authentication authentication) {
        return accountService.listAccounts(
                resolveOrganizationId(authentication),
                resolveAgencyId(authentication)
        );
    }

    /**
     * Lists customers for cashiers.
     *
     * @return customers
     */
    @GetMapping("/cashier/customers")
    @PreAuthorize("hasAuthority('ROLE_CASHIER')")
    public Flux<CashierCustomerResponse> listCashierCustomers(Authentication authentication) {
        return accountService.listCustomers(
                resolveOrganizationId(authentication),
                resolveAgencyId(authentication)
        );
    }

    /**
     * Searches customers by query.
     *
     * @param query search query
     * @return matching customers
     */
    @GetMapping("/customers/search")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_CASHIER')")
    public Flux<CustomerResponse> searchCustomers(
            @RequestParam(value = "q", required = false) String query,
            Authentication authentication
    ) {
        return accountService.searchCustomers(
                query,
                resolveOrganizationId(authentication),
                resolveAgencyId(authentication)
        );
    }

    /**
     * Deposits into an account.
     *
     * @param request operation request
     * @param authentication authentication payload
     * @return transfer response
     */
    @PostMapping("/accounts/transfer")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_CASHIER')")
    public Mono<AccountTransferResponse> deposit(
            @RequestBody AccountOperationRequest request,
            Authentication authentication
    ) {
        return accountService.deposit(
                request != null ? request.getAccountId() : null,
                request != null ? request.getAmount() : null,
                request != null ? request.getReference() : null,
                request != null ? request.getReason() : null,
                request != null ? request.getTicketing() : null,
                request != null ? request.getPaymentMethod() : null,
                resolveUserId(authentication)
        );
    }

    /**
     * Withdraws from an account.
     *
     * @param request operation request
     * @param authentication authentication payload
     * @return transfer response
     */
    @PostMapping("/accounts/withdraw")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_CASHIER')")
    public Mono<AccountTransferResponse> withdraw(
            @RequestBody AccountOperationRequest request,
            Authentication authentication
    ) {
        return accountService.withdraw(
                request != null ? request.getAccountId() : null,
                request != null ? request.getAmount() : null,
                request != null ? request.getReference() : null,
                request != null ? request.getReason() : null,
                request != null ? request.getTicketing() : null,
                request != null ? request.getPaymentMethod() : null,
                resolveUserId(authentication)
        );
    }

    /**
     * Transfers between accounts.
     *
     * @param request transfer request
     * @param authentication authentication payload
     * @return transfer response
     */
    @PostMapping("/accounts/transfer-p2p")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_CASHIER')")
    public Mono<AccountP2PTransferResponse> transferP2P(
            @RequestBody AccountP2PTransferRequest request,
            Authentication authentication
    ) {
        return accountService.transferP2P(request, resolveUserId(authentication));
    }

    /**
     * Requests cash funds from the oldest available cashier in the same agency.
     *
     * @param request fund request payload
     * @param authentication authentication payload
     * @return transfer response
     */
    @PostMapping("/cashier/fund-requests")
    @PreAuthorize("hasAuthority('ROLE_CASHIER')")
    public Mono<FundRequestCreateResponse> requestFunds(
            @RequestBody CashierFundRequest request,
            Authentication authentication
    ) {
        return accountService.requestFunds(request, resolveUserId(authentication));
    }

    /**
     * Lists fund requests for the cashier agency.
     *
     * @param authentication authentication payload
     * @return fund requests
     */
    @GetMapping("/cashier/fund-requests")
    @PreAuthorize("hasAnyAuthority('ROLE_CASHIER', 'ROLE_ADMIN')")
    public Flux<FundRequestResponse> listFundRequests(Authentication authentication) {
        return accountService.listFundRequests(
                resolveUserId(authentication),
                resolveAgencyId(authentication)
        );
    }

    /**
     * Adds funds to a pending cashier fund request with ticketing.
     *
     * @param requestId request identifier
     * @param request funding payload
     * @param authentication authentication payload
     * @return funding response
     */
    @PostMapping("/cashier/fund-requests/{requestId}/fund")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public Mono<FundRequestFundingResponse> fundCashierRequest(
            @PathVariable("requestId") String requestId,
            @RequestBody FundRequestProvisionRequest request,
            Authentication authentication
    ) {
        return accountService.fulfillFundRequest(
                requestId,
                request,
                resolveUserId(authentication)
        );
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
