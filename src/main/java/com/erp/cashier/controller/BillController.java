package com.erp.cashier.controller;

import com.erp.cashier.dto.BillDetailResponse;
import com.erp.cashier.dto.BillListResponse;
import com.erp.cashier.dto.BillPageResponse;
import com.erp.cashier.dto.BillPaymentRequest;
import com.erp.cashier.dto.BillPaymentResponse;
import com.erp.cashier.security.JwtPayload;
import com.erp.cashier.service.BillService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Bill endpoints.
 *
 * @author ERP Cashier Team
 * @since 2025-01-20
 */
@RestController
@RequestMapping("/api")
public class BillController {
    private final BillService billService;

    /**
     * Creates the bill controller.
     *
     * @param billService bill service
     */
    public BillController(BillService billService) {
        this.billService = billService;
    }

    /**
     * Lists bills for the authenticated cashier.
     *
     * @param authentication authentication payload
     * @return bills
     */
    @GetMapping("/cashier/bills")
    @PreAuthorize("hasAuthority('ROLE_CASHIER')")
    public Flux<BillListResponse> listCashierBills(Authentication authentication) {
        return billService.listCashierBills(resolveOrganizationId(authentication));
    }

    /**
     * Gets a bill by id for the authenticated cashier.
     *
     * @param billId bill identifier
     * @param authentication authentication payload
     * @return bill
     */
    @GetMapping("/cashier/bills/{id}")
    @PreAuthorize("hasAuthority('ROLE_CASHIER')")
    public Mono<BillDetailResponse> getCashierBill(
            @PathVariable("id") String billId,
            Authentication authentication
    ) {
        return billService.getCashierBill(billId, resolveOrganizationId(authentication));
    }

    /**
     * Lists bills for admin users.
     *
     * @param page page number
     * @param limit page size
     * @param search search query
     * @return bill page
     */
    @GetMapping("/bills")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public Mono<BillPageResponse> listBills(
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "limit", required = false) Integer limit,
            @RequestParam(value = "search", required = false) String search
    ) {
        return billService.listBills(search, page, limit);
    }

    /**
     * Pays a bill.
     *
     * @param request payment request
     * @param authentication authentication payload
     * @return payment response
     */
    @PostMapping("/bills/pay")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_CASHIER')")
    public Mono<BillPaymentResponse> payBill(
            @RequestBody BillPaymentRequest request,
            Authentication authentication
    ) {
        return billService.payBill(request, resolveUserId(authentication));
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
