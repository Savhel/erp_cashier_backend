package com.erp.cashier.repository;

import com.erp.cashier.model.Agency;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;

/**
 * Reactive repository for agencies.
 *
 * @author ERP Cashier Team
 * @since 2025-01-02
 */
@Repository
public interface AgencyRepository extends ReactiveCrudRepository<Agency, String> {
    /**
     * Lists active agencies for an organization ordered by name.
     *
     * @param organizationId organization identifier
     * @param isActive active flag
     * @return active agencies ordered by name
     */
    Flux<Agency> findByOrganizationIdAndIsActiveOrderByNameAsc(String organizationId, Boolean isActive);
}
