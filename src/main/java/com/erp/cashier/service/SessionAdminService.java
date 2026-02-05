package com.erp.cashier.service;

import com.erp.cashier.dto.CashRegisterTicketingDenominationResponse;
import com.erp.cashier.dto.CashRegisterTicketingDetailResponse;
import com.erp.cashier.dto.CashRegisterUserResponse;
import com.erp.cashier.dto.OpenSessionRequest;
import com.erp.cashier.dto.SessionCashRegisterResponse;
import com.erp.cashier.dto.SessionMovementResponse;
import com.erp.cashier.dto.SessionReconciliationResponse;
import com.erp.cashier.dto.SessionResponse;
import com.erp.cashier.model.CashRegisterSession;
import com.erp.cashier.model.CashReconciliation;
import com.erp.cashier.repository.CashRegisterSessionRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.r2dbc.spi.Row;
import io.r2dbc.spi.RowMetadata;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.data.relational.core.query.Criteria;
import org.springframework.data.relational.core.query.Query;
import org.springframework.stereotype.Service;
import org.springframework.transaction.ReactiveTransactionManager;
import org.springframework.transaction.reactive.TransactionalOperator;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Admin service for managing sessions.
 *
 * @author ERP Cashier Team
 * @since 2025-01-02
 */
@Service
public class SessionAdminService {
    private static final String STATE_OPEN = "ouverte";
    private static final String STATE_CLOSING = "en_cloture";

    private final R2dbcEntityTemplate entityTemplate;
    private final CashRegisterSessionRepository sessionRepository;
    private final ObjectMapper objectMapper;
    private final TransactionalOperator transactionalOperator;
    private final AuditService auditService;

    /**
     * Creates the session admin service.
     *
     * @param entityTemplate entity template
     * @param objectMapper object mapper
     * @param transactionManager reactive transaction manager
     * @param auditService audit service
     */
    public SessionAdminService(
            R2dbcEntityTemplate entityTemplate,
            CashRegisterSessionRepository sessionRepository,
            ObjectMapper objectMapper,
            ReactiveTransactionManager transactionManager,
            AuditService auditService
    ) {
        this.entityTemplate = entityTemplate;
        this.sessionRepository = sessionRepository;
        this.objectMapper = objectMapper;
        this.transactionalOperator = TransactionalOperator.create(transactionManager);
        this.auditService = auditService;
    }

    /**
     * Lists sessions for admins.
     *
     * @param organizationId organization identifier when scoped
     * @param agencyId agency identifier when scoped
     * @param restrictToAgency true when the caller is an agency admin
     * @param restrictToOrganization true when the caller is an organization admin
     * @return sessions
     */
    public Flux<SessionResponse> listSessions(
            String organizationId,
            String agencyId,
            boolean restrictToAgency,
            boolean restrictToOrganization
    ) {
        if (restrictToAgency && !StringUtils.hasText(agencyId)) {
            return Flux.error(new ResponseStatusException(HttpStatus.FORBIDDEN, "Agency scope is required."));
        }
        if (restrictToOrganization && !StringUtils.hasText(organizationId)) {
            return Flux.error(new ResponseStatusException(HttpStatus.FORBIDDEN, "Organization scope is required."));
        }

        StringBuilder sql = new StringBuilder();
        sql.append(baseSessionSelect());
        sql.append("WHERE 1=1 ");
        if (restrictToAgency) {
            sql.append("AND r.agency_id = :agencyId ");
        }
        if (restrictToOrganization) {
            sql.append("AND a.organization_id = :organizationId ");
        }
        sql.append("ORDER BY s.open_on DESC");

        DatabaseClient.GenericExecuteSpec spec = entityTemplate.getDatabaseClient().sql(sql.toString());
        if (restrictToAgency) {
            spec = spec.bind("agencyId", agencyId);
        }
        if (restrictToOrganization) {
            spec = spec.bind("organizationId", organizationId);
        }
        return spec.map(this::mapSessionRow)
                .all()
                .flatMap(this::enrichSession);
    }

