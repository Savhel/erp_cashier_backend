package com.erp.cashier.service;

import com.erp.cashier.config.ThirdPartyProperties;
import com.erp.cashier.dto.external.ThirdPartyAccountResponse;
import com.erp.cashier.model.Account;
import com.erp.cashier.model.CustomerProfile;
import com.erp.cashier.model.Agency;
import com.erp.cashier.model.Organization;
import com.erp.cashier.model.Person;
import com.erp.cashier.repository.AccountRepository;
import com.erp.cashier.repository.AgencyRepository;
import com.erp.cashier.repository.CustomerProfileRepository;
import com.erp.cashier.repository.OrganizationRepository;
import com.erp.cashier.repository.PersonRepository;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Scheduled synchronization job for third-party accounts.
 *
 * @author ERP Cashier Team
 * @since 2026-02-03
 */
@Service
public class ThirdPartyAccountSyncScheduler {
    private static final Logger LOGGER = LoggerFactory.getLogger(ThirdPartyAccountSyncScheduler.class);
    private static final String KIND_CUSTOMER = "CUSTOMER";
    private static final String KIND_SUPPLIER = "SUPPLIER";
    private static final String KIND_SALES_AGENT = "SALES_AGENT";
    private static final int ORG_SYNC_CONCURRENCY = 1;
    private static final int ACCOUNT_SYNC_CONCURRENCY = 1;

    private final ThirdPartyProperties properties;
    private final ThirdPartyAccountsClient accountsClient;
    private final OrganizationRepository organizationRepository;
    private final AgencyRepository agencyRepository;
    private final PersonRepository personRepository;
    private final CustomerProfileRepository customerProfileRepository;
    private final AccountRepository accountRepository;
    private final R2dbcEntityTemplate entityTemplate;
    private final PasswordService passwordService;
    private final AuditService auditService;
    private final AtomicBoolean running = new AtomicBoolean(false);

    /**
     * Creates the scheduler.
     *
     * @param properties third-party properties
     * @param accountsClient third-party accounts client
     * @param organizationRepository organization repository
     * @param agencyRepository agency repository
     * @param personRepository person repository
     * @param customerProfileRepository customer profile repository
     * @param accountRepository account repository
     * @param entityTemplate entity template
     * @param passwordService password service
     * @param auditService audit service
     */
    public ThirdPartyAccountSyncScheduler(
            ThirdPartyProperties properties,
            ThirdPartyAccountsClient accountsClient,
            OrganizationRepository organizationRepository,
            AgencyRepository agencyRepository,
            PersonRepository personRepository,
            CustomerProfileRepository customerProfileRepository,
            AccountRepository accountRepository,
            @Qualifier("thirdpartyEntityTemplate") R2dbcEntityTemplate entityTemplate,
            PasswordService passwordService,
            AuditService auditService
    ) {
        this.properties = properties;
        this.accountsClient = accountsClient;
        this.organizationRepository = organizationRepository;
        this.agencyRepository = agencyRepository;
        this.personRepository = personRepository;
        this.customerProfileRepository = customerProfileRepository;
        this.accountRepository = accountRepository;
        this.entityTemplate = entityTemplate;
        this.passwordService = passwordService;
        this.auditService = auditService;
    }

    /**
     * Runs the third-party accounts synchronization job.
     */
    @Scheduled(
            fixedDelayString = "${app.thirdparty.sync.fixed-delay:PT1H}",
            initialDelayString = "${app.thirdparty.sync.initial-delay:PT1M}"
    )
    public void syncThirdPartyAccounts() {
        if (!properties.isEnabled() || !properties.getSync().isEnabled()) {
            return;
        }
        if (!running.compareAndSet(false, true)) {
            LOGGER.info("Third-party sync already running; skipping this cycle.");
            return;
        }
        LOGGER.info("Third-party sync started.");

        String email = StringUtils.trimWhitespace(properties.getEmail());
        String password = properties.getPassword();

        Mono<Void> syncFlow = safeAudit(
                        "thirdparty_sync_start",
                        Map.of("message", "Third-party sync started")
                )
                .then(Mono.defer(() -> {
                    if (!StringUtils.hasText(email) || !StringUtils.hasText(password)) {
                        LOGGER.warn("Third-party sync skipped: missing credentials.");
                        return safeAudit(
                                "thirdparty_sync_skipped",
                                Map.of("reason", "missing_credentials")
                        );
                    }
                    return accountsClient.login(email, password)
                            .flatMapMany(this::syncOrganizations)
                            .then();
                }))
                .materialize()
                .flatMap(signal -> {
                    Mono<Void> failureAudit = Mono.empty();
                    if (signal.isOnError()) {
                        Throwable ex = signal.getThrowable();
                        LOGGER.error("Third-party sync failed.", ex);
                        failureAudit = safeAudit(
                                "thirdparty_sync_failed",
                                Map.of("error", String.valueOf(ex != null ? ex.getMessage() : null))
                        );
                    }
                    Mono<Void> finishedAudit = safeAudit(
                            "thirdparty_sync_finished",
                            Map.of("signal", String.valueOf(signal.getType()))
                    );
                    if (signal.isOnError()) {
                        Throwable ex = signal.getThrowable();
                        return failureAudit
                                .then(finishedAudit)
                                .then(Mono.error(ex != null ? ex : new RuntimeException("Third-party sync failed")));
                    }
                    return failureAudit.then(finishedAudit);
                })
                .doFinally(signal -> {
                    running.set(false);
                    LOGGER.info("Third-party sync finished.");
                });

        syncFlow.subscribe();
    }

