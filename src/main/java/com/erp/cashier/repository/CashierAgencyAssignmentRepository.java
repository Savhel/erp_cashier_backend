package com.erp.cashier.repository;

import com.erp.cashier.model.CashierAgencyAssignment;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

/**
 * Reactive repository for cashier agency assignments.
 *
 * @author ERP Cashier Team
 * @since 2025-01-20
 */
@Repository
public interface CashierAgencyAssignmentRepository extends ReactiveCrudRepository<CashierAgencyAssignment, String> {
    /**
     * Finds an assignment by cashier and agency.
     *
     * @param cashierId cashier identifier
     * @param agencyId agency identifier
     * @return matching assignment
     */
    Mono<CashierAgencyAssignment> findByCashierIdAndAgencyId(String cashierId, String agencyId);
}
