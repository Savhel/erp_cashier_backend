package com.erp.cashier.controller;

import com.erp.cashier.dto.PublicAgencyResponse;
import com.erp.cashier.dto.PublicOrganizationResponse;
import com.erp.cashier.service.AgencyService;
import com.erp.cashier.service.OrganizationService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;

/**
 * Public lookup endpoints used by the frontend before authentication.
 *
 * @author ERP Cashier Team
 * @since 2025-01-02
 */
@RestController
@RequestMapping("/api/public")
public class PublicLookupController {
    private final OrganizationService organizationService;
    private final AgencyService agencyService;

    /**
     * Creates the public lookup controller.
     *
     * @param organizationService organization service
     * @param agencyService agency service
     */
    public PublicLookupController(OrganizationService organizationService, AgencyService agencyService) {
        this.organizationService = organizationService;
        this.agencyService = agencyService;
    }

    /**
     * Lists active organizations for the login screen.
     *
     * @return active organizations
     */
    @GetMapping("/organizations")
    public Flux<PublicOrganizationResponse> listOrganizations() {
        return organizationService.listActiveOrganizations();
    }

    /**
     * Lists active agencies for a given organization.
     *
     * @param organizationId organization identifier
     * @return active agencies
     */
    @GetMapping("/agencies")
    public Flux<PublicAgencyResponse> listAgencies(
            @RequestParam(name = "organizationId", required = false) String organizationId) {
        if (!StringUtils.hasText(organizationId)) {
            return Flux.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "organizationId is required"));
        }
        return agencyService.listActiveAgencies(organizationId);
    }
}