    private Flux<Void> syncOrganizations(String token) {
        return organizationRepository.findAll()
                .map(Organization::getId)
                .filter(StringUtils::hasText)
                .collectList()
                .flatMapMany(organizationIds -> {
                    LOGGER.info("Third-party sync found {} organizations.", organizationIds.size());
                    return safeAudit(
                                    "thirdparty_sync_orgs",
                                    Map.of("count", organizationIds.size())
                            )
                            .thenMany(Flux.fromIterable(organizationIds));
                })
                .flatMap(orgId -> syncOrganizationAccounts(token, orgId), ORG_SYNC_CONCURRENCY);
    }

    private Mono<Void> syncOrganizationAccounts(String token, String organizationId) {
        return agencyRepository.findByOrganizationIdAndIsActiveOrderByNameAsc(organizationId, Boolean.TRUE)
                .map(Agency::getId)
                .filter(StringUtils::hasText)
                .distinct()
                .collectList()
                .flatMapMany(agencyIds -> {
                    if (agencyIds.isEmpty()) {
                        LOGGER.info("Third-party accounts skipped org={} reason=no_agencies", organizationId);
                        return safeAudit(
                                        "thirdparty_sync_accounts_skipped",
                                        Map.of("organization_id", organizationId, "reason", "no_agencies")
                                )
                                .thenMany(Flux.empty());
                    }
                    LOGGER.info(
                            "Third-party accounts org={} agencies={}",
                            organizationId,
                            agencyIds.size()
                    );
                    return safeAudit(
                                    "thirdparty_sync_accounts_request",
                                    Map.of("organization_id", organizationId, "agency_count", agencyIds.size())
                            )
                            .thenMany(accountsClient.listAccountsByAgencies(token, agencyIds));
                })
                .collectList()
                .flatMapMany(accounts -> {
                    LOGGER.info(
                            "Third-party accounts org={} count={}",
                            organizationId,
                            accounts.size()
                    );
                    return safeAudit(
                                    "thirdparty_sync_accounts",
                                    Map.of("organization_id", organizationId, "count", accounts.size())
                            )
                            .thenMany(Flux.fromIterable(accounts));
                })
                .flatMap(account -> syncAccount(account, organizationId)
                        .onErrorResume(ex -> {
                            LOGGER.warn(
                                    "Third-party account sync failed for org={} code={}",
                                    organizationId,
                                    account != null ? account.getCode() : null,
                                    ex
                            );
                            Map<String, Object> payload = new java.util.HashMap<>();
                            payload.put("organization_id", organizationId);
                            payload.put("code", account != null ? account.getCode() : null);
                            payload.put("error", String.valueOf(ex.getMessage()));
                            return safeAudit("thirdparty_sync_account_failed", payload);
                        }), ACCOUNT_SYNC_CONCURRENCY)
                .then();
    }

    private Mono<Void> syncAccount(ThirdPartyAccountResponse account, String organizationId) {
        if (account == null) {
            return Mono.empty();
        }
        String kind = normalizeKind(account.getKind());
        if (KIND_CUSTOMER.equals(kind) || KIND_SUPPLIER.equals(kind)) {
            return syncCustomerAccount(account, organizationId);
        }
        if (KIND_SALES_AGENT.equals(kind)) {
            return syncSalesAgentAccount(account, organizationId);
        }
        return syncCustomerAccount(account, organizationId);
    }

