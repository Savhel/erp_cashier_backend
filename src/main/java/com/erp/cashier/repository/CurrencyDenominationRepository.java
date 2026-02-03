package com.erp.cashier.repository;

import com.erp.cashier.model.CurrencyDenomination;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;

/**
 * Reactive repository for currency denominations.
 *
 * @author ERP Cashier Team
 * @since 2025-01-20
 */
@Repository
public interface CurrencyDenominationRepository extends ReactiveCrudRepository<CurrencyDenomination, String> {
}