    /**
     * Lists sessions for a cashier.
     *
     * @param cashierId cashier identifier
     * @param organizationId organization identifier when scoped
     * @param agencyId agency identifier when scoped
     * @return sessions
     */
    public Flux<SessionResponse> listSessionsByCashier(
            String cashierId,
            String organizationId,
            String agencyId
    ) {
        if (!StringUtils.hasText(cashierId)) {
            return Flux.error(new ResponseStatusException(HttpStatus.FORBIDDEN, "Cashier scope is required."));
        }

        StringBuilder sql = new StringBuilder();
        sql.append(baseSessionSelect());
        sql.append("WHERE s.open_by = :cashierId ");
        if (StringUtils.hasText(organizationId)) {
            sql.append("AND a.organization_id = :organizationId ");
        }
        if (StringUtils.hasText(agencyId)) {
            sql.append("AND r.agency_id = :agencyId ");
        }
        sql.append("ORDER BY s.open_on DESC");

        DatabaseClient.GenericExecuteSpec spec = entityTemplate.getDatabaseClient()
                .sql(sql.toString())
                .bind("cashierId", cashierId);
        if (StringUtils.hasText(organizationId)) {
            spec = spec.bind("organizationId", organizationId);
        }
        if (StringUtils.hasText(agencyId)) {
            spec = spec.bind("agencyId", agencyId);
        }
        return spec.map(this::mapSessionRow)
                .all()
                .flatMap(this::enrichSession);
    }