    private Mono<Void> syncCustomerAccount(ThirdPartyAccountResponse account, String organizationId) {
        return syncPersonProfileAccount(account, organizationId);
    }

    private Mono<Void> syncPersonProfileAccount(ThirdPartyAccountResponse account, String organizationId) {
        return upsertCustomerPerson(account)
                .flatMap(person -> upsertCustomerProfile(account, person)
                        .flatMap(profile -> upsertAccount(account, profile, organizationId)))
                .then();
    }

    private Mono<Person> upsertCustomerPerson(ThirdPartyAccountResponse account) {
        String externalId = trimToNull(account.getTenantId());
        String code = trimToNull(account.getCode());
        String name = trimToNull(account.getName());

        if (!StringUtils.hasText(externalId)) {
            LOGGER.warn("Third-party person skipped: missing tenant-id (code={})", code);
            return Mono.empty();
        }

        Mono<Person> lookup = personRepository.findById(externalId);
        Mono<Person> createdMono = Mono.defer(() -> {
            Person person = new Person();
            person.setId(externalId);
            person.setUserName(StringUtils.hasText(code) ? code : externalId);
            person.setUserFirstName(name);
            person.setActif(Boolean.TRUE);
            person.setPassword(passwordService.hashPassword(UUID.randomUUID().toString()));
            return entityTemplate.insert(Person.class).using(person);
        });
        return lookup.switchIfEmpty(createdMono);
    }

    private Mono<CustomerProfile> upsertCustomerProfile(ThirdPartyAccountResponse account, Person person) {
        String kind = normalizeKind(account.getKind());
        String personId = person.getId();
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        Mono<CustomerProfile> createdMono = Mono.defer(() -> {
            CustomerProfile profile = new CustomerProfile();
            profile.setId(UUID.randomUUID().toString());
            profile.setPersonId(personId);
            profile.setProfession(kind);
            profile.setDateOfJoining(now);
            return entityTemplate.insert(CustomerProfile.class).using(profile);
        });
        return customerProfileRepository.findByPersonIdAndProfession(personId, kind)
                .switchIfEmpty(createdMono);
    }

    private Mono<Account> upsertAccount(
            ThirdPartyAccountResponse account,
            CustomerProfile profile,
            String organizationId
    ) {
        String accounting = trimToNull(account.getAccountingAccount());
        String bankAccount = trimToNull(account.getBankAccountNumber());
        String orgId = trimToNull(organizationId);
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        if (!StringUtils.hasText(bankAccount)) {
            LOGGER.debug("Third-party account skipped: missing bankAccountNumber (org={})", organizationId);
            return Mono.empty();
        }
        return accountRepository.findByAccountNumber(bankAccount)
                .switchIfEmpty(Mono.defer(() -> {
                    Account accountModel = new Account();
                    accountModel.setId(UUID.randomUUID().toString());
                    accountModel.setClientId(profile.getId());
                    accountModel.setAccountNumber(bankAccount);
                    accountModel.setAccountingAccount(accounting);
                    accountModel.setIsActive(Boolean.TRUE);
                    accountModel.setCreateOn(now);
                    accountModel.setTotalFunds(0.0);
                    accountModel.setOrganizationId(orgId);
                    return entityTemplate.insert(Account.class).using(accountModel);
                }));
    }

    private Mono<Void> syncSalesAgentAccount(ThirdPartyAccountResponse account, String organizationId) {
        return syncSalesAgentPersonAccount(account, organizationId)
                .onErrorResume(ex -> {
                    LOGGER.warn(
                            "Third-party sales agent account sync failed for org={} code={}",
                            organizationId,
                            account != null ? account.getCode() : null,
                            ex
                    );
                    return Mono.empty();
                })
                .then();
    }

    private Mono<Void> syncSalesAgentPersonAccount(ThirdPartyAccountResponse account, String organizationId) {
        if (account == null) {
            return Mono.empty();
        }
        return syncPersonProfileAccount(account, organizationId);
    }

    private String normalizeKind(String kind) {
        String trimmed = trimToNull(kind);
        return trimmed != null ? trimmed.toUpperCase() : null;
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private Mono<Void> safeAudit(String type, Object payload) {
        return auditService.recordEvent(type, null, payload)
                .onErrorResume(ex -> Mono.empty());
    }
}
