package com.erp.cashier.service;

import com.erp.cashier.dto.AccountEventResponse;
import com.erp.cashier.dto.AccountOwnerResponse;
import com.erp.cashier.dto.AccountP2PTransferRequest;
import com.erp.cashier.dto.AccountP2PTransferResponse;
import com.erp.cashier.dto.AccountResponse;
import com.erp.cashier.dto.AccountTransferResponse;
import com.erp.cashier.dto.AdminAccountEventResponse;
import com.erp.cashier.dto.AdminAccountOperationCreatorResponse;
import com.erp.cashier.dto.AdminAccountOperationRegisterResponse;
import com.erp.cashier.dto.AdminAccountOperationResponse;
import com.erp.cashier.dto.AdminAccountOperationSessionResponse;
import com.erp.cashier.dto.AdminAccountResponse;
import com.erp.cashier.dto.AdminCustomerAccountResponse;
import com.erp.cashier.dto.AdminCustomerPersonResponse;
import com.erp.cashier.dto.AdminCustomerResponse;
import com.erp.cashier.dto.CashierAccountCustomerResponse;
import com.erp.cashier.dto.CashierAccountResponse;
import com.erp.cashier.dto.CashierAccountSummaryResponse;
import com.erp.cashier.dto.CashierCustomerResponse;
import com.erp.cashier.dto.CashierFundRequest;
import com.erp.cashier.dto.CashierPersonResponse;
import com.erp.cashier.dto.CashRegisterTicketingDenominationResponse;
import com.erp.cashier.dto.CreateCustomerRequest;
import com.erp.cashier.dto.CreateCustomerResponse;
import com.erp.cashier.dto.CustomerResponse;
import com.erp.cashier.dto.FundRequestResponse;
import com.erp.cashier.dto.PaymentMethod;
import com.erp.cashier.dto.PersonDetailResponse;
import com.erp.cashier.dto.TicketingRequest;
import com.erp.cashier.model.Account;
import com.erp.cashier.model.CashRegister;
import com.erp.cashier.model.CashRegisterMovement;
import com.erp.cashier.model.CashRegisterSession;
import com.erp.cashier.model.CustomerProfile;
import com.erp.cashier.model.Person;
import com.erp.cashier.repository.AccountRepository;
import com.erp.cashier.repository.CashRegisterMovementRepository;
import com.erp.cashier.repository.CashRegisterSessionRepository;
import com.erp.cashier.repository.CustomerProfileRepository;
import com.erp.cashier.repository.PersonRepository;
import io.r2dbc.spi.Row;
import io.r2dbc.spi.RowMetadata;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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
import reactor.util.function.Tuple2;
import reactor.util.function.Tuple3;
import reactor.util.function.Tuples;

/**
 * Service for account and customer operations.
 *
 * @author ERP Cashier Team
 * @since 2025-01-20
 */
@Service
public class AccountService {
    private static final String STATE_OPEN = "ouverte";

    private final PersonRepository personRepository;
    private final CustomerProfileRepository customerProfileRepository;
    private final AccountRepository accountRepository;
    private final CashRegisterMovementRepository movementRepository;
    private final CashRegisterSessionRepository sessionRepository;
    private final R2dbcEntityTemplate entityTemplate;
    private final TransactionalOperator transactionalOperator;
    private final AccountingCashMovementService accountingService;
    private final AuditService auditService;

    /**
     * Creates the account service.
     *
     * @param personRepository person repository
     * @param customerProfileRepository customer repository
     * @param accountRepository account repository
     * @param movementRepository movement repository
     * @param sessionRepository session repository
     * @param entityTemplate entity template
     * @param transactionManager transaction manager
     */
    public AccountService(
            PersonRepository personRepository,
            CustomerProfileRepository customerProfileRepository,
            AccountRepository accountRepository,
            CashRegisterMovementRepository movementRepository,
            CashRegisterSessionRepository sessionRepository,
            R2dbcEntityTemplate entityTemplate,
            ReactiveTransactionManager transactionManager,
            AccountingCashMovementService accountingService,
            AuditService auditService
    ) {
        this.personRepository = personRepository;
        this.customerProfileRepository = customerProfileRepository;
        this.accountRepository = accountRepository;
        this.movementRepository = movementRepository;
        this.sessionRepository = sessionRepository;
        this.entityTemplate = entityTemplate;
        this.transactionalOperator = TransactionalOperator.create(transactionManager);
        this.accountingService = accountingService;
        this.auditService = auditService;
    }

    /**
     * Lists accounts for cashiers (scoped by organization and agency).
     *
     * @param organizationId organization identifier
     * @param agencyId agency identifier
     * @return accounts
     */
    public Flux<CashierAccountResponse> listAccounts(String organizationId, String agencyId) {
        requireOrganization(organizationId);
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT a.id, a.account_number, a.total_funds, a.is_active, a.create_on, ");
        sql.append("cp.id AS customer_id, p.id AS person_id, p.user_name, p.user_first_name, ");
        sql.append("p.phone, p.mail, p.country ");
        sql.append("FROM account a ");
        sql.append("JOIN customer_profile cp ON cp.id = a.client_id ");
        sql.append("JOIN person p ON p.id = cp.person_id ");
        sql.append("WHERE a.organization_id = :organizationId ");
        sql.append("ORDER BY a.create_on DESC");
        DatabaseClient.GenericExecuteSpec spec = entityTemplate.getDatabaseClient().sql(sql.toString())
                .bind("organizationId", organizationId);
        return spec.map((row, meta) -> {
                    CashierPersonResponse person = new CashierPersonResponse(
                            row.get("person_id", String.class),
                            row.get("user_name", String.class),
                            row.get("user_first_name", String.class),
                            row.get("phone", String.class),
                            row.get("mail", String.class),
                            row.get("country", String.class)
                    );
                    CashierAccountCustomerResponse customer = new CashierAccountCustomerResponse(
                            row.get("customer_id", String.class),
                            person
                    );
                    return new CashierAccountResponse(
                            row.get("id", String.class),
                            row.get("account_number", String.class),
                            row.get("total_funds", Double.class),
                            row.get("is_active", Boolean.class),
                            row.get("create_on", LocalDateTime.class),
                            customer,
                            Collections.emptyList()
                    );
                })
                .all();
    }

