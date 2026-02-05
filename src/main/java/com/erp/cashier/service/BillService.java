package com.erp.cashier.service;

import com.erp.cashier.config.BillingProperties;
import com.erp.cashier.dto.BillAccountResponse;
import com.erp.cashier.dto.BillDetailResponse;
import com.erp.cashier.dto.BillItemResponse;
import com.erp.cashier.dto.BillListAccountResponse;
import com.erp.cashier.dto.BillListResponse;
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
import com.erp.cashier.repository.CustomerProfileRepository;
import io.r2dbc.spi.Row;
import io.r2dbc.spi.RowMetadata;
import java.math.BigDecimal;
import java.time.Duration;
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
import org.springframework.web.reactive.function.client.WebClient;
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
    private final CustomerProfileRepository customerProfileRepository;
    private final TransactionalOperator transactionalOperator;
    private final AccountingCashMovementService accountingService;
    private final AuditService auditService;
    private final BillingProperties billingProperties;
    private final WebClient billingClient;

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
            CustomerProfileRepository customerProfileRepository,
            ReactiveTransactionManager transactionManager,
            AccountingCashMovementService accountingService,
            AuditService auditService,
            BillingProperties billingProperties,
            WebClient.Builder webClientBuilder
    ) {
        this.entityTemplate = entityTemplate;
        this.movementRepository = movementRepository;
        this.sessionRepository = sessionRepository;
        this.accountRepository = accountRepository;
        this.customerProfileRepository = customerProfileRepository;
        this.transactionalOperator = TransactionalOperator.create(transactionManager);
        this.accountingService = accountingService;
        this.auditService = auditService;
        this.billingProperties = billingProperties;
        String baseUrl = billingProperties != null ? billingProperties.getBaseUrl() : null;
        this.billingClient = StringUtils.hasText(baseUrl)
                ? webClientBuilder.baseUrl(baseUrl).build()
                : webClientBuilder.build();
    }

    /**
     * Lists bills for a cashier.
     *
     * @param organizationId organization identifier
     * @return bills
     */
    public Flux<BillListResponse> listCashierBills(String organizationId) {
        if (!StringUtils.hasText(organizationId)) {
            return Flux.error(new ResponseStatusException(HttpStatus.FORBIDDEN, "Organization scope is required."));
        }
        if (!isBillingExternalEnabled()) {
            return listCashierBillsLocal(organizationId)
                    .map(this::toBillListResponse);
        }
        return fetchExternalBills(organizationId)
                .flatMapMany(Flux::fromIterable)
                .map(this::toBillListResponse)
                .onErrorResume(ex -> listCashierBillsLocal(organizationId).map(this::toBillListResponse));
    }

    /**
     * Fetches a bill by id for a cashier.
     *
     * @param billId bill identifier
     * @param organizationId organization identifier
     * @return bill
     */
    public Mono<BillDetailResponse> getCashierBill(String billId, String organizationId) {
        String resolvedBillId = trimToNull(billId);
        if (!StringUtils.hasText(resolvedBillId)) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "bill id is required"));
        }
        if (!StringUtils.hasText(organizationId)) {
            return Mono.error(new ResponseStatusException(HttpStatus.FORBIDDEN, "Organization scope is required."));
        }
        if (!isBillingExternalEnabled()) {
            return getCashierBillLocal(resolvedBillId, organizationId)
                    .map(this::toBillDetailResponse);
        }
        return fetchExternalBills(organizationId)
                .flatMapMany(Flux::fromIterable)
                .filter(bill -> resolvedBillId.equals(trimToNull(bill.getId())))
                .next()
                .switchIfEmpty(getCashierBillLocal(resolvedBillId, organizationId))
                .map(this::toBillDetailResponse)
                .onErrorResume(ex -> getCashierBillLocal(resolvedBillId, organizationId)
                        .map(this::toBillDetailResponse));
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
        sql.append("WHERE b.is_deleted = FALSE ");
        Map<String, Object> binds = new HashMap<>();
        if (StringUtils.hasText(trimmedSearch)) {
            sql.append("AND (LOWER(b.invoice_code) LIKE :search ");
            sql.append("OR LOWER(b.customer_name) LIKE :search ");
            sql.append("OR LOWER(p.user_first_name) LIKE :search ");
            sql.append("OR LOWER(p.user_name) LIKE :search ");
            sql.append("OR LOWER(acc.account_number) LIKE :search) ");
            binds.put("search", "%" + trimmedSearch.toLowerCase() + "%");
        }
        sql.append("ORDER BY b.due_date DESC NULLS LAST, b.create_on DESC LIMIT :limit OFFSET :offset");
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

        return Mono.zip(requireOpenSession(actorId), requireSalesAgentAccount(actorId))
                .flatMap(tuple -> {
                    CashRegisterSession session = tuple.getT1();
                    Account cashierAccount = tuple.getT2();
                    boolean hasTicketing = request.getTicketing() != null && !request.getTicketing().isEmpty();
                    CashRegisterMovement movement = buildBillMovement(
                            session.getId(),
                            amount,
                            invoiceCode,
                            "entree",
                            null,
                            cashierAccount.getId(),
                            hasTicketing,
                            actorId
                    );
                    return movementRepository.save(movement)
                            .doOnSuccess(savedMovement -> accountingService.syncMovementAsync(
                                    savedMovement,
                                    cashierAccount.getAccountingAccount(),
                                    null
                            ))
                            .doOnSuccess(savedMovement -> auditService.recordMovementEventAsync(savedMovement))
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
        return Mono.zip(loadAccount(accountId), requireOpenSession(actorId), requireSalesAgentAccount(actorId))
                .flatMap(tuple -> {
                    Account account = tuple.getT1();
                    CashRegisterSession session = tuple.getT2();
                    Account cashierAccount = tuple.getT3();
                    BigDecimal currentBalance = toBigDecimal(account.getTotalFunds());
                    if (currentBalance.compareTo(amount) < 0) {
                        return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Insufficient funds"));
                    }
                    BigDecimal newBalance = currentBalance.subtract(amount);
                    account.setTotalFunds(newBalance.doubleValue());
                    boolean hasTicketing = request.getTicketing() != null && !request.getTicketing().isEmpty();
                    String emitterAccountId = account.getId();
                    String recipientAccountId = cashierAccount.getId();
                    CashRegisterMovement outMovement = buildBillMovement(
                            session.getId(),
                            amount,
                            invoiceCode,
                            "sortie",
                            emitterAccountId,
                            recipientAccountId,
                            hasTicketing,
                            actorId
                    );
                    CashRegisterMovement inMovement = buildBillMovement(
                            session.getId(),
                            amount,
                            invoiceCode,
                            "entree",
                            emitterAccountId,
                            recipientAccountId,
                            hasTicketing,
                            actorId
                    );

                    Mono<reactor.util.function.Tuple2<CashRegisterMovement, CashRegisterMovement>> flow =
                            accountRepository.save(account)
                            .then(movementRepository.save(outMovement))
                            .zipWith(movementRepository.save(inMovement));

                    return transactionalOperator.transactional(flow)
                            .doOnSuccess(tupleSave -> {
                                String emitterAccounting = account.getAccountingAccount();
                                String recipientAccounting = cashierAccount.getAccountingAccount();
                                accountingService.syncMovementAsync(
                                        tupleSave.getT1(),
                                        recipientAccounting,
                                        emitterAccounting
                                );
                                accountingService.syncMovementAsync(
                                        tupleSave.getT2(),
                                        recipientAccounting,
                                        emitterAccounting
                                );
                                auditService.recordMovementEventAsync(tupleSave.getT1());
                                auditService.recordMovementEventAsync(tupleSave.getT2());
                            })
                            .map(tupleSave -> {
                                BillPaymentResponse response = new BillPaymentResponse();
                                response.setSuccess(true);
                                response.setOutMovementId(tupleSave.getT1().getId());
                                response.setInMovementId(tupleSave.getT2().getId());
                                response.setReference(invoiceCode);
                                return response;
                            });
                });
    }

    private String baseBillSelect() {
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT b.id, b.invoice_code, b.amount, b.customer_name, ");
        sql.append("b.due_date, b.payment_mode, b.create_on, ");
        sql.append("acc.id AS account_id, acc.account_number, acc.total_funds, acc.is_active, ");
        sql.append("p.user_first_name, p.user_name, p.phone ");
        sql.append("FROM bill b ");
        sql.append("LEFT JOIN account acc ON acc.id = b.account_id ");
        sql.append("LEFT JOIN customer_profile cp ON cp.id = acc.client_id ");
        sql.append("LEFT JOIN person p ON p.id = cp.person_id ");
        return sql.toString();
    }

    private BillResponse mapBillRow(Row row, RowMetadata metadata) {
        String accountId = row.get("account_id", String.class);
        String personName = trimToNull(row.get("user_first_name", String.class));
        if (!StringUtils.hasText(personName)) {
            personName = trimToNull(row.get("user_name", String.class));
        }
        String billCustomerName = trimToNull(row.get("customer_name", String.class));
        String customerName = StringUtils.hasText(personName) ? personName : billCustomerName;
        BillAccountResponse account = null;
        if (StringUtils.hasText(accountId)) {
            account = new BillAccountResponse(
                    accountId,
                    row.get("account_number", String.class),
                    row.get("total_funds", Double.class),
                    row.get("is_active", Boolean.class),
                    StringUtils.hasText(personName) ? personName : billCustomerName,
                    row.get("phone", String.class)
            );
        }
        return new BillResponse(
                row.get("id", String.class),
                row.get("invoice_code", String.class),
                row.get("amount", BigDecimal.class),
                customerName,
                row.get("due_date", LocalDateTime.class),
                null,
                row.get("payment_mode", String.class),
                Collections.emptyList(),
                account
        );
    }

    private Mono<Long> countBills(String search) {
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT COUNT(*) AS total ");
        sql.append("FROM bill b ");
        sql.append("LEFT JOIN account acc ON acc.id = b.account_id ");
        sql.append("LEFT JOIN customer_profile cp ON cp.id = acc.client_id ");
        sql.append("LEFT JOIN person p ON p.id = cp.person_id ");
        sql.append("WHERE b.is_deleted = FALSE ");
        Map<String, Object> binds = new HashMap<>();
        if (StringUtils.hasText(search)) {
            sql.append("AND (LOWER(b.invoice_code) LIKE :search ");
            sql.append("OR LOWER(b.customer_name) LIKE :search ");
            sql.append("OR LOWER(p.user_first_name) LIKE :search ");
            sql.append("OR LOWER(p.user_name) LIKE :search ");
            sql.append("OR LOWER(acc.account_number) LIKE :search) ");
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
            String invoiceCode,
            String sense,
            String emitterId,
            String recipientId,
            boolean hasTicketing,
            String actorId
    ) {
        CashRegisterMovement movement = new CashRegisterMovement();
        movement.setId(UUID.randomUUID().toString());
        movement.setSessionId(sessionId);
        movement.setSense(sense);
        movement.setAmount(amount);
        movement.setReason(BILL_REASON);
        movement.setEmitterId(trimToNull(emitterId));
        movement.setRecipientId(trimToNull(recipientId));
        movement.setIsAccounted(Boolean.FALSE);
        movement.setEventTicketingDetails(hasTicketing);
        movement.setExternalReference(invoiceCode);
        movement.setCreateOn(LocalDateTime.now());
        movement.setCreateBy(trimToNull(actorId));
        movement.setIsDeleted(Boolean.FALSE);
        movement.markNew();
        return movement;
    }

    private Mono<Account> requireSalesAgentAccount(String actorId) {
        String resolved = trimToNull(actorId);
        if (!StringUtils.hasText(resolved)) {
            return Mono.error(new ResponseStatusException(HttpStatus.FORBIDDEN, "Actor scope is required."));
        }
        return customerProfileRepository.findByPersonIdAndProfession(resolved, "SALES_AGENT")
                .switchIfEmpty(Mono.error(new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Cashier profile not found."
                )))
                .flatMap(profile -> accountRepository.findFirstByClientId(profile.getId()))
                .switchIfEmpty(Mono.error(new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Cashier account not found."
                )));
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

    private Mono<BillResponse> attachBillItems(BillResponse bill) {
        if (bill == null || !StringUtils.hasText(bill.getId())) {
            return Mono.just(bill);
        }
        return fetchBillItems(bill.getId())
                .collectList()
                .map(items -> {
                    bill.setItems(items);
                    return bill;
                });
    }

    private Flux<BillItemResponse> fetchBillItems(String billId) {
        String resolvedBillId = trimToNull(billId);
        if (!StringUtils.hasText(resolvedBillId)) {
            return Flux.empty();
        }
        return entityTemplate.getDatabaseClient()
                .sql("SELECT description, quantity, amount FROM bill_item WHERE bill_id = :billId ORDER BY id")
                .bind("billId", resolvedBillId)
                .map((row, meta) -> new BillItemResponse(
                        row.get("description", String.class),
                        row.get("quantity", Integer.class),
                        row.get("amount", BigDecimal.class)
                ))
                .all();
    }

    private Flux<BillResponse> listCashierBillsLocal(String organizationId) {
        String sql = baseBillSelect() + "WHERE b.organization_id = :organizationId AND b.is_deleted = FALSE "
                + "ORDER BY b.due_date DESC NULLS LAST, b.create_on DESC";
        DatabaseClient.GenericExecuteSpec spec = entityTemplate.getDatabaseClient()
                .sql(sql)
                .bind("organizationId", organizationId);
        return spec.map(this::mapBillRow)
                .all();
    }

    private Mono<BillResponse> getCashierBillLocal(String billId, String organizationId) {
        String sql = baseBillSelect() + "WHERE b.id = :billId AND b.organization_id = :organizationId "
                + "AND b.is_deleted = FALSE";
        return entityTemplate.getDatabaseClient()
                .sql(sql)
                .bind("billId", billId)
                .bind("organizationId", organizationId)
                .map(this::mapBillRow)
                .one()
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Bill not found.")))
                .flatMap(this::attachBillItems);
    }

    private BillListResponse toBillListResponse(BillResponse bill) {
        if (bill == null) {
            return null;
        }
        BillListAccountResponse account = null;
        if (bill.getAccount() != null) {
            account = new BillListAccountResponse(
                    bill.getAccount().getAccountNumber(),
                    bill.getAccount().getCustomerPhone()
            );
        }
        return new BillListResponse(
                bill.getId(),
                bill.getInvoiceCode(),
                bill.getAmount(),
                bill.getCustomerName(),
                bill.getDueDate(),
                bill.getPaymentMode(),
                account
        );
    }

    private BillDetailResponse toBillDetailResponse(BillResponse bill) {
        if (bill == null) {
            return null;
        }
        return new BillDetailResponse(
                bill.getId(),
                bill.getInvoiceCode(),
                bill.getAmount(),
                bill.getCustomerName(),
                bill.getDueDate(),
                bill.getPaymentMode(),
                bill.getItems(),
                bill.getAccount()
        );
    }

    private boolean isBillingExternalEnabled() {
        return billingProperties != null
                && billingProperties.isEnabled()
                && StringUtils.hasText(billingProperties.getBaseUrl());
    }

    private Mono<List<BillResponse>> fetchExternalBills(String organizationId) {
        String resolvedOrgId = trimToNull(organizationId);
        if (!StringUtils.hasText(resolvedOrgId)) {
            return Mono.error(new ResponseStatusException(HttpStatus.FORBIDDEN, "Organization scope is required."));
        }
        return billingClient.get()
                .uri(billingProperties.getAllBillsPath())
                .header("tenant-Id", resolvedOrgId)
                .retrieve()
                .bodyToFlux(BillResponse.class)
                .collectList()
                .timeout(Duration.ofSeconds(billingProperties.getTimeoutSeconds()));
    }
}
