package com.erp.cashier.service;

import com.erp.cashier.dto.AccountEventResponse;
import com.erp.cashier.dto.AccountOwnerResponse;
import com.erp.cashier.dto.AccountResponse;
import com.erp.cashier.dto.BillPageResponse;
import com.erp.cashier.dto.BillPaymentRequest;
import com.erp.cashier.dto.BillPaymentResponse;
import com.erp.cashier.dto.BillResponse;
import com.erp.cashier.model.Account;
import com.erp.cashier.model.CashRegisterMovement;
import com.erp.cashier.model.CashRegisterSession;
import com.erp.cashier.repository.AccountRepository;
import com.erp.cashier.repository.CashRegisterMovementRepository;
import com.erp.cashier.repository.CashRegisterSessionRepository;
import io.r2dbc.spi.Row;
import io.r2dbc.spi.RowMetadata;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
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
 * Service for bill operations.
 *
 * @author ERP Cashier Team
 * @since 2025-01-20
 */
@Service
public class BillService {
    private static final String STATE_OPEN = "ouverte";
    private static final String BILL_REASON = "bill";

    private final R2dbcEntityTemplate entityTemplate;
    private final CashRegisterMovementRepository movementRepository;
    private final CashRegisterSessionRepository sessionRepository;
    private final AccountRepository accountRepository;
    private final TransactionalOperator transactionalOperator;

    /**
     * Creates the bill service.
     *
     * @param entityTemplate entity template
     * @param movementRepository movement repository
     * @param sessionRepository session repository
     * @param accountRepository account repository
     * @param transactionManager transaction manager
     */
    public BillService(
            R2dbcEntityTemplate entityTemplate,
            CashRegisterMovementRepository movementRepository,
            CashRegisterSessionRepository sessionRepository,
            AccountRepository accountRepository,
            ReactiveTransactionManager transactionManager
    ) {
        this.entityTemplate = entityTemplate;
        this.movementRepository = movementRepository;
        this.sessionRepository = sessionRepository;
        this.accountRepository = accountRepository;
        this.transactionalOperator = TransactionalOperator.create(transactionManager);
    }

    /**
     * Lists bills for a cashier.
     *
     * @param cashierId cashier identifier
     * @return bills
     */
    public Flux<BillResponse> listCashierBills(String cashierId) {
        if (!StringUtils.hasText(cashierId)) {
            return Flux.error(new ResponseStatusException(HttpStatus.FORBIDDEN, "Cashier scope is required."));
        }
        String sql = baseBillSelect() + "WHERE m.reason = :reason AND s.open_by = :cashierId "
                + "ORDER BY m.create_on DESC";
        DatabaseClient.GenericExecuteSpec spec = entityTemplate.getDatabaseClient()
                .sql(sql)
                .bind("reason", BILL_REASON)
                .bind("cashierId", cashierId);
        return spec.map(this::mapBillRow)
                .all();
    }