    /**
     * Lists customers with accounts (scoped by organization and agency).
     *
     * @param organizationId organization identifier
     * @param agencyId agency identifier
     * @return customers
     */
    public Flux<CashierCustomerResponse> listCustomers(String organizationId, String agencyId) {
        requireOrganization(organizationId);
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT DISTINCT cp.id AS customer_id, p.id AS person_id, p.user_name, ");
        sql.append("p.user_first_name, p.mail, p.country, p.phone ");
        sql.append("FROM customer_profile cp ");
        sql.append("JOIN person p ON p.id = cp.person_id ");
        sql.append("JOIN account a ON a.client_id = cp.id ");
        sql.append("WHERE a.organization_id = :organizationId ");
        sql.append("ORDER BY p.user_first_name ASC");
        DatabaseClient.GenericExecuteSpec spec = entityTemplate.getDatabaseClient().sql(sql.toString())
                .bind("organizationId", organizationId);
        return spec.map((row, meta) -> new CustomerSeed(
                        row.get("customer_id", String.class),
                        row.get("person_id", String.class),
                        row.get("user_name", String.class),
                        row.get("user_first_name", String.class),
                        row.get("mail", String.class),
                        row.get("country", String.class),
                        row.get("phone", String.class)
                ))
                .all()
                .flatMap(seed -> fetchCashierAccounts(seed.customerId, organizationId, agencyId)
                        .collectList()
                        .map(accounts -> {
                            double total = accounts.stream()
                                    .map(CashierAccountSummaryResponse::getTotalFunds)
                                    .filter(value -> value != null)
                                    .mapToDouble(Double::doubleValue)
                                    .sum();
                            CashierPersonResponse person = new CashierPersonResponse(
                                    seed.personId,
                                    seed.userName,
                                    seed.userFirstName,
                                    seed.phone,
                                    seed.mail,
                                    seed.country
                            );
                            return new CashierCustomerResponse(
                                    seed.customerId,
                                    person,
                                    accounts,
                                    total,
                                    accounts.size()
                            );
                        }));
    }

    /**
     * Lists accounts for admin users (scoped by organization and agency).
     *
     * @param organizationId organization identifier
     * @param agencyId agency identifier
     * @return accounts
     */
    public Flux<AdminAccountResponse> listAdminAccounts(String organizationId, String agencyId) {
        requireOrganization(organizationId);
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT a.id AS account_id, a.account_number, a.total_funds, a.is_active, a.create_on, ");
        sql.append("p.id AS person_id, p.user_name, p.user_first_name ");
        sql.append("FROM account a ");
        sql.append("JOIN customer_profile cp ON cp.id = a.client_id ");
        sql.append("JOIN person p ON p.id = cp.person_id ");
        sql.append("WHERE a.organization_id = :organizationId ");
        sql.append("ORDER BY a.create_on DESC");
        DatabaseClient.GenericExecuteSpec spec = entityTemplate.getDatabaseClient().sql(sql.toString())
                .bind("organizationId", organizationId);
        return spec.map((row, meta) -> {
                    BigDecimal totalFundsValue = row.get("total_funds", BigDecimal.class);
                    Double totalFunds = totalFundsValue != null ? totalFundsValue.doubleValue() : null;
                    return new AdminAccountSeed(
                            row.get("account_id", String.class),
                            row.get("account_number", String.class),
                            totalFunds,
                            row.get("is_active", Boolean.class),
                            row.get("create_on", LocalDateTime.class),
                            row.get("person_id", String.class),
                            row.get("user_name", String.class),
                            row.get("user_first_name", String.class)
                    );
                })
                .all()
                .concatMap(seed -> Mono.zip(
                        fetchAccountEvents(seed.accountId, organizationId, agencyId).collectList(),
                        fetchAccountOperations(seed.accountId, organizationId, agencyId).collectList()
                ).map(tuple -> toAdminAccountResponse(seed, tuple.getT1(), tuple.getT2())));
    }

    /**
     * Lists customers for admin users (scoped by organization and agency).
     *
     * @param organizationId organization identifier
     * @param agencyId agency identifier
     * @return customers
     */
    public Flux<AdminCustomerResponse> listAdminCustomers(String organizationId, String agencyId) {
        requireOrganization(organizationId);
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT cp.id AS customer_id, p.id AS person_id, p.user_name, p.user_first_name, ");
        sql.append("p.mail, p.country, p.phone, ");
        sql.append("COALESCE(SUM(a.total_funds), 0) AS total_balance, ");
        sql.append("COUNT(a.id) AS accounts_count ");
        sql.append("FROM customer_profile cp ");
        sql.append("JOIN person p ON p.id = cp.person_id ");
        sql.append("JOIN account a ON a.client_id = cp.id ");
        sql.append("AND a.organization_id = :organizationId ");
        sql.append("GROUP BY cp.id, p.id, p.user_name, p.user_first_name, p.mail, p.country, p.phone ");
        sql.append("ORDER BY p.user_first_name ASC");
        DatabaseClient.GenericExecuteSpec spec = entityTemplate.getDatabaseClient().sql(sql.toString())
                .bind("organizationId", organizationId);
        return spec.map((row, meta) -> {
                    BigDecimal totalBalanceValue = row.get("total_balance", BigDecimal.class);
                    Number countValue = row.get("accounts_count", Number.class);
                    int accountsCount = countValue != null ? countValue.intValue() : 0;
                    double totalBalance = totalBalanceValue != null ? totalBalanceValue.doubleValue() : 0.0d;
                    return new AdminCustomerSeed(
                            row.get("customer_id", String.class),
                            row.get("person_id", String.class),
                            row.get("user_name", String.class),
                            row.get("user_first_name", String.class),
                            row.get("mail", String.class),
                            row.get("country", String.class),
                            row.get("phone", String.class),
                            totalBalance,
                            accountsCount
                    );
                })
                .all()
                .concatMap(seed -> fetchAdminCustomerAccounts(seed.customerId, organizationId, agencyId)
                        .collectList()
                        .map(accounts -> new AdminCustomerResponse(
                                seed.customerId,
                                new AdminCustomerPersonResponse(
                                        seed.personId,
                                        seed.userName,
                                        seed.userFirstName,
                                        seed.phone,
                                        seed.mail,
                                        seed.country
                                ),
                                seed.phone,
                                accounts,
                                seed.totalBalance,
                                seed.accountsCount
                        )));
    }

