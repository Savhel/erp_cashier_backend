package com.erp.cashier.repository;

import com.erp.cashier.model.CashierProfile;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

/**
 * Reactive repository for cashier profiles.
 *
 * @author ERP Cashier Team
 * @since 2025-01-02
 */
@Repository
public interface CashierProfileRepository extends ReactiveCrudRepository<CashierProfile, String> {
    /**
     * Finds a cashier profile by person identifier.
     *
     * @param personId person identifier
     * @return matching cashier profile
     */
    Mono<CashierProfile> findByPersonId(String personId);
}
