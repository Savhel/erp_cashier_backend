package com.erp.cashier.repository;

import com.erp.cashier.model.CashRegisterSession;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
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

    /**
     * Finds open sessions in the same agency, ordered by oldest first.
     *
     * @param agencyId agency identifier
     * @param state session state
     * @return open sessions
     */
    @Query("SELECT s.* FROM cash_register_session s "
            + "JOIN cash_register r ON r.id = s.cash_register_id "
            + "WHERE r.agency_id = :agencyId "
            + "AND s.state = :state "
            + "AND (s.is_locked = false OR s.is_locked IS NULL) "
            + "ORDER BY s.open_on ASC")
    Flux<CashRegisterSession> findOpenByAgency(String agencyId, String state);

    /**
     * Finds the latest session for a cash register by state.
     *
     * @param registerId cash register identifier
     * @param state session state
     * @return latest session when present
     */
    @Query("SELECT * FROM cash_register_session "
            + "WHERE cash_register_id = :registerId AND state = :state "
            + "ORDER BY open_on DESC NULLS LAST LIMIT 1")
    Mono<CashRegisterSession> findLatestByRegisterAndState(String registerId, String state);
}