    /**
     * Searches customers by query.
     *
     * @param query search query
     * @return customers
     */
    public Flux<CustomerResponse> searchCustomers(String query, String organizationId, String agencyId) {
        String q = trimToNull(query);
        if (!StringUtils.hasText(q)) {
            return Flux.empty();
        }
        requireOrganization(organizationId);
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT DISTINCT cp.id AS customer_id, p.id AS person_id, p.user_name, ");
        sql.append("p.user_first_name, p.mail, p.country, p.phone ");
        sql.append("FROM customer_profile cp ");
        sql.append("JOIN person p ON p.id = cp.person_id ");
        sql.append("JOIN account a ON a.client_id = cp.id ");
        sql.append("WHERE a.organization_id = :organizationId ");
        sql.append("AND (LOWER(p.user_name) LIKE :nameToken OR p.phone LIKE :phoneToken) ");
        sql.append("ORDER BY p.user_first_name ASC");
        String token = "%" + q.toLowerCase() + "%";
        DatabaseClient.GenericExecuteSpec spec = entityTemplate.getDatabaseClient().sql(sql.toString())
                .bind("organizationId", organizationId)
                .bind("nameToken", token)
                .bind("phoneToken", "%" + q + "%");
        return spec.map((row, meta) -> new CustomerSeed(
                        row.get("customer_id", String.class),
                        row.get("person_id", String.class),
                        row.get("user_name", String.class),
                        row.get("user_first_name", String.class),
                        row.get("mail", String.class),
                        row.get("country", String.class),
                        row.get("phone", String.class)
                ))
                .all()
                .flatMap(seed -> enrichCustomer(seed, organizationId, agencyId));
    }

