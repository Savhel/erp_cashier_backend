package com.erp.cashier.service;

import com.erp.cashier.config.ThirdPartyProperties;
import com.erp.cashier.dto.external.ThirdPartyAccountResponse;
import com.erp.cashier.model.Account;
import com.erp.cashier.model.CustomerProfile;
import com.erp.cashier.model.Organization;
import com.erp.cashier.model.Person;
import com.erp.cashier.repository.AccountRepository;
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
import org.springframework.r2dbc.core.DatabaseClient;
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

    private final ThirdPartyProperties properties;
    private final ThirdPartyAccountsClient accountsClient;
    private final OrganizationRepository organizationRepository;
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
            PersonRepository personRepository,
            CustomerProfileRepository customerProfileRepository,
            AccountRepository accountRepository,
            R2dbcEntityTemplate entityTemplate,
            PasswordService passwordService,
            AuditService auditService
    ) {
        this.properties = properties;
        this.accountsClient = accountsClient;
        this.organizationRepository = organizationRepository;
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
        auditService.recordEvent(
                        "thirdparty_sync_start",
                        null,
                        Map.of("message", "Third-party sync started")
                )
                .onErrorResume(ex -> Mono.empty())
                .subscribe();

        String email = StringUtils.trimWhitespace(properties.getEmail());
        String password = properties.getPassword();
        if (!StringUtils.hasText(email) || !StringUtils.hasText(password)) {
            running.set(false);
            LOGGER.warn("Third-party sync skipped: missing credentials.");
            auditService.recordEvent(
                            "thirdparty_sync_skipped",
                            null,
                            Map.of("reason", "missing_credentials")
                    )
                    .onErrorResume(ex -> Mono.empty())
                    .subscribe();
            return;
        }

        accountsClient.login(email, password)
                .flatMapMany(this::syncOrganizations)
                .doOnError(ex -> {
                    LOGGER.error("Third-party sync failed.", ex);
                    auditService.recordEvent(
                                    "thirdparty_sync_failed",
                                    null,
                                    Map.of("error", String.valueOf(ex.getMessage()))
                            )
                            .onErrorResume(inner -> Mono.empty())
                            .subscribe();
                })
                .doFinally(signal -> {
                    running.set(false);
                    LOGGER.info("Third-party sync finished.");
                    auditService.recordEvent(
                                    "thirdparty_sync_finished",
                                    null,
                                    Map.of("signal", String.valueOf(signal))
                            )
                            .onErrorResume(ex -> Mono.empty())
                            .subscribe();
                })
                .subscribe();
    }

    private Flux<Void> syncOrganizations(String token) {
        return organizationRepository.findAll()
                .map(Organization::getId)
                .filter(StringUtils::hasText)
                .collectList()
                .flatMapMany(organizationIds -> {
                    LOGGER.info("Third-party sync found {} organizations.", organizationIds.size());
                    auditService.recordEvent(
                                    "thirdparty_sync_orgs",
                                    null,
                                    Map.of("count", organizationIds.size())
                            )
                            .onErrorResume(ex -> Mono.empty())
                            .subscribe();
                    return Flux.fromIterable(organizationIds);
                })
                .concatMap(orgId -> syncOrganizationAccounts(token, orgId));
    }

    private Mono<Void> syncOrganizationAccounts(String token, String organizationId) {
        return accountsClient.listAccounts(token, organizationId)
                .collectList()
                .flatMapMany(accounts -> {
                    LOGGER.info(
                            "Third-party accounts org={} count={}",
                            organizationId,
                            accounts.size()
                    );
                    auditService.recordEvent(
                                    "thirdparty_sync_accounts",
                                    null,
                                    Map.of("organization_id", organizationId, "count", accounts.size())
                            )
                            .onErrorResume(ex -> Mono.empty())
                            .subscribe();
                    return Flux.fromIterable(accounts);
                })
                .concatMap(account -> syncAccount(account, organizationId)
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
                            auditService.recordEvent("thirdparty_sync_account_failed", null, payload)
                                    .onErrorResume(inner -> Mono.empty())
                                    .subscribe();
                            return Mono.empty();
                        }))
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
        return Mono.empty();
    }

    private Mono<Void> syncCustomerAccount(ThirdPartyAccountResponse account, String organizationId) {
        String code = trimToNull(account.getCode());
        if (!StringUtils.hasText(code)) {
            LOGGER.debug("Third-party account skipped: missing code (org={})", organizationId);
            return Mono.empty();
        }
        return upsertCustomerPerson(account)
                .flatMap(person -> upsertCustomerProfile(account, person))
                .flatMap(profile -> upsertAccount(account, profile))
                .then();
    }

    private Mono<Person> upsertCustomerPerson(ThirdPartyAccountResponse account) {
        String externalId = trimToNull(account.getId());
        String code = trimToNull(account.getCode());
        String name = trimToNull(account.getName());

        Mono<Person> lookup = Mono.empty();
        if (StringUtils.hasText(externalId)) {
            lookup = personRepository.findById(externalId);
        }
        if (StringUtils.hasText(code)) {
            Mono<Person> byCode = personRepository.findByAccountNumber(code)
                    .switchIfEmpty(personRepository.findByUserName(code));
            lookup = lookup.switchIfEmpty(byCode);
        }

        Mono<Person> updatedMono = lookup.flatMap(existing -> {
            boolean updated = false;
            if (StringUtils.hasText(code) && !code.equals(existing.getUserName())) {
                existing.setUserName(code);
                updated = true;
            }
            if (StringUtils.hasText(code) && !code.equals(existing.getAccountNumber())) {
                existing.setAccountNumber(code);
                updated = true;
            }
            if (StringUtils.hasText(name) && !name.equals(existing.getUserFirstName())) {
                existing.setUserFirstName(name);
                updated = true;
            }
            if (existing.getActif() == null || !existing.getActif()) {
                existing.setActif(Boolean.TRUE);
                updated = true;
            }
            return updated ? personRepository.save(existing) : Mono.just(existing);
        });
        Mono<Person> createdMono = Mono.defer(() -> {
            Person person = new Person();
            person.setId(StringUtils.hasText(externalId) ? externalId : UUID.randomUUID().toString());
            person.setUserName(StringUtils.hasText(code) ? code : person.getId());
            person.setAccountNumber(code);
            person.setUserFirstName(name);
            person.setActif(Boolean.TRUE);
            person.setPassword(passwordService.hashPassword(UUID.randomUUID().toString()));
            return entityTemplate.insert(Person.class).using(person);
        });
        return updatedMono.switchIfEmpty(createdMono);
    }

    private Mono<CustomerProfile> upsertCustomerProfile(ThirdPartyAccountResponse account, Person person) {
        String kind = normalizeKind(account.getKind());
        String personId = person.getId();
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        Mono<CustomerProfile> updatedMono = customerProfileRepository.findByPersonId(personId)
                .flatMap(existing -> {
                    boolean updated = false;
                    if (StringUtils.hasText(kind) && !kind.equals(existing.getProfession())) {
                        existing.setProfession(kind);
                        updated = true;
                    }
                    if (existing.getDateOfJoining() == null) {
                        existing.setDateOfJoining(now);
                        updated = true;
                    }
                    return updated ? customerProfileRepository.save(existing) : Mono.just(existing);
                });
        Mono<CustomerProfile> createdMono = Mono.defer(() -> {
            CustomerProfile profile = new CustomerProfile();
            profile.setId(UUID.randomUUID().toString());
            profile.setPersonId(personId);
            profile.setProfession(kind);
            profile.setDateOfJoining(now);
            return entityTemplate.insert(CustomerProfile.class).using(profile);
        });
        return updatedMono.switchIfEmpty(createdMono);
    }

    private Mono<Account> upsertAccount(ThirdPartyAccountResponse account, CustomerProfile profile) {
        String externalId = trimToNull(account.getId());
        String code = trimToNull(account.getCode());
        String accounting = trimToNull(account.getAccountingAccount());
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        Mono<Account> lookup = Mono.empty();
        if (StringUtils.hasText(externalId)) {
            lookup = accountRepository.findById(externalId);
        }
        if (StringUtils.hasText(code)) {
            lookup = lookup.switchIfEmpty(accountRepository.findByAccountNumber(code));
        }
        Mono<Account> updatedMono = lookup.flatMap(existing -> {
            boolean updated = false;
            if (StringUtils.hasText(code) && !code.equals(existing.getAccountNumber())) {
                existing.setAccountNumber(code);
                updated = true;
            }
            if (StringUtils.hasText(accounting)
                    && !accounting.equals(existing.getAccountingAccount())) {
                existing.setAccountingAccount(accounting);
                updated = true;
            }
            if (StringUtils.hasText(profile.getId())
                    && !profile.getId().equals(existing.getClientId())) {
                existing.setClientId(profile.getId());
                updated = true;
            }
            if (existing.getIsActive() == null) {
                existing.setIsActive(Boolean.TRUE);
                updated = true;
            }
            return updated ? accountRepository.save(existing) : Mono.just(existing);
        });
        Mono<Account> createdMono = Mono.defer(() -> {
            Account accountModel = new Account();
            accountModel.setId(StringUtils.hasText(externalId) ? externalId : UUID.randomUUID().toString());
            accountModel.setClientId(profile.getId());
            accountModel.setAccountNumber(code);
            accountModel.setAccountingAccount(accounting);
            accountModel.setIsActive(Boolean.TRUE);
            accountModel.setCreateOn(now);
            accountModel.setTotalFunds(0.0);
            return entityTemplate.insert(Account.class).using(accountModel);
        });
        return updatedMono.switchIfEmpty(createdMono);
    }

    private Mono<Void> syncSalesAgentAccount(ThirdPartyAccountResponse account, String organizationId) {
        String code = trimToNull(account.getCode());
        String externalId = trimToNull(account.getId());
        String accounting = trimToNull(account.getAccountingAccount());
        String bankAccount = trimToNull(account.getBankAccountNumber());
        if (!StringUtils.hasText(code)
                && !StringUtils.hasText(externalId)
                && !StringUtils.hasText(accounting)
                && !StringUtils.hasText(bankAccount)) {
            return Mono.empty();
        }
        String sql = "UPDATE cash_register r "
                + "SET sale_agent_bank_account = CASE "
                + "WHEN :bankAccount IS NULL OR :bankAccount = '' THEN r.sale_agent_bank_account "
                + "ELSE :bankAccount END, "
                + "sale_agent_accounting_account = CASE "
                + "WHEN :accounting IS NULL OR :accounting = '' THEN r.sale_agent_accounting_account "
                + "ELSE :accounting END "
                + "FROM agency a "
                + "WHERE r.agency_id = a.id "
                + "AND a.organization_id = :organizationId "
                + "AND ("
                + "(:externalId IS NOT NULL AND r.id = :externalId) "
                + "OR (:code IS NOT NULL AND r.cashier = :code) "
                + "OR (:accounting IS NOT NULL AND r.sale_agent_accounting_account = :accounting) "
                + "OR (:bankAccount IS NOT NULL AND r.sale_agent_bank_account = :bankAccount)"
                + ")";
        DatabaseClient.GenericExecuteSpec spec = entityTemplate.getDatabaseClient()
                .sql(sql)
                .bind("organizationId", organizationId);
        spec = bindNullable(spec, "externalId", externalId);
        spec = bindNullable(spec, "code", code);
        spec = bindNullable(spec, "accounting", accounting);
        spec = bindNullable(spec, "bankAccount", bankAccount);
        return spec.fetch()
                .rowsUpdated()
                .doOnNext(count -> {
                    if (count > 0) {
                        LOGGER.info(
                                "Updated {} cash registers for sales agent {} in org {}",
                                count,
                                code,
                                organizationId
                        );
                    }
                })
                .then();
    }

    private DatabaseClient.GenericExecuteSpec bindNullable(
            DatabaseClient.GenericExecuteSpec spec,
            String name,
            String value
    ) {
        if (value == null) {
            return spec.bindNull(name, String.class);
        }
        return spec.bind(name, value);
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
}
