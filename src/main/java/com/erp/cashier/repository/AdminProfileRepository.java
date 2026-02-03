package com.erp.cashier.repository;

import com.erp.cashier.model.AdminProfile;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

/**
 * Reactive repository for admin profiles.
 *
 * @author ERP Cashier Team
 * @since 2025-01-02
 */
@Repository
public interface AdminProfileRepository extends ReactiveCrudRepository<AdminProfile, String> {
    /**
     * Finds an admin profile by person identifier.
     *
     * @param personId person identifier
     * @return matching admin profile
     */
    Mono<AdminProfile> findByPersonId(String personId);
}