    /**
     * Creates a customer with an initial account.
     *
     * @param request request payload
     * @param actorId actor identifier
     * @param organizationId organization identifier
     * @return created customer
     */
    public Mono<CreateCustomerResponse> createCustomer(
            CreateCustomerRequest request,
            String actorId,
            String organizationId
    ) {
        if (request == null) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Customer payload is required."));
        }
        requireOrganization(organizationId);
        String phone = trimToNull(request.getPhone());
        String userName = trimToNull(request.getUserName());
        String userFirstName = trimToNull(request.getUserFirstName());
        String accountNumber = trimToNull(request.getAccountNumber());
        if (!StringUtils.hasText(phone) || !StringUtils.hasText(userName)
                || !StringUtils.hasText(userFirstName) || !StringUtils.hasText(accountNumber)) {
            return Mono.error(new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "phone, user_name, user_first_name, account_number are required"
            ));
        }
        Person person = new Person();
        person.setId(UUID.randomUUID().toString());
        person.setPhone(phone);
        person.setUserName(userName);
        person.setUserFirstName(userFirstName);
        person.setMail(trimToNull(request.getMail()));
        person.setCountry(trimToNull(request.getCountry()));
        person.setActif(true);

        CustomerProfile profile = new CustomerProfile();
        profile.setId(UUID.randomUUID().toString());
        profile.setPersonId(person.getId());
        profile.setProfession(trimToNull(request.getProfession()));
        profile.setDateOfJoining(LocalDateTime.now());

        Account account = new Account();
        account.setId(UUID.randomUUID().toString());
        account.setClientId(profile.getId());
        account.setAccountNumber(accountNumber);
        account.setIsActive(true);
        account.setCreateOn(LocalDateTime.now());
        account.setCreateBy(trimToNull(actorId));
        account.setOrganizationId(trimToNull(organizationId));
        account.setTotalFunds(resolveBalance(request.getInitialBalance()));

        Mono<CreateCustomerResponse> insertFlow = personRepository.save(person)
                .flatMap(savedPerson -> customerProfileRepository.save(profile)
                        .flatMap(savedProfile -> accountRepository.save(account)
                                .map(savedAccount -> new CreateCustomerResponse(
                                        savedProfile.getId(),
                                        toPersonDetail(savedPerson),
                                        List.of(toAccountResponse(savedAccount, savedPerson))
                                ))));

        return transactionalOperator.transactional(insertFlow);
    }

    /**
     * Deposits into an account.
     *
     * @param accountId account identifier
     * @param amount amount
     * @param reference reference
     * @param reason reason detail
     * @param ticketing ticketing payload
     * @param paymentMethod payment method
     * @param actorId actor identifier
     * @return transfer response
     */
    public Mono<AccountTransferResponse> deposit(
            String accountId,
            BigDecimal amount,
            String reference,
            String reason,
            TicketingRequest ticketing,
            PaymentMethod paymentMethod,
            String actorId
    ) {
        return applyMovement(
                accountId,
                amount,
                reference,
                reason,
                ticketing,
                paymentMethod,
                actorId,
                true
        );
    }

    /**
     * Withdraws from an account.
     *
     * @param accountId account identifier
     * @param amount amount
     * @param reference reference
     * @param reason reason detail
     * @param ticketing ticketing payload
     * @param paymentMethod payment method
     * @param actorId actor identifier
     * @return transfer response
     */
    public Mono<AccountTransferResponse> withdraw(
            String accountId,
            BigDecimal amount,
            String reference,
            String reason,
            TicketingRequest ticketing,
            PaymentMethod paymentMethod,
            String actorId
    ) {
        return applyMovement(
                accountId,
                amount,
                reference,
                reason,
                ticketing,
                paymentMethod,
                actorId,
                false
        );
    }

    /**
     * Transfers funds between accounts.
     *
     * @param request transfer request
     * @param actorId actor identifier
     * @return transfer response
     */
    public Mono<AccountP2PTransferResponse> transferP2P(AccountP2PTransferRequest request, String actorId) {
        if (request == null) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Transfer payload is required."));
        }
        String sourceId = trimToNull(request.getSourceAccountId());
        String destId = trimToNull(request.getDestAccountId());
        if (!StringUtils.hasText(sourceId) || !StringUtils.hasText(destId)) {
            return Mono.error(new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "source_account_id and dest_account_id are required"
            ));
        }
        BigDecimal amount = request.getAmount();
        if (amount == null || amount.signum() <= 0) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "amount must be positive"));
        }

        return Mono.zip(loadAccount(sourceId), loadAccount(destId), requireOpenSession(actorId))
                .flatMap(tuple -> {
                    Account source = tuple.getT1();
                    Account dest = tuple.getT2();
                    CashRegisterSession session = tuple.getT3();
                    double sourceBalance = safeBalance(source.getTotalFunds());
                    if (sourceBalance < amount.doubleValue()) {
                        return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Insufficient funds"));
                    }
                    source.setTotalFunds(sourceBalance - amount.doubleValue());
                    dest.setTotalFunds(safeBalance(dest.getTotalFunds()) + amount.doubleValue());

                    String sourceAccountId = source.getId();
                    String destAccountId = dest.getId();
                    CashRegisterMovement outMovement = buildMovement(
                            session.getId(),
                            "sortie",
                            amount,
                            "p2p_transfer",
                            null,
                            sourceAccountId,
                            destAccountId,
                            request.getTicketing(),
                            request.getReference(),
                            null,
                            actorId
                    );
                    CashRegisterMovement inMovement = buildMovement(
                            session.getId(),
                            "entree",
                            amount,
                            "p2p_transfer",
                            null,
                            sourceAccountId,
                            destAccountId,
                            request.getTicketing(),
                            request.getReference(),
                            null,
                            actorId
                    );

                    Mono<Tuple3<CashRegisterMovement, CashRegisterMovement, AccountP2PTransferResponse>> flow =
                            accountRepository.save(source)
                            .then(accountRepository.save(dest))
                            .then(movementRepository.save(outMovement))
                            .zipWith(movementRepository.save(inMovement))
                            .map(tupleSave -> Tuples.of(
                                    tupleSave.getT1(),
                                    tupleSave.getT2(),
                                    new AccountP2PTransferResponse(
                                            true,
                                            tupleSave.getT2().getId(),
                                            tupleSave.getT1().getId(),
                                            request.getReference()
                                    )
                            ));

                    return transactionalOperator.transactional(flow)
                            .doOnSuccess(tupleSave -> {
                                accountingService.syncMovementAsync(
                                        tupleSave.getT1(),
                                        dest.getAccountingAccount(),
                                        source.getAccountingAccount()
                                );
                                accountingService.syncMovementAsync(
                                        tupleSave.getT2(),
                                        dest.getAccountingAccount(),
                                        source.getAccountingAccount()
                                );
                                auditService.recordMovementEventAsync(tupleSave.getT1());
                                auditService.recordMovementEventAsync(tupleSave.getT2());
                            })
                            .map(Tuple3::getT3);
                });
    }

    /**
     * Requests cash funds from the oldest available cashier in the same agency.
     *
     * @param request fund request payload
     * @param actorId cashier identifier
     * @return transfer response
     */
    public Mono<AccountP2PTransferResponse> requestFunds(CashierFundRequest request, String actorId) {
        if (request == null) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Request payload is required."));
        }
        BigDecimal amount = request.getAmount();
        if (amount == null || amount.signum() <= 0) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "amount must be positive"));
        }

        return requireOpenSession(actorId)
                .flatMap(requesterSession -> resolveRegisterForSession(requesterSession)
                        .flatMap(requesterRegister -> findDonorSessionWithRegister(
                                        requesterRegister.getAgencyId(),
                                        requesterRegister.getId()
                                )
                                .flatMap(donorTuple -> {
                                    CashRegisterSession donorSession = donorTuple.getT1();
                                    CashRegister donorRegister = donorTuple.getT2();
                                    String requesterMac = trimToNull(requesterRegister.getMacAddress());
                                    String donorMac = trimToNull(donorRegister.getMacAddress());
                                    if (!StringUtils.hasText(requesterMac) || !StringUtils.hasText(donorMac)) {
                                        return Mono.error(new ResponseStatusException(
                                                HttpStatus.BAD_REQUEST,
                                                "Cash register MAC address is required."
                                        ));
                                    }
                                    String reason = "fund request";
                                    CashRegisterMovement outMovement = buildMovement(
                                            donorSession.getId(),
                                            "sortie",
                                            amount,
                                            reason,
                                            request.getReason(),
                                            donorMac,
                                            requesterMac,
                                            request.getTicketing(),
                                            request.getReference(),
                                            PaymentMethod.CASH,
                                            actorId
                                    );
                                    CashRegisterMovement inMovement = buildMovement(
                                            requesterSession.getId(),
                                            "entree",
                                            amount,
                                            reason,
                                            request.getReason(),
                                            donorMac,
                                            requesterMac,
                                            request.getTicketing(),
                                            request.getReference(),
                                            PaymentMethod.CASH,
                                            actorId
                                    );

                                    Mono<Tuple3<CashRegisterMovement, CashRegisterMovement, AccountP2PTransferResponse>> flow =
                                            movementRepository.save(outMovement)
                                            .zipWith(movementRepository.save(inMovement))
                                            .map(tupleSave -> Tuples.of(
                                                    tupleSave.getT1(),
                                                    tupleSave.getT2(),
                                                    new AccountP2PTransferResponse(
                                                            true,
                                                            tupleSave.getT2().getId(),
                                                            tupleSave.getT1().getId(),
                                                            request.getReference()
                                                    )
                                            ));

                                    String emitterAccounting = trimToNull(donorRegister.getSaleAgentAccountingAccount());
                                    String recipientAccounting = trimToNull(requesterRegister.getSaleAgentAccountingAccount());

                                    return transactionalOperator.transactional(flow)
                                            .doOnSuccess(tupleSave -> {
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
                                            .map(Tuple3::getT3);
                                })));
    }

    /**
     * Lists fund requests for the cashier agency.
     *
     * @param actorId cashier identifier
     * @param agencyId agency identifier from token when available
     * @return fund requests
     */
    public Flux<FundRequestResponse> listFundRequests(String actorId, String agencyId) {
        String resolvedAgencyId = trimToNull(agencyId);
        if (StringUtils.hasText(resolvedAgencyId)) {
            return listFundRequestsByAgency(resolvedAgencyId);
        }
        return requireOpenSession(actorId)
                .flatMap(this::resolveRegisterForSession)
                .flatMapMany(register -> listFundRequestsByAgency(register.getAgencyId()));
    }

    private Mono<AccountTransferResponse> applyMovement(
            String accountId,
            BigDecimal amount,
            String reference,
            String reason,
            Object ticketing,
            PaymentMethod paymentMethod,
            String actorId,
            boolean isDeposit
    ) {
        String resolvedAccountId = trimToNull(accountId);
        if (!StringUtils.hasText(resolvedAccountId)) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "account_id is required"));
        }
        if (amount == null || amount.signum() <= 0) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "amount must be positive"));
        }

        return Mono.zip(
                        loadAccount(resolvedAccountId),
                        requireOpenSession(actorId),
                        requireSalesAgentAccount(actorId)
                )
                .flatMap(tuple -> {
                    Account account = tuple.getT1();
                    CashRegisterSession session = tuple.getT2();
                    Account cashierAccount = tuple.getT3();
                    double currentBalance = safeBalance(account.getTotalFunds());
                    if (!isDeposit && currentBalance < amount.doubleValue()) {
                        return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Insufficient funds"));
                    }
                    double newBalance = isDeposit
                            ? currentBalance + amount.doubleValue()
                            : currentBalance - amount.doubleValue();
                    account.setTotalFunds(newBalance);

                    String emitterId = isDeposit ? account.getId() : cashierAccount.getId();
                    String recipientId = isDeposit ? cashierAccount.getId() : account.getId();
                    CashRegisterMovement movement = buildMovement(
                            session.getId(),
                            isDeposit ? "entree" : "sortie",
                            amount,
                            isDeposit ? "deposit" : "withdrawal",
                            reason,
                            emitterId,
                            recipientId,
                            ticketing,
                            reference,
                            paymentMethod,
                            actorId
                    );

                    Mono<Tuple2<CashRegisterMovement, AccountTransferResponse>> flow =
                            accountRepository.save(account)
                            .then(movementRepository.save(movement))
                            .map(savedMovement -> Tuples.of(
                                    savedMovement,
                                    new AccountTransferResponse(
                                            true,
                                            newBalance,
                                            savedMovement.getId(),
                                            reference
                                    )
                            ));

                    String emitterAccounting = isDeposit
                            ? account.getAccountingAccount()
                            : cashierAccount.getAccountingAccount();
                    String recipientAccounting = isDeposit
                            ? cashierAccount.getAccountingAccount()
                            : account.getAccountingAccount();

                    return transactionalOperator.transactional(flow)
                            .doOnSuccess(tupleSave -> accountingService.syncMovementAsync(
                                    tupleSave.getT1(),
                                    recipientAccounting,
                                    emitterAccounting
                            ))
                            .doOnSuccess(tupleSave -> auditService.recordMovementEventAsync(tupleSave.getT1()))
                            .map(Tuple2::getT2);
                });
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

    private CashRegisterMovement buildMovement(
            String sessionId,
            String sense,
            BigDecimal amount,
            String reason,
            String reasonDetail,
            String emitterId,
            String recipientId,
            Object ticketing,
            String reference,
            PaymentMethod paymentMethod,
            String actorId
    ) {
        CashRegisterMovement movement = new CashRegisterMovement();
        movement.setId(UUID.randomUUID().toString());
        movement.setSessionId(sessionId);
        movement.setSense(sense);
        movement.setAmount(amount);
        movement.setReason(reason);
        movement.setReasonDetail(trimToNull(reasonDetail));
        movement.setEmitterId(trimToNull(emitterId));
        movement.setRecipientId(trimToNull(recipientId));
        movement.setIsAccounted(Boolean.FALSE);
        movement.setEventTicketingDetails(hasTicketingDetails(ticketing));
        movement.setExternalReference(trimToNull(reference));
        movement.setPaymentMethod(paymentMethod != null ? paymentMethod.name() : null);
        movement.setCreateOn(LocalDateTime.now());
        movement.setCreateBy(trimToNull(actorId));
        movement.setIsDeleted(false);
        movement.markNew();
        return movement;
    }

    private boolean hasTicketingDetails(Object ticketing) {
        if (ticketing == null) {
            return false;
        }
        if (ticketing instanceof TicketingRequest request) {
            if (request.getTotal() != null && request.getTotal().signum() > 0) {
                return true;
            }
            return request.getDenominations() != null && !request.getDenominations().isEmpty();
        }
        if (ticketing instanceof List<?> list) {
            return !list.isEmpty();
        }
        return true;
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

    private Mono<CashRegister> resolveRegisterForSession(CashRegisterSession session) {
        if (session == null || !StringUtils.hasText(session.getCashRegisterId())) {
            return Mono.error(new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Cash register context is required."
            ));
        }
        return entityTemplate.select(CashRegister.class)
                .matching(org.springframework.data.relational.core.query.Query.query(
                        org.springframework.data.relational.core.query.Criteria.where("id")
                                .is(session.getCashRegisterId())
                ))
                .one()
                .switchIfEmpty(Mono.error(new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Cash register not found."
                )));
    }

    private Mono<Tuple2<CashRegisterSession, CashRegister>> findDonorSessionWithRegister(
            String requesterAgencyId,
            String requesterRegisterId
    ) {
        String agencyId = trimToNull(requesterAgencyId);
        if (!StringUtils.hasText(agencyId)) {
            return Mono.error(new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Agency scope is required."
            ));
        }
        return sessionRepository.findOpenByAgency(agencyId, STATE_OPEN)
                .filter(session -> !StringUtils.hasText(requesterRegisterId)
                        || !requesterRegisterId.equals(session.getCashRegisterId()))
                .concatMap(session -> resolveRegisterForSession(session)
                        .filter(register -> agencyId.equals(register.getAgencyId()))
                        .map(register -> Tuples.of(session, register))
                )
                .next()
                .switchIfEmpty(Mono.error(new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "No available cashier found in agency."
                )));
    }

    private Flux<FundRequestResponse> listFundRequestsByAgency(String agencyId) {
        if (!StringUtils.hasText(agencyId)) {
            return Flux.error(new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Agency scope is required."
            ));
        }
        String sql = "SELECT m.id, m.amount, m.reason_detail, m.reason AS base_reason, m.sense, m.create_on, "
                + "r.town AS session_town, r.mac_address AS session_mac, "
                + "src.town AS source_town, src.country AS source_country, src.adress AS source_adress, "
                + "src.mac_address AS source_mac, "
                + "dst.town AS dest_town, dst.country AS dest_country, dst.adress AS dest_adress, "
                + "dst.mac_address AS dest_mac "
                + "FROM cash_register_movement m "
                + "JOIN cash_register_session s ON s.id = m.session_id "
                + "JOIN cash_register r ON r.id = s.cash_register_id "
                + "LEFT JOIN cash_register src ON src.mac_address = m.emitter_id "
                + "LEFT JOIN cash_register dst ON dst.mac_address = m.recipient_id "
                + "WHERE r.agency_id = :agencyId "
                + "AND m.reason = :reason "
                + "AND m.sense = :sense "
                + "AND (m.is_deleted = false OR m.is_deleted IS NULL) "
                + "ORDER BY m.create_on DESC";
        return entityTemplate.getDatabaseClient()
                .sql(sql)
                .bind("agencyId", agencyId)
                .bind("reason", "fund request")
                .bind("sense", "entree")
                .map(this::mapFundRequestRow)
                .all()
                .flatMap(this::enrichFundRequest);
    }

    private FundRequestResponse mapFundRequestRow(Row row, RowMetadata metadata) {
        FundRequestResponse response = new FundRequestResponse();
        response.setId(row.get("id", String.class));
        response.setAmount(row.get("amount", BigDecimal.class));
        String reasonDetail = row.get("reason_detail", String.class);
        if (!StringUtils.hasText(reasonDetail)) {
            reasonDetail = row.get("base_reason", String.class);
        }
        response.setReason(reasonDetail);
        response.setSense(row.get("sense", String.class));
        response.setCreateOn(row.get("create_on", LocalDateTime.class));

        FundRequestResponse.CashRegisterInfo sessionRegister = new FundRequestResponse.CashRegisterInfo(
                row.get("session_town", String.class),
                row.get("session_mac", String.class)
        );
        response.setSession(new FundRequestResponse.SessionInfo(sessionRegister));
        response.setSourceRegister(new FundRequestResponse.RegisterInfo(
                row.get("source_town", String.class),
                row.get("source_country", String.class),
                row.get("source_adress", String.class),
                row.get("source_mac", String.class)
        ));
        response.setDestinationRegister(new FundRequestResponse.RegisterInfo(
                row.get("dest_town", String.class),
                row.get("dest_country", String.class),
                row.get("dest_adress", String.class),
                row.get("dest_mac", String.class)
        ));
        response.setTicketingDetails(new ArrayList<>());
        return response;
    }

    private Mono<FundRequestResponse> enrichFundRequest(FundRequestResponse response) {
        return fetchMovementTicketingDetails(response.getId())
                .collectList()
                .map(ticketingDetails -> {
                    response.setTicketingDetails(ticketingDetails);
                    return response;
                });
    }

    private Flux<FundRequestResponse.TicketingDetail> fetchMovementTicketingDetails(String movementId) {
        if (!StringUtils.hasText(movementId)) {
            return Flux.empty();
        }
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT d.quantity, d.value, d.total, ");
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

    private FundRequestResponse.TicketingDetail mapTicketingRow(Row row, RowMetadata metadata) {
        CashRegisterTicketingDenominationResponse denomination = null;
        BigDecimal denominationValue = row.get("denomination_value", BigDecimal.class);
        String denominationLabel = row.get("denomination_label", String.class);
        if (denominationValue != null || StringUtils.hasText(denominationLabel)) {
            denomination = new CashRegisterTicketingDenominationResponse(denominationValue, denominationLabel);
        }
        return new FundRequestResponse.TicketingDetail(
                row.get("quantity", Integer.class),
                row.get("value", BigDecimal.class),
                row.get("total", BigDecimal.class),
                denomination
        );
    }

    private Flux<AdminAccountEventResponse> fetchAccountEvents(
            String accountId,
            String organizationId,
            String agencyId
    ) {
        if (!StringUtils.hasText(accountId)) {
            return Flux.empty();
        }
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT e.id, e.type, e.date_time, e.payload ");
        sql.append("FROM cash_register_event e ");
        sql.append("JOIN account a ON a.id = e.account_id ");
        sql.append("LEFT JOIN cash_register_session s ON s.id = e.session_id ");
        sql.append("LEFT JOIN cash_register r ON r.id = s.cash_register_id ");
        sql.append("LEFT JOIN agency ag ON ag.id = r.agency_id ");
        sql.append("WHERE e.account_id = :accountId ");
        sql.append("AND a.organization_id = :organizationId ");
        sql.append("ORDER BY e.date_time DESC");
        DatabaseClient.GenericExecuteSpec spec = entityTemplate.getDatabaseClient().sql(sql.toString())
                .bind("accountId", accountId)
                .bind("organizationId", organizationId);
        return spec.map((row, meta) -> new AdminAccountEventResponse(
                        row.get("id", String.class),
                        row.get("type", String.class),
                        row.get("date_time", LocalDateTime.class),
                        row.get("payload", String.class)
                ))
                .all();
    }

    private Flux<AdminAccountOperationResponse> fetchAccountOperations(
            String accountId,
            String organizationId,
            String agencyId
    ) {
        if (!StringUtils.hasText(accountId)) {
            return Flux.empty();
        }
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT m.id, m.amount, m.sense, m.reason, m.external_reference, ");
        sql.append("m.create_on, m.recipient_id, m.emitter_id, r.id AS register_id, ");
        sql.append("r.town AS register_town, p.user_first_name AS creator_first_name, ");
        sql.append("p.user_name AS creator_user_name ");
        sql.append("FROM cash_register_movement m ");
        sql.append("LEFT JOIN cash_register_session s ON s.id = m.session_id ");
        sql.append("LEFT JOIN cash_register r ON r.id = s.cash_register_id ");
        sql.append("LEFT JOIN agency ag ON ag.id = r.agency_id ");
        sql.append("LEFT JOIN person p ON p.id = m.create_by ");
        sql.append("WHERE m.is_deleted = false ");
        sql.append("AND (m.recipient_id = :accountId OR m.emitter_id = :accountId) ");
        sql.append("AND ag.organization_id = :organizationId ");
        sql.append("ORDER BY m.create_on DESC");
        DatabaseClient.GenericExecuteSpec spec = entityTemplate.getDatabaseClient().sql(sql.toString())
                .bind("accountId", accountId)
                .bind("organizationId", organizationId);
        return spec.map((row, meta) -> {
                    String registerId = row.get("register_id", String.class);
                    String registerTown = row.get("register_town", String.class);
                    AdminAccountOperationSessionResponse session = null;
                    if (StringUtils.hasText(registerTown) || StringUtils.hasText(registerId)) {
                        session = new AdminAccountOperationSessionResponse(
                                new AdminAccountOperationRegisterResponse(registerId, registerTown)
                        );
                    }
                    String creatorName = row.get("creator_first_name", String.class);
                    String creatorUserName = row.get("creator_user_name", String.class);
                    AdminAccountOperationCreatorResponse creator = null;
                    if (StringUtils.hasText(creatorName) || StringUtils.hasText(creatorUserName)) {
                        creator = new AdminAccountOperationCreatorResponse(creatorName, creatorUserName);
                    }
                    return new AdminAccountOperationResponse(
                            row.get("id", String.class),
                            row.get("amount", BigDecimal.class),
                            mapMovementSense(row.get("sense", String.class)),
                            row.get("reason", String.class),
                            row.get("external_reference", String.class),
                            row.get("create_on", LocalDateTime.class),
                            row.get("recipient_id", String.class),
                            row.get("emitter_id", String.class),
                            session,
                            creator
                    );
                })
                .all();
    }

    private AdminAccountResponse toAdminAccountResponse(
            AdminAccountSeed seed,
            List<AdminAccountEventResponse> events,
            List<AdminAccountOperationResponse> operations
    ) {
        AccountOwnerResponse owner = new AccountOwnerResponse(
                resolveOwnerName(seed.userFirstName, seed.userName),
                seed.userName,
                "customer"
        );
        return new AdminAccountResponse(
                seed.accountId,
                seed.accountNumber,
                seed.totalFunds,
                seed.isActive,
                seed.createOn,
                seed.personId,
                owner,
                events,
                operations
        );
    }

    private String resolveOwnerName(String firstName, String userName) {
        if (StringUtils.hasText(firstName)) {
            return firstName;
        }
        return StringUtils.hasText(userName) ? userName : null;
    }

    private String mapMovementSense(String sense) {
        if (!StringUtils.hasText(sense)) {
            return null;
        }
        String trimmed = sense.trim().toLowerCase();
        if ("entree".equals(trimmed) || "in".equals(trimmed)) {
            return "entree";
        }
        if ("sortie".equals(trimmed) || "out".equals(trimmed) || "transfert".equals(trimmed)) {
            return "sortie";
        }
        return trimmed;
    }

    private void requireOrganization(String organizationId) {
        if (!StringUtils.hasText(organizationId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Organization scope is required.");
        }
    }

    private AccountResponse toAccountResponse(
            String id,
            String accountNumber,
            String accountingAccount,
            Double totalFunds,
            Boolean isActive,
            LocalDateTime createOn,
            String ownerId,
            String personId,
            String userName,
            String userFirstName
    ) {
        String accountLabel = buildAccountLabel(accountNumber, accountingAccount);
        return new AccountResponse(
                id,
                accountNumber,
                accountNumber,
                accountingAccount,
                accountLabel,
                totalFunds,
                isActive,
                createOn,
                ownerId,
                new AccountOwnerResponse(userFirstName, userName, "customer"),
                Collections.<AccountEventResponse>emptyList(),
                Collections.emptyList()
        );
    }

    private AccountResponse toAccountResponse(Account account, Person person) {
        return toAccountResponse(
                account.getId(),
                account.getAccountNumber(),
                account.getAccountingAccount(),
                account.getTotalFunds(),
                account.getIsActive(),
                account.getCreateOn(),
                account.getClientId(),
                person.getId(),
                person.getUserName(),
                person.getUserFirstName()
        );
    }

    private String buildAccountLabel(String accountNumber, String accountingAccount) {
        String number = trimToNull(accountNumber);
        String accounting = trimToNull(accountingAccount);
        if (!StringUtils.hasText(number) && !StringUtils.hasText(accounting)) {
            return null;
        }
        if (!StringUtils.hasText(accounting)) {
            return number;
        }
        if (!StringUtils.hasText(number)) {
            return accounting;
        }
        return number + " (" + accounting + ")";
    }

    private Mono<CustomerResponse> enrichCustomer(CustomerSeed seed, String organizationId, String agencyId) {
        return fetchAccounts(seed.customerId, organizationId, agencyId)
                .collectList()
                .map(accounts -> {
                    double total = accounts.stream()
                            .map(AccountResponse::getTotalFunds)
                            .filter(value -> value != null)
                            .mapToDouble(Double::doubleValue)
                            .sum();
                    String primaryAccount = accounts.stream()
                            .map(AccountResponse::getAccountNumber)
                            .filter(StringUtils::hasText)
                            .findFirst()
                            .orElse(null);
                    PersonDetailResponse person = new PersonDetailResponse(
                            seed.personId,
                            seed.userName,
                            seed.userFirstName,
                            seed.mail,
                            seed.country,
                            seed.phone,
                            primaryAccount
                    );
                    return new CustomerResponse(
                            seed.customerId,
                            person,
                            seed.phone,
                            accounts,
                            total,
                            accounts.size()
                    );
                });
    }

    private Flux<AccountResponse> fetchAccounts(String customerId, String organizationId, String agencyId) {
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT a.id, a.account_number, a.accounting_account, a.total_funds, ");
        sql.append("a.is_active, a.create_on, a.client_id, ");
        sql.append("p.id AS person_id, p.user_name, p.user_first_name ");
        sql.append("FROM account a ");
        sql.append("JOIN customer_profile cp ON cp.id = a.client_id ");
        sql.append("JOIN person p ON p.id = cp.person_id ");
        sql.append("WHERE a.client_id = :customerId ");
        sql.append("AND a.organization_id = :organizationId ");
        DatabaseClient.GenericExecuteSpec spec = entityTemplate.getDatabaseClient().sql(sql.toString())
                .bind("customerId", customerId)
                .bind("organizationId", organizationId);
        return spec.map((row, meta) -> toAccountResponse(
                        row.get("id", String.class),
                        row.get("account_number", String.class),
                        row.get("accounting_account", String.class),
                        row.get("total_funds", Double.class),
                        row.get("is_active", Boolean.class),
                        row.get("create_on", LocalDateTime.class),
                        row.get("client_id", String.class),
                        row.get("person_id", String.class),
                        row.get("user_name", String.class),
                        row.get("user_first_name", String.class)
                ))
                .all();
    }

    private Flux<CashierAccountSummaryResponse> fetchCashierAccounts(
            String customerId,
            String organizationId,
            String agencyId
    ) {
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT a.id, a.account_number, a.total_funds, a.is_active, a.create_on ");
        sql.append("FROM account a ");
        sql.append("WHERE a.client_id = :customerId ");
        sql.append("AND a.organization_id = :organizationId ");
        sql.append("ORDER BY a.create_on DESC");
        DatabaseClient.GenericExecuteSpec spec = entityTemplate.getDatabaseClient().sql(sql.toString())
                .bind("customerId", customerId)
                .bind("organizationId", organizationId);
        return spec.map((row, meta) -> new CashierAccountSummaryResponse(
                        row.get("id", String.class),
                        row.get("account_number", String.class),
                        row.get("total_funds", Double.class),
                        row.get("is_active", Boolean.class),
                        row.get("create_on", LocalDateTime.class)
                ))
                .all();
    }

    private Flux<AdminCustomerAccountResponse> fetchAdminCustomerAccounts(
            String customerId,
            String organizationId,
            String agencyId
    ) {
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT a.id, a.account_number, a.total_funds, a.is_active, a.create_on ");
        sql.append("FROM account a ");
        sql.append("WHERE a.client_id = :customerId ");
        sql.append("AND a.organization_id = :organizationId ");
        sql.append("ORDER BY a.create_on DESC");
        DatabaseClient.GenericExecuteSpec spec = entityTemplate.getDatabaseClient().sql(sql.toString())
                .bind("customerId", customerId)
                .bind("organizationId", organizationId);
        return spec.map((row, meta) -> new AdminCustomerAccountResponse(
                        row.get("id", String.class),
                        row.get("account_number", String.class),
                        row.get("total_funds", Double.class),
                        row.get("is_active", Boolean.class),
                        row.get("create_on", LocalDateTime.class)
                ))
                .all();
    }

    private PersonDetailResponse toPersonDetail(Person person) {
        return new PersonDetailResponse(
                person.getId(),
                person.getUserName(),
                person.getUserFirstName(),
                person.getMail(),
                person.getCountry(),
                person.getPhone(),
                null
        );
    }

    private double safeBalance(Double value) {
        return value != null ? value : 0.0d;
    }

    private double resolveBalance(Double value) {
        return value != null ? value : 0.0d;
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private static class CustomerSeed {
        private final String customerId;
        private final String personId;
        private final String userName;
        private final String userFirstName;
        private final String mail;
        private final String country;
        private final String phone;

        private CustomerSeed(
                String customerId,
                String personId,
                String userName,
                String userFirstName,
                String mail,
                String country,
                String phone
        ) {
            this.customerId = customerId;
            this.personId = personId;
            this.userName = userName;
            this.userFirstName = userFirstName;
            this.mail = mail;
            this.country = country;
            this.phone = phone;
        }
    }

    private static class AdminAccountSeed {
        private final String accountId;
        private final String accountNumber;
        private final Double totalFunds;
        private final Boolean isActive;
        private final LocalDateTime createOn;
        private final String personId;
        private final String userName;
        private final String userFirstName;

        private AdminAccountSeed(
                String accountId,
                String accountNumber,
                Double totalFunds,
                Boolean isActive,
                LocalDateTime createOn,
                String personId,
                String userName,
                String userFirstName
        ) {
            this.accountId = accountId;
            this.accountNumber = accountNumber;
            this.totalFunds = totalFunds;
            this.isActive = isActive;
            this.createOn = createOn;
            this.personId = personId;
            this.userName = userName;
            this.userFirstName = userFirstName;
        }
    }

    private static class AdminCustomerSeed {
        private final String customerId;
        private final String personId;
        private final String userName;
        private final String userFirstName;
        private final String mail;
        private final String country;
        private final String phone;
        private final Double totalBalance;
        private final int accountsCount;

        private AdminCustomerSeed(
                String customerId,
                String personId,
                String userName,
                String userFirstName,
                String mail,
                String country,
                String phone,
                Double totalBalance,
                int accountsCount
        ) {
            this.customerId = customerId;
            this.personId = personId;
            this.userName = userName;
            this.userFirstName = userFirstName;
            this.mail = mail;
            this.country = country;
            this.phone = phone;
            this.totalBalance = totalBalance;
            this.accountsCount = accountsCount;
        }
    }
}
