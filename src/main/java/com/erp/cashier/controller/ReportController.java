package com.erp.cashier.controller;

import com.erp.cashier.dto.RegisterReportRequest;
import com.erp.cashier.security.JwtPayload;
import com.erp.cashier.service.ReportService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * Report endpoints returning PDF documents.
 *
 * @author ERP Cashier Team
 * @since 2025-01-20
 */
@RestController
@RequestMapping("/api/reports")
public class ReportController {
    private final ReportService reportService;

    /**
     * Creates the report controller.
     *
     * @param reportService report service
     */
    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    /**
     * Returns the transactions report PDF.
     *
     * @param startDate start date filter
     * @param endDate end date filter
     * @param type transaction type filter
     * @param registerId cash register filter
     * @param cashierId cashier filter
     * @param authentication authentication payload
     * @param response server response
     * @return PDF response
     */
    @GetMapping("/transactions")
    @PreAuthorize("hasAuthority('ROLE_ADMIN') or hasAuthority('ROLE_CASHIER')")
    public Mono<byte[]> getTransactionsReport(
            @RequestParam(value = "startDate", required = false) String startDate,
            @RequestParam(value = "endDate", required = false) String endDate,
            @RequestParam(value = "type", required = false) String type,
            @RequestParam(value = "registerId", required = false) String registerId,
            @RequestParam(value = "cashierId", required = false) String cashierId,
            Authentication authentication,
            ServerHttpResponse response
    ) {
        JwtPayload payload = resolvePayload(authentication);
        return reportService.generateTransactionsReport(
                        resolveOrganizationId(payload),
                        resolveAgencyId(payload),
                        resolveUserId(payload),
                        startDate,
                        endDate,
                        type,
                        registerId,
                        cashierId
                )
                .map(bytes -> applyPdfHeaders(response, bytes));
    }

    /**
     * Returns the register report PDF.
     *
     * @param registerId register identifier
     * @param request report request
     * @param authentication authentication payload
     * @param response server response
     * @return PDF response
     */
    @PostMapping("/register/{registerId}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN') or hasAuthority('ROLE_CASHIER')")
    public Mono<byte[]> getRegisterReport(
            @PathVariable("registerId") String registerId,
            @RequestBody RegisterReportRequest request,
            Authentication authentication,
            ServerHttpResponse response
    ) {
        JwtPayload payload = resolvePayload(authentication);
        return reportService.generateRegisterReport(
                        registerId,
                        request,
                        resolveOrganizationId(payload),
                        resolveAgencyId(payload),
                        resolveUserId(payload)
                )
                .map(bytes -> applyPdfHeaders(response, bytes));
    }

    /**
     * Returns the session report PDF.
     *
     * @param sessionId session identifier
     * @param authentication authentication payload
     * @param response server response
     * @return PDF response
     */
    @GetMapping("/session/{sessionId}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN') or hasAuthority('ROLE_CASHIER')")
    public Mono<byte[]> getSessionReport(
            @PathVariable("sessionId") String sessionId,
            Authentication authentication,
            ServerHttpResponse response
    ) {
        JwtPayload payload = resolvePayload(authentication);
        return reportService.generateSessionReport(
                        sessionId,
                        resolveOrganizationId(payload),
                        resolveAgencyId(payload),
                        resolveUserId(payload)
                )
                .map(bytes -> applyPdfHeaders(response, bytes));
    }

    /**
     * Returns the audit report PDF.
     *
     * @param type audit type filter
     * @param startOn start date filter
     * @param endOn end date filter
     * @param agencyId agency filter
     * @param limit row limit
     * @param authentication authentication payload
     * @param response server response
     * @return PDF response
     */
    @GetMapping("/audit")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public Mono<byte[]> getAuditReport(
            @RequestParam(value = "type", required = false) String type,
            @RequestParam(value = "start_on", required = false) String startOn,
            @RequestParam(value = "end_on", required = false) String endOn,
            @RequestParam(value = "agencyId", required = false) String agencyId,
            @RequestParam(value = "limit", required = false) Integer limit,
            Authentication authentication,
            ServerHttpResponse response
    ) {
        JwtPayload payload = resolvePayload(authentication);
        String scopedAgencyId = resolveAgencyId(payload);
        String effectiveAgency = scopedAgencyId != null ? scopedAgencyId : agencyId;
        return reportService.generateAuditReport(
                        type,
                        startOn,
                        endOn,
                        limit,
                        resolveOrganizationId(payload),
                        effectiveAgency,
                        resolveUserId(payload)
                )
                .map(bytes -> applyPdfHeaders(response, bytes));
    }

    private byte[] applyPdfHeaders(ServerHttpResponse response, byte[] payload) {
        if (response != null && !response.isCommitted()) {
            response.getHeaders().setContentType(MediaType.APPLICATION_PDF);
            response.getHeaders().set(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=report.pdf");
        }
        return payload;
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

    private String resolveUserId(JwtPayload payload) {
        return payload != null ? payload.getUserId() : null;
    }

    private String resolveOrganizationId(JwtPayload payload) {
        return payload != null ? payload.getOrganizationId() : null;
    }

    private String resolveAgencyId(JwtPayload payload) {
        return payload != null ? payload.getAgencyId() : null;
    }
}
