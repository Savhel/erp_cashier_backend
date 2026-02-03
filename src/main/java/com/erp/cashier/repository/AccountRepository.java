package com.erp.cashier.repository;

import com.erp.cashier.model.Account;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

/**
 * Reactive repository for accounts.
 *
 * @author ERP Cashier Team
 * @since 2025-01-20
 */
@Repository
public interface AccountRepository extends ReactiveCrudRepository<Account, String> {
    /**
     * Finds an account by account number.
     *
     * @param accountNumber account number
     * @return matching account
     */
    Mono<Account> findByAccountNumber(String accountNumber);
}
