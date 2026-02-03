package com.erp.cashier.repository;

import com.erp.cashier.model.CashReconciliation;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;

/**
 * Reactive repository for cash reconciliations.
 *
 * @author ERP Cashier Team
 * @since 2025-01-20
 */
@Repository
public interface CashReconciliationRepository extends ReactiveCrudRepository<CashReconciliation, String> {
}
