package com.erp.cashier.service;

import com.erp.cashier.dto.PublicAgencyResponse;
import com.erp.cashier.model.Agency;
import com.erp.cashier.repository.AgencyRepository;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

/**
 * Agency service layer.
 *
 * @author ERP Cashier Team
 * @since 2025-01-02
 */
@Service
public class AgencyService {
    private final AgencyRepository agencyRepository;

    /**
     * Creates the agency service.
     *
     * @param agencyRepository agency repository
     */
    public AgencyService(AgencyRepository agencyRepository) {
        this.agencyRepository = agencyRepository;
    }

    /**
     * Lists active agencies for an organization.
     *
     * @param organizationId organization identifier
     * @return active agencies
     */
    public Flux<PublicAgencyResponse> listActiveAgencies(String organizationId) {
        return agencyRepository.findByOrganizationIdAndIsActiveOrderByNameAsc(organizationId, true)
                .map(this::toPublicResponse);
    }

    private PublicAgencyResponse toPublicResponse(Agency agency) {
        return new PublicAgencyResponse(
                agency.getId(),
                agency.getName(),
                agency.getCountry(),
                agency.getTown(),
                agency.getNeighborhood()
        );
    }
}
