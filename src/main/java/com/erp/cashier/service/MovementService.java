package com.erp.cashier.service;

import com.erp.cashier.dto.CashRegisterSummaryResponse;
import com.erp.cashier.dto.MovementAccountResponse;
import com.erp.cashier.dto.MovementPartyResponse;
import com.erp.cashier.dto.MovementPersonResponse;
import com.erp.cashier.dto.MovementResponse;
import com.erp.cashier.dto.MovementTransferRequest;
import com.erp.cashier.dto.MovementTransferResponse;
import com.erp.cashier.dto.RecentTransactionResponse;
import com.erp.cashier.dto.TransactionPageResponse;
import com.erp.cashier.model.CashRegisterMovement;
import com.erp.cashier.model.CashRegisterSession;
import com.erp.cashier.repository.CashRegisterMovementRepository;
import com.erp.cashier.repository.CashRegisterSessionRepository;
import io.r2dbc.spi.Row;
import io.r2dbc.spi.RowMetadata;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.ReactiveTransactionManager;
import org.springframework.transaction.reactive.TransactionalOperator;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Service for cash register movements and transactions.
 *
 * @author ERP Cashier Team
 * @since 2025-01-20
 */
@Service
public class MovementService {
    private static final String STATE_OPEN = "ouverte";
    private static final String TRANSFER_REASON = "transfer";

    private final R2dbcEntityTemplate entityTemplate;
    private final CashRegisterMovementRepository movementRepository;
    private final CashRegisterSessionRepository sessionRepository;
    private final TransactionalOperator transactionalOperator;
    private final AccountingCashMovementService accountingService;
    private final AuditService auditService;

    /**
     * Creates the movement service.
     *
     * @param entityTemplate entity template
     * @param movementRepository movement repository
     * @param sessionRepository session repository
     * @param transactionManager transaction manager
     */
    public MovementService(
            R2dbcEntityTemplate entityTemplate,
            CashRegisterMovementRepository movementRepository,
            CashRegisterSessionRepository sessionRepository,
            ReactiveTransactionManager transactionManager,
            AccountingCashMovementService accountingService,
            AuditService auditService
    ) {
        this.entityTemplate = entityTemplate;
        this.movementRepository = movementRepository;
        this.sessionRepository = sessionRepository;
        this.transactionalOperator = TransactionalOperator.create(transactionManager);
        this.accountingService = accountingService;
        this.auditService = auditService;
    }

    /**
     * Lists movements for a cashier with optional filters.
     *
     * @param cashierId cashier identifier
     * @param sense movement sense
     * @param hasInvoice invoice filter
     * @param isTransfer transfer filter
     * @param type movement type
     * @return movements
     */
    public Flux<MovementResponse> listCashierMovements(
            String cashierId,
            String sense,
            Boolean hasInvoice,
            Boolean isTransfer,
            String type
    ) {
        if (!StringUtils.hasText(cashierId)) {
            return Flux.error(new ResponseStatusException(HttpStatus.FORBIDDEN, "Cashier scope is required."));
        }
        MovementQuery query = buildMovementQuery(
                cashierId,
                sense,
                hasInvoice,
                isTransfer,
                type,
                null,
                null,
                null,
                null,
                null,
                null
        );

        DatabaseClient.GenericExecuteSpec spec = entityTemplate.getDatabaseClient()
                .sql(query.sql());
        spec = applyBinds(spec, query.binds());
        return spec.map(this::mapMovementRow)
                .all();
    }