    /**
     * Fetches a bill by id for a cashier.
     *
     * @param billId bill identifier
     * @param cashierId cashier identifier
     * @return bill
     */
    public Mono<BillResponse> getCashierBill(String billId, String cashierId) {
        String resolvedBillId = trimToNull(billId);
        if (!StringUtils.hasText(resolvedBillId)) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "bill id is required"));
        }
        if (!StringUtils.hasText(cashierId)) {
            return Mono.error(new ResponseStatusException(HttpStatus.FORBIDDEN, "Cashier scope is required."));
        }
        String sql = baseBillSelect() + "WHERE m.id = :billId AND m.reason = :reason AND s.open_by = :cashierId";
        return entityTemplate.getDatabaseClient()
                .sql(sql)
                .bind("billId", resolvedBillId)
                .bind("reason", BILL_REASON)
                .bind("cashierId", cashierId)
                .map(this::mapBillRow)
                .one()
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Bill not found.")));
    }

    /**
     * Lists bills with pagination and search.
     *
     * @param search search query
     * @param page page number
     * @param limit page size
     * @return bill page
     */
    public Mono<BillPageResponse> listBills(String search, Integer page, Integer limit) {
        int resolvedPage = page != null && page > 0 ? page : 1;
        int resolvedLimit = limit != null && limit > 0 ? limit : 20;
        int offset = (resolvedPage - 1) * resolvedLimit;
        String trimmedSearch = trimToNull(search);

        StringBuilder sql = new StringBuilder(baseBillSelect());
        sql.append("WHERE m.reason = :reason ");
        Map<String, Object> binds = new HashMap<>();
        binds.put("reason", BILL_REASON);
        if (StringUtils.hasText(trimmedSearch)) {
            sql.append("AND (LOWER(m.external_reference) LIKE :search ");
            sql.append("OR LOWER(p.user_first_name) LIKE :search ");
            sql.append("OR LOWER(p.user_name) LIKE :search) ");
            binds.put("search", "%" + trimmedSearch.toLowerCase() + "%");
        }
        sql.append("ORDER BY m.create_on DESC LIMIT :limit OFFSET :offset");
        binds.put("limit", resolvedLimit);
        binds.put("offset", offset);

        DatabaseClient.GenericExecuteSpec listSpec = entityTemplate.getDatabaseClient().sql(sql.toString());
        listSpec = applyBinds(listSpec, binds);

        Mono<Long> total = countBills(trimmedSearch);
        Mono<List<BillResponse>> bills = listSpec.map(this::mapBillRow).all().collectList();
        return Mono.zip(bills, total)
                .map(tuple -> {
                    long totalCount = tuple.getT2();
                    int totalPages = (int) Math.ceil((double) totalCount / resolvedLimit);
                    return new BillPageResponse(tuple.getT1(), totalCount, resolvedPage, totalPages);
                });
    }

    /**
     * Pays a bill and creates movements.
     *
     * @param request payment request
     * @param actorId actor identifier
     * @return payment response
     */
    public Mono<BillPaymentResponse> payBill(BillPaymentRequest request, String actorId) {
        if (request == null) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Payment payload is required."));
        }
        BigDecimal amount = request.getAmount();
        if (amount == null || amount.signum() <= 0) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "amount must be positive"));
        }
        String paymentMode = trimToNull(request.getPaymentMode());
        if (!StringUtils.hasText(paymentMode)) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "payment_mode is required"));
        }
        String invoiceCode = trimToNull(request.getInvoiceCode());

        if ("account".equalsIgnoreCase(paymentMode)) {
            return payBillWithAccount(request, actorId, invoiceCode, amount);
        }
        if (!"cash".equalsIgnoreCase(paymentMode)) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported payment mode."));
        }

        return requireOpenSession(actorId)
                .flatMap(session -> {
                    boolean hasTicketing = request.getTicketing() != null && !request.getTicketing().isEmpty();
                    CashRegisterMovement movement = buildBillMovement(
                            session.getId(),
                            amount,
                            actorId,
                            invoiceCode,
                            "entree",
                            null,
                            null,
                            hasTicketing
                    );
                    return movementRepository.save(movement)
                            .map(savedMovement -> {
                                BillPaymentResponse response = new BillPaymentResponse();
                                response.setSuccess(true);
                                response.setMovementId(savedMovement.getId());
                                response.setChange(calculateChange(request.getCashGiven(), amount));
                                response.setReference(invoiceCode);
                                return response;
                            });
                });
    }

    private Mono<BillPaymentResponse> payBillWithAccount(
            BillPaymentRequest request,
            String actorId,
            String invoiceCode,
            BigDecimal amount
    ) {
        String accountId = trimToNull(request.getAccountId());
        if (!StringUtils.hasText(accountId)) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "account_id is required"));
        }
        return Mono.zip(loadAccount(accountId), requireOpenSession(actorId))
                .flatMap(tuple -> {
                    Account account = tuple.getT1();
                    CashRegisterSession session = tuple.getT2();
                    BigDecimal currentBalance = toBigDecimal(account.getTotalFunds());
                    if (currentBalance.compareTo(amount) < 0) {
                        return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Insufficient funds"));
                    }
                    BigDecimal newBalance = currentBalance.subtract(amount);
                    account.setTotalFunds(newBalance.doubleValue());
                    boolean hasTicketing = request.getTicketing() != null && !request.getTicketing().isEmpty();
                    CashRegisterMovement outMovement = buildBillMovement(
                            session.getId(),
                            amount,
                            actorId,
                            invoiceCode,
                            "sortie",
                            accountId,
                            null,
                            hasTicketing
                    );
                    CashRegisterMovement inMovement = buildBillMovement(
                            session.getId(),
                            amount,
                            actorId,
                            invoiceCode,
                            "entree",
                            null,
                            accountId,
                            hasTicketing
                    );

                    Mono<BillPaymentResponse> flow = accountRepository.save(account)
                            .then(movementRepository.save(outMovement))
                            .zipWith(movementRepository.save(inMovement))
                            .map(tupleSave -> {
                                BillPaymentResponse response = new BillPaymentResponse();
                                response.setSuccess(true);
                                response.setOutMovementId(tupleSave.getT1().getId());
                                response.setInMovementId(tupleSave.getT2().getId());
                                response.setReference(invoiceCode);
                                return response;
                            });

                    return transactionalOperator.transactional(flow);
                });
    }

    private String baseBillSelect() {
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT m.id, m.external_reference, m.amount, m.create_on, ");
        sql.append("s.cash_register_id, m.recipient_id, m.emitter_id, ");
        sql.append("acc.id AS account_id, acc.account_number, acc.total_funds, acc.is_active, ");
        sql.append("acc.create_on AS account_create_on, acc.client_id, ");
        sql.append("p.id AS person_id, p.user_first_name, p.user_name ");
        sql.append("FROM cash_register_movement m ");
        sql.append("JOIN cash_register_session s ON s.id = m.session_id ");
        sql.append("LEFT JOIN account acc ON acc.id = COALESCE(m.recipient_id, m.emitter_id) ");
        sql.append("LEFT JOIN customer_profile cp ON cp.id = acc.client_id ");
        sql.append("LEFT JOIN person p ON p.id = cp.person_id ");
        return sql.toString();
    }

    private BillResponse mapBillRow(Row row, RowMetadata metadata) {
        String accountId = row.get("account_id", String.class);
        AccountResponse account = null;
        if (StringUtils.hasText(accountId)) {
            AccountOwnerResponse owner = new AccountOwnerResponse(
                    row.get("user_first_name", String.class),
                    row.get("user_name", String.class),
                    "customer"
            );
            account = new AccountResponse(
                    accountId,
                    row.get("account_number", String.class),
                    row.get("total_funds", Double.class),
                    row.get("is_active", Boolean.class),
                    row.get("account_create_on", LocalDateTime.class),
                    row.get("client_id", String.class),
                    owner,
                    Collections.<AccountEventResponse>emptyList(),
                    Collections.emptyList()
            );
        }
        String customerName = row.get("user_first_name", String.class);
        if (!StringUtils.hasText(customerName)) {
            customerName = row.get("user_name", String.class);
        }
        return new BillResponse(
                row.get("id", String.class),
                row.get("external_reference", String.class),
                row.get("amount", BigDecimal.class),
                customerName,
                row.get("create_on", LocalDateTime.class),
                row.get("cash_register_id", String.class),
                StringUtils.hasText(accountId) ? "account" : "cash",
                Collections.emptyList(),
                account
        );
    }

    private Mono<Long> countBills(String search) {
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT COUNT(*) AS total ");
        sql.append("FROM cash_register_movement m ");
        sql.append("LEFT JOIN account acc ON acc.id = COALESCE(m.recipient_id, m.emitter_id) ");
        sql.append("LEFT JOIN customer_profile cp ON cp.id = acc.client_id ");
        sql.append("LEFT JOIN person p ON p.id = cp.person_id ");
        sql.append("WHERE m.reason = :reason ");
        Map<String, Object> binds = new HashMap<>();
        binds.put("reason", BILL_REASON);
        if (StringUtils.hasText(search)) {
            sql.append("AND (LOWER(m.external_reference) LIKE :search ");
            sql.append("OR LOWER(p.user_first_name) LIKE :search ");
            sql.append("OR LOWER(p.user_name) LIKE :search) ");
            binds.put("search", "%" + search.toLowerCase() + "%");
        }
        DatabaseClient.GenericExecuteSpec spec = entityTemplate.getDatabaseClient()
                .sql(sql.toString());
        spec = applyBinds(spec, binds);
        return spec.map((row, meta) -> row.get("total", Long.class))
                .one()
                .defaultIfEmpty(0L);
    }

    private CashRegisterMovement buildBillMovement(
            String sessionId,
            BigDecimal amount,
            String actorId,
            String invoiceCode,
            String sense,
            String emitterId,
            String recipientId,
            boolean hasTicketing
    ) {
        CashRegisterMovement movement = new CashRegisterMovement();
        movement.setId(UUID.randomUUID().toString());
        movement.setSessionId(sessionId);
        movement.setSense(sense);
        movement.setAmount(amount);
        movement.setReason(BILL_REASON);
        movement.setEmitterId(emitterId);
        movement.setRecipientId(recipientId);
        movement.setIsAccounted(Boolean.FALSE);
        movement.setEventTicketingDetails(hasTicketing);
        movement.setExternalReference(invoiceCode);
        movement.setCreateOn(LocalDateTime.now());
        movement.setCreateBy(trimToNull(actorId));
        movement.setIsDeleted(Boolean.FALSE);
        return movement;
    }

    private Mono<Account> loadAccount(String accountId) {
        return accountRepository.findById(accountId)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found.")));
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

    private BigDecimal calculateChange(BigDecimal cashGiven, BigDecimal amount) {
        if (cashGiven == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal change = cashGiven.subtract(amount);
        if (change.signum() < 0) {
            return BigDecimal.ZERO;
        }
        return change;
    }

    private BigDecimal toBigDecimal(Double value) {
        return value != null ? BigDecimal.valueOf(value) : BigDecimal.ZERO;
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

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }
}
