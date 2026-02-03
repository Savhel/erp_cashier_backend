package com.erp.cashier.repository;

import com.erp.cashier.model.CustomerProfile;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

/**
 * Reactive repository for customer profiles.
 *
 * @author ERP Cashier Team
 * @since 2025-01-20
 */
@Repository
public interface CustomerProfileRepository extends ReactiveCrudRepository<CustomerProfile, String> {
    /**
     * Finds a customer profile by person identifier.
     *
     * @param personId person identifier
     * @return matching profile
     */
    Mono<CustomerProfile> findByPersonId(String personId);
}
