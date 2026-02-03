package com.erp.cashier.repository;

import com.erp.cashier.model.CashierManageCashRegister;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;

/**
 * Reactive repository for cashier register assignments.
 *
 * @author ERP Cashier Team
 * @since 2025-01-20
 */
@Repository
public interface CashierManageCashRegisterRepository extends ReactiveCrudRepository<CashierManageCashRegister, String> {
}
