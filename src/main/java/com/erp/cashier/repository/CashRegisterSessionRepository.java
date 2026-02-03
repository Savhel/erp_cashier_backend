package com.erp.cashier.repository;

import com.erp.cashier.model.CashRegisterSession;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

/**
 * Reactive repository for cash register sessions.
 *
 * @author ERP Cashier Team
 * @since 2025-01-02
 */
@Repository
public interface CashRegisterSessionRepository extends ReactiveCrudRepository<CashRegisterSession, String> {
    /**
     * Finds the latest active session for a cashier.
     *
     * @param openBy cashier identifier
     * @param state session state
     * @return active session when present
     */
    @Query("SELECT * FROM cash_register_session "
            + "WHERE open_by = :openBy AND state = :state "
            + "ORDER BY open_on DESC LIMIT 1")
    Mono<CashRegisterSession> findLatestByOpenByAndState(String openBy, String state);
}
