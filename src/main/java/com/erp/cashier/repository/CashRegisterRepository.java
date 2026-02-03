package com.erp.cashier.repository;

import com.erp.cashier.model.CashRegister;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;

/**
 * Reactive repository for cash registers.
 *
 * @author ERP Cashier Team
 * @since 2025-01-02
 */
@Repository
public interface CashRegisterRepository extends ReactiveCrudRepository<CashRegister, String> {
}