    /**
     * Opens a new session.
     *
     * @param request open session request
     * @param actorId authenticated user identifier
     * @param restrictToCashier true when caller is a cashier
     * @param organizationId organization identifier when scoped
     * @param agencyId agency identifier when scoped
     * @param restrictToOrganization true when scoped to an organization
     * @param restrictToAgency true when scoped to an agency
     * @return opened session
     */
    public Mono<SessionResponse> openSession(
            OpenSessionRequest request,
            String actorId,
            boolean restrictToCashier,
            String organizationId,
            String agencyId,
            boolean restrictToOrganization,
            boolean restrictToAgency
    ) {
        if (request == null) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Session payload is required."));
        }
        String registerId = trimToNull(request.getCashRegisterId());
        if (!StringUtils.hasText(registerId)) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "cash_register_id is required"));
        }
        BigDecimal initialFunds = request.getTheoricalInitialFunds();
        if (initialFunds == null) {
            return Mono.error(new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "theorical_initial_funds is required"
            ));
        }

        String resolvedActorId = trimToNull(actorId);
        String openBy = trimToNull(request.getOpenBy());
        if (!StringUtils.hasText(openBy)) {
            openBy = resolvedActorId;
        }
        if (!StringUtils.hasText(openBy)) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "open_by is required"));
        }
        if (restrictToCashier && StringUtils.hasText(resolvedActorId) && !resolvedActorId.equals(openBy)) {
            return Mono.error(new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Cashiers can only open their own sessions."
            ));
        }

        return fetchRegister(registerId)
                .then(assertSessionScope(
                        registerId,
                        organizationId,
                        agencyId,
                        restrictToAgency,
                        restrictToOrganization
                ))
                .then(Mono.when(
                        assertRegisterNotLocked(registerId),
                        assertCashierNoOpenSession(openBy),
                        assertRegisterNoActiveSession(registerId)
                ))
                .then(createSession(registerId, openBy, initialFunds, resolvedActorId))
                .flatMap(this::fetchSessionById);
    }

    /**
     * Closes a session and creates a reconciliation entry.
     *
     * @param sessionId session identifier
     * @param physicalTotal physical total
     * @param actorId authenticated user identifier
     * @param restrictToCashier true when caller is a cashier
     * @param organizationId organization identifier when scoped
     * @param agencyId agency identifier when scoped
     * @param restrictToOrganization true when scoped to an organization
     * @param restrictToAgency true when scoped to an agency
     * @return closed session
     */
    public Mono<SessionResponse> closeSession(
            String sessionId,
            BigDecimal physicalTotal,
            String actorId,
            boolean restrictToCashier,
            String organizationId,
            String agencyId,
            boolean restrictToOrganization,
            boolean restrictToAgency
    ) {
        String resolvedSessionId = trimToNull(sessionId);
        if (!StringUtils.hasText(resolvedSessionId)) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Session ID is required."));
        }
        if (physicalTotal == null) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "physical_total is required"));
        }
        String resolvedActorId = trimToNull(actorId);
        if (!StringUtils.hasText(resolvedActorId)) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Actor ID is required"));
        }

        return resolveSessionForClose(resolvedSessionId)
                .flatMap(session -> assertSessionScope(
                                session.getCashRegisterId(),
                                organizationId,
                                agencyId,
                                restrictToAgency,
                                restrictToOrganization
                        )
                        .thenReturn(session))
                .flatMap(session -> {
                    if (!STATE_OPEN.equals(session.getState())) {
                        return Mono.error(new ResponseStatusException(
                                HttpStatus.CONFLICT,
                                "Session is not open."
                        ));
                    }
                    if (Boolean.TRUE.equals(session.getIsLocked())) {
                        return Mono.error(new ResponseStatusException(
                                HttpStatus.CONFLICT,
                                "Session is locked."
                        ));
                    }
                    if (restrictToCashier && !resolvedActorId.equals(session.getOpenBy())) {
                        return Mono.error(new ResponseStatusException(
                                HttpStatus.FORBIDDEN,
                                "Cashiers can only close their own sessions."
                        ));
                    }

                    return computeTheoricalTotal(session)
                            .flatMap(theoricalTotal -> {
                                session.setState(STATE_CLOSING);
                                session.setCloseOn(LocalDateTime.now());
                                session.setCloseBy(resolvedActorId);
                                session.setTheoricalCloseFunds(theoricalTotal);

                                Map<String, Object> payload = new java.util.LinkedHashMap<>();
                                putIfPresent(payload, "open_by", session.getOpenBy());
                                putIfPresent(payload, "close_by", resolvedActorId);
                                putIfPresent(payload, "physical_total", physicalTotal);
                                putIfPresent(payload, "theorical_total", theoricalTotal);

                                Mono<String> updateFlow = entityTemplate.update(session)
                                        .map(CashRegisterSession::getId)
                                        .flatMap(id -> insertReconciliation(id, physicalTotal, theoricalTotal, resolvedActorId)
                                                .thenReturn(id))
                                        .flatMap(id -> recordSessionEvent(
                                                "fermeture",
                                                id,
                                                session.getCashRegisterId(),
                                                resolvedActorId,
                                                payload,
                                                "session:" + id + ":close"
                                        ).thenReturn(id));

                                return transactionalOperator.transactional(updateFlow)
                                        .flatMap(this::fetchSessionById);
                            });
                });
    }

    /**
     * Locks or unlocks a session.
     *
     * @param sessionId session identifier
     * @param locked lock flag
     * @param actorId authenticated user identifier
     * @param organizationId organization identifier when scoped
     * @param agencyId agency identifier when scoped
     * @param restrictToAgency true when scoped to an agency
     * @param restrictToOrganization true when scoped to an organization
     * @return updated session
     */
    public Mono<SessionResponse> setSessionLocked(
            String sessionId,
            boolean locked,
            String actorId,
            String organizationId,
            String agencyId,
            boolean restrictToAgency,
            boolean restrictToOrganization
    ) {
        String resolvedSessionId = trimToNull(sessionId);
        if (!StringUtils.hasText(resolvedSessionId)) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Session ID is required."));
        }
        String resolvedActorId = trimToNull(actorId);
        return findSessionEntity(resolvedSessionId)
                .flatMap(session -> assertSessionScope(
                                session.getCashRegisterId(),
                                organizationId,
                                agencyId,
                                restrictToAgency,
                                restrictToOrganization
                        )
                        .thenReturn(session))
                .flatMap(session -> {
                    session.setIsLocked(locked);
                    Map<String, Object> payload = new java.util.LinkedHashMap<>();
                    putIfPresent(payload, "locked", locked);
                    putIfPresent(payload, "changed_by", resolvedActorId);
                    return entityTemplate.update(session)
                            .map(CashRegisterSession::getId)
                            .flatMap(id -> recordSessionEvent(
                                    locked ? "verrouillage" : "deverrouillage",
                                    id,
                                    session.getCashRegisterId(),
                                    resolvedActorId,
                                    payload,
                                    "session:" + id + ":lock:" + locked
                            ).thenReturn(id));
                })
                .flatMap(this::fetchSessionById);
    }
    
    private Mono<Void> assertSessionScope(
            String registerId,
            String organizationId,
            String agencyId,
            boolean restrictToAgency,
            boolean restrictToOrganization
    ) {
        if (!restrictToAgency && !restrictToOrganization) {
            return Mono.empty();
        }
        String resolvedRegisterId = trimToNull(registerId);
        if (!StringUtils.hasText(resolvedRegisterId)) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cash register scope is required."));
        }
        String sql = "SELECT r.agency_id, a.organization_id "
                + "FROM cash_register r "
                + "LEFT JOIN agency a ON a.id = r.agency_id "
                + "WHERE r.id = :registerId";
        return entityTemplate.getDatabaseClient()
                .sql(sql)
                .bind("registerId", resolvedRegisterId)
                .map((row, meta) -> new RegisterScope(
                        row.get("agency_id", String.class),
                        row.get("organization_id", String.class)
                ))
                .one()
                .switchIfEmpty(Mono.error(new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Cash register not found."
                )))
                .flatMap(scope -> {
                    if (restrictToAgency && (!StringUtils.hasText(agencyId)
                            || !agencyId.equals(scope.agencyId()))) {
                        return Mono.error(new ResponseStatusException(
                                HttpStatus.FORBIDDEN,
                                "You can only manage sessions from your agency."
                        ));
                    }
                    if (restrictToOrganization && (!StringUtils.hasText(organizationId)
                            || !organizationId.equals(scope.organizationId()))) {
                        return Mono.error(new ResponseStatusException(
                                HttpStatus.FORBIDDEN,
                                "You can only manage sessions from your organization."
                        ));
                    }
                    return Mono.empty();
                });
    }

    private Mono<Void> fetchRegister(String registerId) {
        String sql = "SELECT 1 FROM cash_register WHERE id = :registerId";
        return entityTemplate.getDatabaseClient()
                .sql(sql)
                .bind("registerId", registerId)
                .map((row, meta) -> 1)
                .first()
                .switchIfEmpty(Mono.error(new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Cash register not found."
                )))
                .then();
    }

    private Mono<String> createSession(
            String registerId,
            String openBy,
            BigDecimal initialFunds,
            String authorId
    ) {
        CashRegisterSession session = new CashRegisterSession();
        session.setId(UUID.randomUUID().toString());
        session.setCashRegisterId(registerId);
        session.setOpenBy(openBy);
        session.setState(STATE_OPEN);
        session.setOpenOn(LocalDateTime.now());
        session.setTheoricalInitialFunds(initialFunds);
        session.setIsLocked(false);

        Map<String, Object> payload = new java.util.LinkedHashMap<>();
        putIfPresent(payload, "open_by", openBy);
        putIfPresent(payload, "initial_funds", initialFunds);

        Mono<String> insertFlow = entityTemplate.insert(CashRegisterSession.class)
                .using(session)
                .map(saved -> saved.getId())
                .flatMap(sessionId -> recordSessionEvent(
                        "ouverture",
                        sessionId,
                        registerId,
                        StringUtils.hasText(authorId) ? authorId : openBy,
                        payload,
                        "session:" + sessionId + ":open"
                )
                        .thenReturn(sessionId));

        return transactionalOperator.transactional(insertFlow);
    }

    private Mono<Void> recordSessionEvent(
            String type,
            String sessionId,
            String cashRegisterId,
            String authorId,
            Map<String, Object> payload,
            String idempotency
    ) {
        if (!StringUtils.hasText(sessionId)) {
            return Mono.empty();
        }
        Map<String, Object> data = new java.util.LinkedHashMap<>();
        if (payload != null) {
            data.putAll(payload);
        }
        putIfPresent(data, "session_id", sessionId);
        putIfPresent(data, "cash_register_id", cashRegisterId);
        return auditService.recordSubjectEvent(
                type,
                authorId,
                sessionId,
                null,
                "session",
                sessionId,
                idempotency,
                data
        ).onErrorResume(ex -> Mono.empty());
    }

    private void putIfPresent(Map<String, Object> payload, String key, Object value) {
        if (payload == null || !StringUtils.hasText(key) || value == null) {
            return;
        }
        payload.put(key, value);
    }

    private Mono<Void> insertReconciliation(
            String sessionId,
            BigDecimal physicalTotal,
            BigDecimal theoricalTotal,
            String authorId
    ) {
        CashReconciliation reconciliation = new CashReconciliation();
        reconciliation.setId(UUID.randomUUID().toString());
        reconciliation.setSessionId(sessionId);
        reconciliation.setPhysicalTotal(physicalTotal);
        reconciliation.setTheoricalTotal(theoricalTotal);
        reconciliation.setDifference(physicalTotal.subtract(theoricalTotal));
        reconciliation.setStatut("en_attente");
        reconciliation.setCreateOn(LocalDateTime.now());
        reconciliation.setCreateBy(authorId);
        return entityTemplate.insert(CashReconciliation.class)
                .using(reconciliation)
                .then();
    }

    private Mono<CashRegisterSession> resolveSessionForClose(String sessionOrRegisterId) {
        return sessionRepository.findById(sessionOrRegisterId)
                .switchIfEmpty(sessionRepository.findLatestByRegisterAndState(sessionOrRegisterId, STATE_OPEN))
                .switchIfEmpty(Mono.error(new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Session not found."
                )));
    }

    private Mono<BigDecimal> computeTheoricalTotal(CashRegisterSession session) {
        String sessionId = session != null ? session.getId() : null;
        BigDecimal initial = session != null && session.getTheoricalInitialFunds() != null
                ? session.getTheoricalInitialFunds()
                : BigDecimal.ZERO;
        if (!StringUtils.hasText(sessionId)) {
            return Mono.just(initial);
        }
        String sql = "SELECT "
                + "COALESCE(SUM(CASE WHEN sense = 'entree' THEN amount ELSE 0 END), 0) AS total_in, "
                + "COALESCE(SUM(CASE WHEN sense = 'sortie' THEN amount ELSE 0 END), 0) AS total_out "
                + "FROM cash_register_movement "
                + "WHERE session_id = :sessionId "
                + "AND (is_deleted = false OR is_deleted IS NULL)";
        return entityTemplate.getDatabaseClient()
                .sql(sql)
                .bind("sessionId", sessionId)
                .map((row, meta) -> {
                    BigDecimal totalIn = row.get("total_in", BigDecimal.class);
                    BigDecimal totalOut = row.get("total_out", BigDecimal.class);
                    if (totalIn == null) {
                        totalIn = BigDecimal.ZERO;
                    }
                    if (totalOut == null) {
                        totalOut = BigDecimal.ZERO;
                    }
                    return initial.add(totalIn).subtract(totalOut);
                })
                .one()
                .defaultIfEmpty(initial);
    }

    private Mono<CashRegisterSession> findSessionEntity(String sessionId) {
        return entityTemplate.selectOne(
                        Query.query(Criteria.where("id").is(sessionId)),
                        CashRegisterSession.class
                )
                .switchIfEmpty(Mono.error(new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Session not found."
                )));
    }

    private Mono<SessionResponse> fetchSessionById(String sessionId) {
        StringBuilder sql = new StringBuilder();
        sql.append(baseSessionSelect());
        sql.append("WHERE s.id = :sessionId");

        return entityTemplate.getDatabaseClient()
                .sql(sql.toString())
                .bind("sessionId", sessionId)
                .map(this::mapSessionRow)
                .one()
                .flatMap(this::enrichSession)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Session not found")));
    }

    private Mono<Void> assertRegisterNotLocked(String registerId) {
        String sql = "SELECT is_locked FROM cash_register_session "
                + "WHERE cash_register_id = :registerId "
                + "ORDER BY open_on DESC NULLS LAST LIMIT 1";
        return entityTemplate.getDatabaseClient()
                .sql(sql)
                .bind("registerId", registerId)
                .map((row, meta) -> row.get("is_locked", Boolean.class))
                .one()
                .filter(Boolean.TRUE::equals)
                .flatMap(ignore -> Mono.error(new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "This cash register is locked."
                )))
                .then();
    }

    private Mono<Void> assertCashierNoOpenSession(String cashierId) {
        String sql = "SELECT 1 FROM cash_register_session "
                + "WHERE open_by = :cashierId AND state = :openState LIMIT 1";
        return entityTemplate.getDatabaseClient()
                .sql(sql)
                .bind("cashierId", cashierId)
                .bind("openState", STATE_OPEN)
                .map((row, meta) -> 1)
                .first()
                .flatMap(ignore -> Mono.error(new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Cashier already has an active session."
                )))
                .then();
    }

    private Mono<Void> assertRegisterNoActiveSession(String registerId) {
        String sql = "SELECT 1 FROM cash_register_session "
                + "WHERE cash_register_id = :registerId "
                + "AND state IN (:openState, :closingState) LIMIT 1";
        return entityTemplate.getDatabaseClient()
                .sql(sql)
                .bind("registerId", registerId)
                .bind("openState", STATE_OPEN)
                .bind("closingState", STATE_CLOSING)
                .map((row, meta) -> 1)
                .first()
                .flatMap(ignore -> Mono.error(new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "There is already an active session for this cash register."
                )))
                .then();
    }

    private Mono<SessionResponse> enrichSession(SessionResponse session) {
        Mono<List<SessionMovementResponse>> movements = fetchMovements(session.getId()).collectList();
        Mono<List<CashRegisterTicketingDetailResponse>> ticketing = fetchTicketingDetails(session.getId())
                .collectList();
        Mono<Optional<SessionReconciliationResponse>> reconciliation = fetchReconciliation(session.getId())
                .map(Optional::of)
                .defaultIfEmpty(Optional.empty());

        return Mono.zip(movements, ticketing, reconciliation)
                .map(tuple -> {
                    session.setMovements(tuple.getT1());
                    session.setTicketingDetails(tuple.getT2());
                    session.setReconciliation(tuple.getT3().orElse(null));
                    return session;
                });
    }

    private Flux<SessionMovementResponse> fetchMovements(String sessionId) {
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT m.id, m.sense, m.amount, m.reason, m.create_on, ");
        sql.append("p.user_name AS creator_user_name, p.user_first_name AS creator_user_first_name ");
        sql.append("FROM cash_register_movement m ");
        sql.append("LEFT JOIN person p ON p.id = m.create_by ");
        sql.append("WHERE m.session_id = :sessionId ");
        sql.append("ORDER BY m.create_on DESC");

        return entityTemplate.getDatabaseClient()
                .sql(sql.toString())
                .bind("sessionId", sessionId)
                .map(this::mapMovementRow)
                .all()
                .flatMap(this::enrichMovement);
    }

    private Mono<SessionMovementResponse> enrichMovement(SessionMovementResponse movement) {
        return fetchMovementTicketingDetails(movement.getId())
                .collectList()
                .map(ticketingDetails -> {
                    movement.setTicketingDetails(ticketingDetails);
                    return movement;
                });
    }

    private Flux<CashRegisterTicketingDetailResponse> fetchMovementTicketingDetails(String movementId) {
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT d.id, d.connection_type, d.quantity, d.value, d.total, ");
        sql.append("denom.value AS denomination_value, denom.label AS denomination_label ");
        sql.append("FROM event_ticketing_detail d ");
        sql.append("LEFT JOIN currency_denomination denom ON denom.id = d.denomination_id ");
        sql.append("WHERE d.movement_id = :movementId ");
        sql.append("ORDER BY d.id");

        return entityTemplate.getDatabaseClient()
                .sql(sql.toString())
                .bind("movementId", movementId)
                .map(this::mapTicketingRow)
                .all();
    }

    private Flux<CashRegisterTicketingDetailResponse> fetchTicketingDetails(String sessionId) {
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT d.id, d.connection_type, d.quantity, d.value, d.total, ");
        sql.append("denom.value AS denomination_value, denom.label AS denomination_label ");
        sql.append("FROM event_ticketing_detail d ");
        sql.append("LEFT JOIN currency_denomination denom ON denom.id = d.denomination_id ");
        sql.append("WHERE d.session_id = :sessionId ");
        sql.append("ORDER BY d.id");

        return entityTemplate.getDatabaseClient()
                .sql(sql.toString())
                .bind("sessionId", sessionId)
                .map(this::mapTicketingRow)
                .all();
    }

    private Mono<SessionReconciliationResponse> fetchReconciliation(String sessionId) {
        String sql = "SELECT id, theorical_total, physical_total, difference "
                + "FROM cash_reconciliation WHERE session_id = :sessionId LIMIT 1";

        return entityTemplate.getDatabaseClient()
                .sql(sql)
                .bind("sessionId", sessionId)
                .map(this::mapReconciliationRow)
                .one();
    }

    private SessionResponse mapSessionRow(Row row, RowMetadata metadata) {
        SessionResponse session = new SessionResponse();
        session.setId(row.get("id", String.class));
        session.setState(row.get("state", String.class));
        session.setOpenOn(row.get("open_on", LocalDateTime.class));
        session.setCloseOn(row.get("close_on", LocalDateTime.class));
        session.setOpenBy(row.get("open_by", String.class));
        session.setTheoricalInitialFunds(row.get("theorical_initial_funds", BigDecimal.class));
        session.setTheoricalCloseFunds(row.get("theorical_close_funds", BigDecimal.class));
        session.setIsLocked(Boolean.TRUE.equals(row.get("is_locked", Boolean.class)));
        session.setCashRegister(mapCashRegister(row));
        session.setOpener(mapUser(
                row.get("opener_user_name", String.class),
                row.get("opener_user_first_name", String.class)
        ));
        session.setCloser(mapUser(null, row.get("closer_user_first_name", String.class)));
        session.setMovements(new ArrayList<>());
        session.setTicketingDetails(new ArrayList<>());
        return session;
    }

    private SessionCashRegisterResponse mapCashRegister(Row row) {
        String registerId = row.get("register_id", String.class);
        String town = row.get("register_town", String.class);
        String country = row.get("register_country", String.class);
        String agencyId = row.get("register_agency_id", String.class);
        if (!StringUtils.hasText(registerId) && !StringUtils.hasText(town) && !StringUtils.hasText(country)) {
            return null;
        }
        return new SessionCashRegisterResponse(registerId, town, country, agencyId);
    }

    private SessionMovementResponse mapMovementRow(Row row, RowMetadata metadata) {
        CashRegisterUserResponse creator = mapUser(
                row.get("creator_user_name", String.class),
                row.get("creator_user_first_name", String.class)
        );
        SessionMovementResponse movement = new SessionMovementResponse();
        movement.setId(row.get("id", String.class));
        movement.setSense(mapSense(row.get("sense", String.class)));
        movement.setAmount(row.get("amount", BigDecimal.class));
        movement.setReason(row.get("reason", String.class));
        movement.setCreateOn(row.get("create_on", LocalDateTime.class));
        movement.setCreator(creator);
        movement.setTicketingDetails(new ArrayList<>());
        return movement;
    }

    private CashRegisterTicketingDetailResponse mapTicketingRow(Row row, RowMetadata metadata) {
        CashRegisterTicketingDenominationResponse denomination = null;
        BigDecimal denominationValue = row.get("denomination_value", BigDecimal.class);
        String denominationLabel = row.get("denomination_label", String.class);
        if (denominationValue != null || StringUtils.hasText(denominationLabel)) {
            denomination = new CashRegisterTicketingDenominationResponse(denominationValue, denominationLabel);
        }
        return new CashRegisterTicketingDetailResponse(
                row.get("id", String.class),
                row.get("connection_type", String.class),
                row.get("quantity", Integer.class),
                row.get("value", BigDecimal.class),
                row.get("total", BigDecimal.class),
                denomination
        );
    }

    private SessionReconciliationResponse mapReconciliationRow(Row row, RowMetadata metadata) {
        return new SessionReconciliationResponse(
                row.get("id", String.class),
                row.get("theorical_total", BigDecimal.class),
                row.get("physical_total", BigDecimal.class),
                row.get("difference", BigDecimal.class)
        );
    }

    private CashRegisterUserResponse mapUser(String userName, String userFirstName) {
        if (!StringUtils.hasText(userName) && !StringUtils.hasText(userFirstName)) {
            return null;
        }
        return new CashRegisterUserResponse(userName, userFirstName);
    }

    private String mapSense(String sense) {
        if (!StringUtils.hasText(sense)) {
            return null;
        }
        String normalized = sense.trim().toLowerCase();
        if ("entree".equals(normalized) || "in".equals(normalized)) {
            return "entry";
        }
        if ("sortie".equals(normalized) || "out".equals(normalized)) {
            return "exit";
        }
        return normalized;
    }

    private String baseSessionSelect() {
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT s.id, s.state, s.open_on, s.close_on, s.open_by, ");
        sql.append("s.theorical_initial_funds, s.theorical_close_funds, s.is_locked, ");
        sql.append("r.id AS register_id, r.town AS register_town, r.country AS register_country, ");
        sql.append("r.agency_id AS register_agency_id, ");
        sql.append("op.user_name AS opener_user_name, op.user_first_name AS opener_user_first_name, ");
        sql.append("cl.user_first_name AS closer_user_first_name ");
        sql.append("FROM cash_register_session s ");
        sql.append("LEFT JOIN cash_register r ON r.id = s.cash_register_id ");
        sql.append("LEFT JOIN agency a ON a.id = r.agency_id ");
        sql.append("LEFT JOIN person op ON op.id = s.open_by ");
        sql.append("LEFT JOIN person cl ON cl.id = s.close_by ");
        return sql.toString();
    }

    private String serializePayload(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to serialize session payload.",
                    ex
            );
        }
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private record RegisterScope(String agencyId, String organizationId) {
    }
}
