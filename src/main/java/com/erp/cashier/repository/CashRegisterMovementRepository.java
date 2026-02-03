package com.erp.cashier.repository;

import com.erp.cashier.model.CashRegisterMovement;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;

/**
 * Reactive repository for cash register movements.
 *
 * @author ERP Cashier Team
 * @since 2025-01-20
 */
@Repository
public interface CashRegisterMovementRepository extends ReactiveCrudRepository<CashRegisterMovement, String> {
}
