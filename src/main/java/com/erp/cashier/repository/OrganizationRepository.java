package com.erp.cashier.repository;

import com.erp.cashier.model.Organization;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;

/**
 * Reactive repository for organizations.
 *
 * @author ERP Cashier Team
 * @since 2025-01-02
 */
@Repository
public interface OrganizationRepository extends ReactiveCrudRepository<Organization, String> {
    /**
     * Lists active organizations ordered by name.
     *
     * @param isActive active flag
     * @return active organizations ordered by name
     */
    Flux<Organization> findByIsActiveOrderByNameAsc(Boolean isActive);
}
