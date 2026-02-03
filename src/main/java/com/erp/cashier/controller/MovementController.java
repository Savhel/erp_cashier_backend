package com.erp.cashier.controller;

import com.erp.cashier.dto.MovementAccountResponse;
import com.erp.cashier.dto.MovementResponse;
import com.erp.cashier.dto.MovementTransferRequest;
import com.erp.cashier.dto.MovementTransferResponse;
import com.erp.cashier.dto.RecentTransactionResponse;
import com.erp.cashier.dto.TransactionPageResponse;
import com.erp.cashier.security.JwtPayload;
import com.erp.cashier.service.MovementService;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Movement and transaction endpoints.
 *
 * @author ERP Cashier Team
 * @since 2025-01-20
 */
@RestController
@RequestMapping("/api")
public class MovementController {
    private final MovementService movementService;

    /**
     * Creates the movement controller.
     *
     * @param movementService movement service
     */
    public MovementController(MovementService movementService) {
        this.movementService = movementService;
    }

    /**
     * Lists movements for the authenticated cashier.
     *
     * @param sense movement sense
     * @param hasInvoice invoice filter
     * @param isTransfer transfer filter
     * @param type movement type
     * @param authentication authentication payload
     * @return movements
     */
    @GetMapping("/cashier/movements")
    @PreAuthorize("hasAuthority('ROLE_CASHIER')")
    public Flux<MovementResponse> listCashierMovements(
            @RequestParam(value = "sense", required = false) String sense,
            @RequestParam(value = "hasInvoice", required = false) Boolean hasInvoice,
            @RequestParam(value = "isTransfer", required = false) Boolean isTransfer,
            @RequestParam(value = "type", required = false) String type,
            Authentication authentication
    ) {
        return movementService.listCashierMovements(
                resolveUserId(authentication),
                sense,
                hasInvoice,
                isTransfer,
                type
        );
    }

    /**
     * Creates a register transfer movement.
     *
     * @param request transfer request
     * @param authentication authentication payload
     * @return transfer response
     */
    @PostMapping("/movements/transfer")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_CASHIER')")
    public Mono<MovementTransferResponse> transferMovement(
            @RequestBody MovementTransferRequest request,
            Authentication authentication
    ) {
        return movementService.transferBetweenRegisters(request, resolveUserId(authentication));
    }

    /**
     * Marks a movement as accounted.
     *
     * @param movementId movement identifier
     * @return accounting response
     */
    @PostMapping("/movements/{id}/account")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public Mono<MovementAccountResponse> accountMovement(@PathVariable("id") String movementId) {
        return movementService.accountMovement(movementId);
    }

    /**
     * Lists transactions.
     *
     * @param startDate start date filter
     * @param endDate end date filter
     * @param registerId register filter
     * @param cashierId cashier filter
     * @param type movement type
     * @param page page number
     * @param limit page size
     * @param authentication authentication payload
     * @return transaction page
     */
    @GetMapping("/transactions")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public Mono<TransactionPageResponse> listTransactions(
            @RequestParam(value = "startDate", required = false) String startDate,
            @RequestParam(value = "endDate", required = false) String endDate,
            @RequestParam(value = "registerId", required = false) String registerId,
            @RequestParam(value = "cashierId", required = false) String cashierId,
            @RequestParam(value = "type", required = false) String type,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "limit", required = false) Integer limit,
            Authentication authentication
    ) {
        JwtPayload payload = resolvePayload(authentication);
        String organizationId = payload != null ? trimToNull(payload.getOrganizationId()) : null;
        String agencyId = payload != null ? trimToNull(payload.getAgencyId()) : null;
        return movementService.listTransactions(
                startDate,
                endDate,
                registerId,
                cashierId,
                type,
                page,
                limit,
                organizationId,
                agencyId
        );
    }

    /**
     * Lists recent transactions.
     *
     * @param authentication authentication payload
     * @return recent transactions
     */
    @GetMapping("/transactions/recent")
    @PreAuthorize("isAuthenticated()")
    public Flux<RecentTransactionResponse> listRecentTransactions(Authentication authentication) {
        JwtPayload payload = resolvePayload(authentication);
        if (payload == null || !StringUtils.hasText(payload.getUserId())) {
            return Flux.error(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required."));
        }
        String orgId = trimToNull(payload.getOrganizationId());
        if (!StringUtils.hasText(orgId)) {
            return Flux.error(new ResponseStatusException(HttpStatus.FORBIDDEN, "Organization scope is required."));
        }
        String agencyId = trimToNull(payload.getAgencyId());
        return movementService.listRecentTransactions(orgId, agencyId);
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

    private JwtPayload resolvePayload(Authentication authentication) {
        if (authentication == null) {
            return null;
        }
        Object details = authentication.getDetails();
        if (details instanceof JwtPayload payload) {
            return payload;
        }
        return null;
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }
}