    /**
     * Creates a register transfer movement.
     *
     * @param request transfer request
     * @param actorId actor identifier
     * @return transfer response
     */
    public Mono<MovementTransferResponse> transferBetweenRegisters(MovementTransferRequest request, String actorId) {
        BigDecimal amount = request != null ? request.getAmount() : null;
        if (amount == null || amount.signum() <= 0) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "amount must be positive"));
        }

        return requireOpenSession(actorId)
                .flatMap(session -> {
                    CashRegisterMovement movement = buildTransferMovement(session.getId(), amount, actorId);
                    Mono<reactor.util.function.Tuple2<CashRegisterMovement, MovementTransferResponse>> flow =
                            movementRepository.save(movement)
                            .flatMap(saved -> Mono.zip(
                                    calculateSessionBalance(session.getId()),
                                    fetchRegisterSummary(session.getCashRegisterId())
                            ).map(tuple -> reactor.util.function.Tuples.of(
                                    saved,
                                    new MovementTransferResponse(
                                            true,
                                            "Transfer recorded.",
                                            tuple.getT1(),
                                            tuple.getT2()
                                    )
                            )));

                    return transactionalOperator.transactional(flow)
                            .doOnSuccess(tupleSave -> accountingService.syncMovementAsync(
                                    tupleSave.getT1(),
                                    null,
                                    null
                            ))
                            .doOnSuccess(tupleSave -> auditService.recordMovementEventAsync(tupleSave.getT1()))
                            .map(reactor.util.function.Tuple2::getT2);
                });
    }

    /**
     * Marks a movement as accounted and returns it.
     *
     * @param movementId movement identifier
     * @return account response
     */
    public Mono<MovementAccountResponse> accountMovement(String movementId) {
        String resolvedId = trimToNull(movementId);
        if (!StringUtils.hasText(resolvedId)) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "movement id is required"));
        }
        String sql = "UPDATE cash_register_movement SET is_accounted = true WHERE id = :id";
        return entityTemplate.getDatabaseClient()
                .sql(sql)
                .bind("id", resolvedId)
                .fetch()
                .rowsUpdated()
                .flatMap(rows -> findMovementById(resolvedId))
                .map(movement -> new MovementAccountResponse(true, movement));
    }

    /**
     * Lists transactions with pagination and filters.
     *
     * @param startDate start date filter
     * @param endDate end date filter
     * @param registerId register filter
     * @param cashierId cashier filter
     * @param type movement type
     * @param page page number
     * @param limit page size
     * @param organizationId organization identifier
     * @param agencyId agency identifier
     * @return transaction page
     */
    public Mono<TransactionPageResponse> listTransactions(
            String startDate,
            String endDate,
            String registerId,
            String cashierId,
            String type,
            Integer page,
            Integer limit,
            String organizationId,
            String agencyId
    ) {
        int resolvedPage = page != null && page > 0 ? page : 1;
        int resolvedLimit = limit != null && limit > 0 ? limit : 20;
        int offset = (resolvedPage - 1) * resolvedLimit;
        LocalDateTime startDateTime = parseStartDateTime(startDate);
        LocalDateTime endDateTime = parseEndDateTime(endDate);

        MovementQuery query = buildMovementQuery(
                cashierId,
                null,
                null,
                null,
                type,
                registerId,
                startDateTime,
                endDateTime,
                "ORDER BY m.create_on DESC LIMIT :limit OFFSET :offset",
                organizationId,
                agencyId
        );
        DatabaseClient.GenericExecuteSpec listSpec = entityTemplate.getDatabaseClient()
                .sql(query.sql());
        Map<String, Object> listBinds = new HashMap<>(query.binds());
        listBinds.put("limit", resolvedLimit);
        listBinds.put("offset", offset);
        listSpec = applyBinds(listSpec, listBinds);

        Mono<Long> total = countMovements(
                cashierId,
                registerId,
                type,
                startDateTime,
                endDateTime,
                organizationId,
                agencyId
        );
        Mono<java.util.List<MovementResponse>> movements = listSpec.map(this::mapMovementRow).all().collectList();
        return Mono.zip(movements, total)
                .map(tuple -> {
                    long totalCount = tuple.getT2();
                    int totalPages = (int) Math.ceil((double) totalCount / resolvedLimit);
                    return new TransactionPageResponse(tuple.getT1(), totalCount, resolvedPage, totalPages);
                });
    }

    /**
     * Lists recent transactions.
     *
     * @param organizationId organization identifier
     * @param agencyId agency identifier
     * @return recent transactions
     */
    public Flux<RecentTransactionResponse> listRecentTransactions(String organizationId, String agencyId) {
        if (!StringUtils.hasText(organizationId)) {
            return Flux.error(new ResponseStatusException(HttpStatus.FORBIDDEN, "Organization scope is required."));
        }
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT m.id, m.amount, m.sense, m.reason, m.create_on, m.external_reference, ");
        sql.append("r.town AS register_town, ");
        sql.append("cashier.user_first_name AS cashier_first_name, ");
        sql.append("cashier.user_name AS cashier_user_name ");
        sql.append("FROM cash_register_movement m ");
        sql.append("JOIN cash_register_session s ON s.id = m.session_id ");
        sql.append("LEFT JOIN cash_register r ON r.id = s.cash_register_id ");
        sql.append("LEFT JOIN agency a ON a.id = r.agency_id ");
        sql.append("LEFT JOIN person cashier ON cashier.id = m.create_by ");
        sql.append("WHERE a.organization_id = :orgId AND m.is_deleted = false ");
        if (StringUtils.hasText(agencyId)) {
            sql.append("AND a.id = :agencyId ");
        }
        sql.append("ORDER BY m.create_on DESC LIMIT 5");

        DatabaseClient.GenericExecuteSpec spec = entityTemplate.getDatabaseClient()
                .sql(sql.toString())
                .bind("orgId", organizationId);
        if (StringUtils.hasText(agencyId)) {
            spec = spec.bind("agencyId", agencyId);
        }
        return spec.map((row, meta) -> new RecentTransactionResponse(
                        row.get("id", String.class),
                        row.get("amount", BigDecimal.class),
                        mapSense(row.get("sense", String.class)),
                        row.get("reason", String.class),
                        toInstant(row.get("create_on", LocalDateTime.class)),
                        resolveCashierName(
                                row.get("cashier_first_name", String.class),
                                row.get("cashier_user_name", String.class)
                        ),
                        row.get("register_town", String.class),
                        null,
                        row.get("external_reference", String.class)
                ))
                .all();
    }

    private MovementResponse mapMovementRow(Row row, RowMetadata metadata) {
        String recipientId = row.get("recipient_id", String.class);
        String emitterId = row.get("emitter_id", String.class);
        MovementPartyResponse recipient = buildParty(
                row.get("recipient_person_id", String.class),
                row.get("recipient_first_name", String.class),
                row.get("recipient_user_name", String.class),
                "customer"
        );
        if (recipient == null && StringUtils.hasText(recipientId)) {
            recipient = new MovementPartyResponse(recipientId, null, null, "account");
        }
        MovementPartyResponse emitter = buildParty(
                row.get("emitter_person_id", String.class),
                row.get("emitter_first_name", String.class),
                row.get("emitter_user_name", String.class),
                "customer"
        );
        if (emitter == null && StringUtils.hasText(emitterId)) {
            emitter = new MovementPartyResponse(emitterId, null, null, "account");
        }

        String reasonDetail = row.get("reason_detail", String.class);
        String reason = StringUtils.hasText(reasonDetail)
                ? reasonDetail
                : row.get("reason", String.class);

        return new MovementResponse(
                row.get("id", String.class),
                row.get("amount", BigDecimal.class),
                row.get("sense", String.class),
                reason,
                row.get("external_reference", String.class),
                row.get("is_accounted", Boolean.class),
                row.get("payment_method", String.class),
                row.get("create_on", LocalDateTime.class),
                recipient,
                emitter,
                mapRegister(row),
                null
        );
    }

    private CashRegisterSummaryResponse mapRegister(Row row) {
        String registerId = row.get("register_id", String.class);
        String town = row.get("register_town", String.class);
        String country = row.get("register_country", String.class);
        String neighborhood = row.get("register_neighborhood", String.class);
        if (!StringUtils.hasText(registerId) && !StringUtils.hasText(town) && !StringUtils.hasText(country)) {
            return null;
        }
        return new CashRegisterSummaryResponse(registerId, town, country, neighborhood);
    }

    private Instant toInstant(LocalDateTime dateTime) {
        if (dateTime == null) {
            return null;
        }
        return dateTime.toInstant(ZoneOffset.UTC);
    }

    private String resolveCashierName(String firstName, String userName) {
        if (StringUtils.hasText(firstName)) {
            return firstName;
        }
        return StringUtils.hasText(userName) ? userName : null;
    }

    private String mapSense(String sense) {
        if (!StringUtils.hasText(sense)) {
            return null;
        }
        String trimmed = sense.trim().toLowerCase();
        if ("entree".equals(trimmed)) {
            return "entree";
        }
        if ("sortie".equals(trimmed)) {
            return "sortie";
        }
        if ("transfert".equals(trimmed)) {
            return "sortie";
        }
        if ("in".equals(trimmed)) {
            return "entree";
        }
        if ("out".equals(trimmed)) {
            return "sortie";
        }
        return trimmed;
    }

    private MovementPartyResponse buildParty(String id, String name, String username, String role) {
        if (!StringUtils.hasText(id) && !StringUtils.hasText(name) && !StringUtils.hasText(username)) {
            return null;
        }
        MovementPersonResponse person = null;
        if (StringUtils.hasText(name) || StringUtils.hasText(username)) {
            person = new MovementPersonResponse(name, username);
        }
        return new MovementPartyResponse(id, name, username, role, person);
    }

    private Mono<MovementResponse> findMovementById(String movementId) {
        MovementQuery query = buildMovementQuery(
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                "AND m.id = :movementId",
                null,
                null
        );
        DatabaseClient.GenericExecuteSpec spec = entityTemplate.getDatabaseClient()
                .sql(query.sql());
        Map<String, Object> binds = new HashMap<>(query.binds());
        binds.put("movementId", movementId);
        spec = applyBinds(spec, binds);
        return spec.map(this::mapMovementRow)
                .one()
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Movement not found.")));
    }

    private MovementQuery buildMovementQuery(
            String cashierId,
            String sense,
            Boolean hasInvoice,
            Boolean isTransfer,
            String type,
            String registerId,
            LocalDateTime startDate,
            LocalDateTime endDate,
            String suffix,
            String organizationId,
            String agencyId
    ) {
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT m.id, m.amount, m.sense, m.reason, m.reason_detail, ");
        sql.append("m.external_reference, m.is_accounted, m.payment_method, ");
        sql.append("m.create_on, ");
        sql.append("m.recipient_id, m.emitter_id, ");
        sql.append("r.id AS register_id, r.town AS register_town, r.country AS register_country, ");
        sql.append("r.neighborhood AS register_neighborhood, ");
        sql.append("rp.id AS recipient_person_id, ");
        sql.append("rp.user_first_name AS recipient_first_name, ");
        sql.append("rp.user_name AS recipient_user_name, ");
        sql.append("ep.id AS emitter_person_id, ");
        sql.append("ep.user_first_name AS emitter_first_name, ");
        sql.append("ep.user_name AS emitter_user_name ");
        sql.append("FROM cash_register_movement m ");
        sql.append("JOIN cash_register_session s ON s.id = m.session_id ");
        sql.append("LEFT JOIN cash_register r ON r.id = s.cash_register_id ");
        sql.append("LEFT JOIN agency a ON a.id = r.agency_id ");
        sql.append("LEFT JOIN account ra ON ra.id = m.recipient_id ");
        sql.append("LEFT JOIN customer_profile rcp ON rcp.id = ra.client_id ");
        sql.append("LEFT JOIN person rp ON rp.id = rcp.person_id ");
        sql.append("LEFT JOIN account ea ON ea.id = m.emitter_id ");
        sql.append("LEFT JOIN customer_profile ecp ON ecp.id = ea.client_id ");
        sql.append("LEFT JOIN person ep ON ep.id = ecp.person_id ");
        sql.append("WHERE 1=1 ");

        Map<String, Object> binds = new HashMap<>();
        if (StringUtils.hasText(cashierId)) {
            sql.append("AND s.open_by = :cashierId ");
            binds.put("cashierId", cashierId);
        }
        if (StringUtils.hasText(registerId)) {
            sql.append("AND r.id = :registerId ");
            binds.put("registerId", registerId);
        }
        if (StringUtils.hasText(organizationId)) {
            sql.append("AND a.organization_id = :organizationId ");
            binds.put("organizationId", organizationId);
        }
        if (StringUtils.hasText(agencyId)) {
            sql.append("AND a.id = :agencyId ");
            binds.put("agencyId", agencyId);
        }
        if (StringUtils.hasText(sense)) {
            sql.append("AND m.sense = :sense ");
            binds.put("sense", sense);
        }
        if (StringUtils.hasText(type)) {
            sql.append("AND m.reason = :type ");
            binds.put("type", type);
        }
        if (hasInvoice != null) {
            if (Boolean.TRUE.equals(hasInvoice)) {
                sql.append("AND m.external_reference IS NOT NULL ");
            } else {
                sql.append("AND (m.external_reference IS NULL OR m.external_reference = '') ");
            }
        }
        if (isTransfer != null) {
            if (Boolean.TRUE.equals(isTransfer)) {
                sql.append("AND m.reason = :transferReason ");
                binds.put("transferReason", TRANSFER_REASON);
            } else {
                sql.append("AND (m.reason IS NULL OR m.reason <> :transferReason) ");
                binds.put("transferReason", TRANSFER_REASON);
            }
        }
        if (startDate != null) {
            sql.append("AND m.create_on >= :startDate ");
            binds.put("startDate", startDate);
        }
        if (endDate != null) {
            sql.append("AND m.create_on <= :endDate ");
            binds.put("endDate", endDate);
        }
        if (StringUtils.hasText(suffix)) {
            sql.append(suffix).append(' ');
        }

        return new MovementQuery(sql.toString(), binds);
    }

    private Mono<Long> countMovements(
            String cashierId,
            String registerId,
            String type,
            LocalDateTime startDate,
            LocalDateTime endDate,
            String organizationId,
            String agencyId
    ) {
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT COUNT(*) AS total ");
        sql.append("FROM cash_register_movement m ");
        sql.append("JOIN cash_register_session s ON s.id = m.session_id ");
        sql.append("LEFT JOIN cash_register r ON r.id = s.cash_register_id ");
        sql.append("LEFT JOIN agency a ON a.id = r.agency_id ");
        sql.append("WHERE 1=1 ");

        Map<String, Object> binds = new HashMap<>();
        if (StringUtils.hasText(cashierId)) {
            sql.append("AND s.open_by = :cashierId ");
            binds.put("cashierId", cashierId);
        }
        if (StringUtils.hasText(registerId)) {
            sql.append("AND r.id = :registerId ");
            binds.put("registerId", registerId);
        }
        if (StringUtils.hasText(organizationId)) {
            sql.append("AND a.organization_id = :organizationId ");
            binds.put("organizationId", organizationId);
        }
        if (StringUtils.hasText(agencyId)) {
            sql.append("AND a.id = :agencyId ");
            binds.put("agencyId", agencyId);
        }
        if (StringUtils.hasText(type)) {
            sql.append("AND m.reason = :type ");
            binds.put("type", type);
        }
        if (startDate != null) {
            sql.append("AND m.create_on >= :startDate ");
            binds.put("startDate", startDate);
        }
        if (endDate != null) {
            sql.append("AND m.create_on <= :endDate ");
            binds.put("endDate", endDate);
        }

        DatabaseClient.GenericExecuteSpec spec = entityTemplate.getDatabaseClient()
                .sql(sql.toString());
        spec = applyBinds(spec, binds);
        return spec.map((row, meta) -> row.get("total", Long.class))
                .one()
                .defaultIfEmpty(0L);
    }

    private CashRegisterMovement buildTransferMovement(String sessionId, BigDecimal amount, String actorId) {
        CashRegisterMovement movement = new CashRegisterMovement();
        movement.setId(UUID.randomUUID().toString());
        movement.setSessionId(sessionId);
        movement.setSense("transfert");
        movement.setAmount(amount);
        movement.setReason(TRANSFER_REASON);
        movement.setEmitterId(null);
        movement.setIsAccounted(Boolean.FALSE);
        movement.setEventTicketingDetails(Boolean.FALSE);
        movement.setExternalReference(null);
        movement.setCreateOn(LocalDateTime.now());
        movement.setCreateBy(trimToNull(actorId));
        movement.setIsDeleted(Boolean.FALSE);
        movement.markNew();
        return movement;
    }

    private Mono<CashRegisterSession> requireOpenSession(String actorId) {
        String resolved = trimToNull(actorId);
        if (!StringUtils.hasText(resolved)) {
            return Mono.error(new ResponseStatusException(HttpStatus.FORBIDDEN, "Actor scope is required."));
        }
        return sessionRepository.findLatestByOpenByAndState(resolved, STATE_OPEN)
                .switchIfEmpty(Mono.error(new ResponseStatusException(
                        HttpStatus.FORBIDDEN,
                        "No open session found."
                )));
    }

    private Mono<BigDecimal> calculateSessionBalance(String sessionId) {
        String sql = "SELECT COALESCE(SUM(CASE "
                + "WHEN sense IN ('entree', 'in') THEN amount "
                + "WHEN sense IN ('sortie', 'out', 'transfert') THEN -amount "
                + "ELSE 0 END), 0) AS total "
                + "FROM cash_register_movement WHERE session_id = :sessionId";
        return entityTemplate.getDatabaseClient()
                .sql(sql)
                .bind("sessionId", sessionId)
                .map((row, meta) -> row.get("total", BigDecimal.class))
                .one()
                .defaultIfEmpty(BigDecimal.ZERO);
    }

    private Mono<CashRegisterSummaryResponse> fetchRegisterSummary(String registerId) {
        if (!StringUtils.hasText(registerId)) {
            return Mono.justOrEmpty((CashRegisterSummaryResponse) null);
        }
        String sql = "SELECT id, town, country, neighborhood FROM cash_register WHERE id = :id";
        return entityTemplate.getDatabaseClient()
                .sql(sql)
                .bind("id", registerId)
                .map((row, meta) -> new CashRegisterSummaryResponse(
                        row.get("id", String.class),
                        row.get("town", String.class),
                        row.get("country", String.class),
                        row.get("neighborhood", String.class)
                ))
                .one();
    }

    private DatabaseClient.GenericExecuteSpec applyBinds(
            DatabaseClient.GenericExecuteSpec spec,
            Map<String, Object> binds
    ) {
        DatabaseClient.GenericExecuteSpec current = spec;
        for (Map.Entry<String, Object> entry : binds.entrySet()) {
            current = current.bind(entry.getKey(), entry.getValue());
        }
        return current;
    }

    private LocalDateTime parseStartDateTime(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.contains("T")) {
            return LocalDateTime.parse(trimmed);
        }
        return LocalDate.parse(trimmed).atStartOfDay();
    }

    private LocalDateTime parseEndDateTime(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.contains("T")) {
            return LocalDateTime.parse(trimmed);
        }
        return LocalDate.parse(trimmed).atTime(LocalTime.MAX);
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private record MovementQuery(String sql, Map<String, Object> binds) {
    }
}
