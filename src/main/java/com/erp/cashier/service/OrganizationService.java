package com.erp.cashier.service;

import com.erp.cashier.dto.PublicOrganizationResponse;
import com.erp.cashier.model.Organization;
import com.erp.cashier.repository.OrganizationRepository;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

/**
 * Organization service layer.
 *
 * @author ERP Cashier Team
 * @since 2025-01-02
 */
@Service
public class OrganizationService {
    private final OrganizationRepository organizationRepository;

    /**
     * Creates the organization service.
     *
     * @param organizationRepository organization repository
     */
    public OrganizationService(OrganizationRepository organizationRepository) {
        this.organizationRepository = organizationRepository;
    }

    /**
     * Lists active organizations for public lookup.
     *
     * @return active organizations
     */
    public Flux<PublicOrganizationResponse> listActiveOrganizations() {
        return organizationRepository.findByIsActiveOrderByNameAsc(true)
                .map(this::toPublicResponse);
    }

    private PublicOrganizationResponse toPublicResponse(Organization organization) {
        return new PublicOrganizationResponse(
                organization.getId(),
                organization.getName(),
                organization.getCountry()
        );
    }
}
