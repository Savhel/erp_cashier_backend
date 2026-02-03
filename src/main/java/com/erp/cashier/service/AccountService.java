package com.erp.cashier.service;

import com.erp.cashier.dto.AccountEventResponse;
import com.erp.cashier.dto.AccountOwnerResponse;
import com.erp.cashier.dto.AccountP2PTransferRequest;
import com.erp.cashier.dto.AccountP2PTransferResponse;
import com.erp.cashier.dto.AccountResponse;
import com.erp.cashier.dto.AccountTransferResponse;
import com.erp.cashier.dto.CreateCustomerRequest;
import com.erp.cashier.dto.CreateCustomerResponse;
import com.erp.cashier.dto.CustomerResponse;
import com.erp.cashier.dto.PersonDetailResponse;
import com.erp.cashier.model.Account;
import com.erp.cashier.model.CashRegisterMovement;
import com.erp.cashier.model.CashRegisterSession;
import com.erp.cashier.model.CustomerProfile;
import com.erp.cashier.model.Person;
import com.erp.cashier.repository.AccountRepository;
import com.erp.cashier.repository.CashRegisterMovementRepository;
import com.erp.cashier.repository.CashRegisterSessionRepository;
import com.erp.cashier.repository.CustomerProfileRepository;
import com.erp.cashier.repository.PersonRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
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
            ReactiveTransactionManager transactionManager
    ) {
        this.personRepository = personRepository;
        this.customerProfileRepository = customerProfileRepository;
        this.accountRepository = accountRepository;
        this.movementRepository = movementRepository;
        this.sessionRepository = sessionRepository;
        this.entityTemplate = entityTemplate;
        this.transactionalOperator = TransactionalOperator.create(transactionManager);
    }

    /**
     * Lists accounts for admin users.
     *
     * @return accounts
     */
    public Flux<AccountResponse> listAccounts() {
        String sql = "SELECT a.id, a.account_number, a.total_funds, a.is_active, a.create_on, a.client_id, "
                + "p.id AS person_id, p.user_name, p.user_first_name "
                + "FROM account a "
                + "JOIN customer_profile cp ON cp.id = a.client_id "
                + "JOIN person p ON p.id = cp.person_id "
                + "ORDER BY a.create_on DESC";
        DatabaseClient client = entityTemplate.getDatabaseClient();
        return client.sql(sql)
                .map((row, meta) -> toAccountResponse(
                        row.get("id", String.class),
                        row.get("account_number", String.class),
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

    /**
     * Lists customers with accounts.
     *
     * @return customers
     */
    public Flux<CustomerResponse> listCustomers() {
        String sql = "SELECT cp.id AS customer_id, p.id AS person_id, p.user_name, p.user_first_name, "
                + "p.mail, p.country, p.phone, p.account_number "
                + "FROM customer_profile cp "
                + "JOIN person p ON p.id = cp.person_id "
                + "ORDER BY p.user_first_name ASC";
        DatabaseClient client = entityTemplate.getDatabaseClient();
        return client.sql(sql)
                .map((row, meta) -> new CustomerSeed(
                        row.get("customer_id", String.class),
                        row.get("person_id", String.class),
                        row.get("user_name", String.class),
                        row.get("user_first_name", String.class),
                        row.get("mail", String.class),
                        row.get("country", String.class),
                        row.get("phone", String.class),
                        row.get("account_number", String.class)
                ))
                .all()
                .flatMap(this::enrichCustomer);
    }

    /**
     * Searches customers by query.
     *
     * @param query search query
     * @return customers
     */
    public Flux<CustomerResponse> searchCustomers(String query) {
        String q = trimToNull(query);
        if (!StringUtils.hasText(q)) {
            return Flux.empty();
        }
        String sql = "SELECT cp.id AS customer_id, p.id AS person_id, p.user_name, p.user_first_name, "
                + "p.mail, p.country, p.phone, p.account_number "
                + "FROM customer_profile cp "
                + "JOIN person p ON p.id = cp.person_id "
                + "WHERE LOWER(p.user_name) LIKE $1 OR p.phone LIKE $2 "
                + "ORDER BY p.user_first_name ASC";
        String token = "%" + q.toLowerCase() + "%";
        DatabaseClient client = entityTemplate.getDatabaseClient();
        return client.sql(sql)
                .bind(0, token)
                .bind(1, "%" + q + "%")
                .map((row, meta) -> new CustomerSeed(
                        row.get("customer_id", String.class),
                        row.get("person_id", String.class),
                        row.get("user_name", String.class),
                        row.get("user_first_name", String.class),
                        row.get("mail", String.class),
                        row.get("country", String.class),
                        row.get("phone", String.class),
                        row.get("account_number", String.class)
                ))
                .all()
                .flatMap(this::enrichCustomer);
    }

    /**
     * Creates a customer with an initial account.
     *
     * @param request request payload
     * @param actorId actor identifier
     * @return created customer
     */
    public Mono<CreateCustomerResponse> createCustomer(CreateCustomerRequest request, String actorId) {
        if (request == null) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Customer payload is required."));
        }
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
        person.setAccountNumber(accountNumber);
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
     * @param ticketing ticketing entries
     * @param actorId actor identifier
     * @return transfer response
     */
    public Mono<AccountTransferResponse> deposit(
            String accountId,
            BigDecimal amount,
            String reference,
            List<?> ticketing,
            String actorId
    ) {
        return applyMovement(accountId, amount, reference, ticketing, actorId, true);
    }

    /**
     * Withdraws from an account.
     *
     * @param accountId account identifier
     * @param amount amount
     * @param reference reference
     * @param ticketing ticketing entries
     * @param actorId actor identifier
     * @return transfer response
     */
    public Mono<AccountTransferResponse> withdraw(
            String accountId,
            BigDecimal amount,
            String reference,
            List<?> ticketing,
            String actorId
    ) {
        return applyMovement(accountId, amount, reference, ticketing, actorId, false);
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

                    CashRegisterMovement outMovement = buildMovement(
                            session.getId(),
                            "sortie",
                            amount,
                            "p2p_transfer",
                            source.getId(),
                            null,
                            actorId,
                            request.getTicketing(),
                            request.getReference()
                    );
                    CashRegisterMovement inMovement = buildMovement(
                            session.getId(),
                            "entree",
                            amount,
                            "p2p_transfer",
                            null,
                            dest.getId(),
                            actorId,
                            request.getTicketing(),
                            request.getReference()
                    );

                    Mono<AccountP2PTransferResponse> flow = accountRepository.save(source)
                            .then(accountRepository.save(dest))
                            .then(movementRepository.save(outMovement))
                            .zipWith(movementRepository.save(inMovement))
                            .map(tupleSave -> new AccountP2PTransferResponse(
                                    true,
                                    tupleSave.getT2().getId(),
                                    tupleSave.getT1().getId(),
                                    request.getReference()
                            ));

                    return transactionalOperator.transactional(flow);
                });
    }

    private Mono<AccountTransferResponse> applyMovement(
            String accountId,
            BigDecimal amount,
            String reference,
            List<?> ticketing,
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

        return Mono.zip(loadAccount(resolvedAccountId), requireOpenSession(actorId))
                .flatMap(tuple -> {
                    Account account = tuple.getT1();
                    CashRegisterSession session = tuple.getT2();
                    double currentBalance = safeBalance(account.getTotalFunds());
                    if (!isDeposit && currentBalance < amount.doubleValue()) {
                        return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Insufficient funds"));
                    }
                    double newBalance = isDeposit
                            ? currentBalance + amount.doubleValue()
                            : currentBalance - amount.doubleValue();
                    account.setTotalFunds(newBalance);

                    CashRegisterMovement movement = buildMovement(
                            session.getId(),
                            isDeposit ? "entree" : "sortie",
                            amount,
                            isDeposit ? "deposit" : "withdrawal",
                            isDeposit ? null : account.getId(),
                            isDeposit ? account.getId() : null,
                            actorId,
                            ticketing,
                            reference
                    );

                    Mono<AccountTransferResponse> flow = accountRepository.save(account)
                            .then(movementRepository.save(movement))
                            .map(savedMovement -> new AccountTransferResponse(
                                    true,
                                    newBalance,
                                    savedMovement.getId(),
                                    reference
                            ));

                    return transactionalOperator.transactional(flow);
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
            String emitterId,
            String recipientId,
            String actorId,
            List<?> ticketing,
            String reference
    ) {
        CashRegisterMovement movement = new CashRegisterMovement();
        movement.setId(UUID.randomUUID().toString());
        movement.setSessionId(sessionId);
        movement.setSense(sense);
        movement.setAmount(amount);
        movement.setReason(reason);
        movement.setEmitterId(emitterId);
        movement.setRecipientId(recipientId);
        movement.setIsAccounted(Boolean.TRUE);
        movement.setEventTicketingDetails(ticketing != null && !ticketing.isEmpty());
        movement.setExternalReference(trimToNull(reference));
        movement.setCreateOn(LocalDateTime.now());
        movement.setCreateBy(trimToNull(actorId));
        movement.setIsDeleted(false);
        return movement;
    }

    private AccountResponse toAccountResponse(
            String id,
            String accountNumber,
            Double totalFunds,
            Boolean isActive,
            LocalDateTime createOn,
            String ownerId,
            String personId,
            String userName,
            String userFirstName
    ) {
        return new AccountResponse(
                id,
                accountNumber,
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
                account.getTotalFunds(),
                account.getIsActive(),
                account.getCreateOn(),
                account.getClientId(),
                person.getId(),
                person.getUserName(),
                person.getUserFirstName()
        );
    }

    private Mono<CustomerResponse> enrichCustomer(CustomerSeed seed) {
        return fetchAccounts(seed.customerId)
                .collectList()
                .map(accounts -> {
                    double total = accounts.stream()
                            .map(AccountResponse::getTotalFunds)
                            .filter(value -> value != null)
                            .mapToDouble(Double::doubleValue)
                            .sum();
                    PersonDetailResponse person = new PersonDetailResponse(
                            seed.personId,
                            seed.userName,
                            seed.userFirstName,
                            seed.mail,
                            seed.country,
                            seed.phone,
                            seed.accountNumber
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

    private Flux<AccountResponse> fetchAccounts(String customerId) {
        String sql = "SELECT a.id, a.account_number, a.total_funds, a.is_active, a.create_on, a.client_id, "
                + "p.id AS person_id, p.user_name, p.user_first_name "
                + "FROM account a "
                + "JOIN customer_profile cp ON cp.id = a.client_id "
                + "JOIN person p ON p.id = cp.person_id "
                + "WHERE a.client_id = $1";
        DatabaseClient client = entityTemplate.getDatabaseClient();
        return client.sql(sql)
                .bind(0, customerId)
                .map((row, meta) -> toAccountResponse(
                        row.get("id", String.class),
                        row.get("account_number", String.class),
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

    private PersonDetailResponse toPersonDetail(Person person) {
        return new PersonDetailResponse(
                person.getId(),
                person.getUserName(),
                person.getUserFirstName(),
                person.getMail(),
                person.getCountry(),
                person.getPhone(),
                person.getAccountNumber()
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
        private final String accountNumber;

        private CustomerSeed(
                String customerId,
                String personId,
                String userName,
                String userFirstName,
                String mail,
                String country,
                String phone,
                String accountNumber
        ) {
            this.customerId = customerId;
            this.personId = personId;
            this.userName = userName;
            this.userFirstName = userFirstName;
            this.mail = mail;
            this.country = country;
            this.phone = phone;
            this.accountNumber = accountNumber;
        }
    }
}
