package com.erp.cashier.repository;

import com.erp.cashier.model.RtSyncState;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;

/**
 * Reactive repository for RT sync state.
 *
 * @author ERP Cashier Team
 * @since 2026-02-01
 */
@Repository
public interface RtSyncStateRepository extends ReactiveCrudRepository<RtSyncState, String> {
}
