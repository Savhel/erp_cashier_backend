package com.erp.cashier.repository;

import com.erp.cashier.model.CashRegisterEvent;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;

/**
 * Reactive repository for cash register events.
 *
 * @author ERP Cashier Team
 * @since 2025-01-20
 */
@Repository
public interface CashRegisterEventRepository extends ReactiveCrudRepository<CashRegisterEvent, String> {
}
