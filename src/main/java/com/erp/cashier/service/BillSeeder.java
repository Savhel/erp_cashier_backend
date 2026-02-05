package com.erp.cashier.service;

import com.erp.cashier.config.R2dbcMigrationRunner;
import com.erp.cashier.model.Organization;
import com.erp.cashier.repository.OrganizationRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Seeds demo bills when enabled.
 *
 * @author ERP Cashier Team
 * @since 2026-02-05
 */
@Component
public class BillSeeder implements ApplicationListener<ApplicationReadyEvent> {
    private static final Logger LOGGER = LoggerFactory.getLogger(BillSeeder.class);

    private final R2dbcEntityTemplate entityTemplate;
    private final OrganizationRepository organizationRepository;
    private final R2dbcMigrationRunner migrationRunner;
    private final boolean enabled;

    public BillSeeder(
            R2dbcEntityTemplate entityTemplate,
            OrganizationRepository organizationRepository,
            R2dbcMigrationRunner migrationRunner,
            @Value("${app.seed.bills.enabled:false}") boolean enabled
    ) {
        this.entityTemplate = entityTemplate;
        this.organizationRepository = organizationRepository;
        this.migrationRunner = migrationRunner;
        this.enabled = enabled;
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        if (!enabled) {
            return;
        }
        migrationRunner.migrate()
                .then(seedBills())
                .doOnError(ex -> LOGGER.error("Failed to seed bills.", ex))
                .subscribe();
    }

    private Mono<Void> seedBills() {
        return countExistingBills()
                .flatMap(total -> {
                    if (total != null && total > 0) {
                        return Mono.empty();
                    }
                    return organizationRepository.findAll()
                            .flatMap(this::seedForOrganization)
                            .then();
                });
    }

    private Mono<Long> countExistingBills() {
        return entityTemplate.getDatabaseClient()
                .sql("SELECT COUNT(*) AS total FROM bill")
                .map((row, meta) -> row.get("total", Long.class))
                .one()
                .defaultIfEmpty(0L);
    }

    private Mono<Void> seedForOrganization(Organization organization) {
        String organizationId = organization.getId();
        if (!StringUtils.hasText(organizationId)) {
            return Mono.empty();
        }
        return loadAccountSeed(organizationId)
                .flatMapMany(seed -> buildBillSeeds(organization, seed))
                .concatMap(this::insertBillSeed)
                .then();
    }

    private Mono<AccountSeed> loadAccountSeed(String organizationId) {
        return entityTemplate.getDatabaseClient()
                .sql("""
                        SELECT acc.id AS account_id, acc.account_number, p.user_first_name, p.user_name, p.phone
                        FROM account acc
                        LEFT JOIN customer_profile cp ON cp.id = acc.client_id
                        LEFT JOIN person p ON p.id = cp.person_id
                        WHERE acc.organization_id = :organizationId
                        ORDER BY acc.create_on ASC
                        LIMIT 1
                        """)
                .bind("organizationId", organizationId)
                .map((row, meta) -> new AccountSeed(
                        row.get("account_id", String.class),
                        row.get("account_number", String.class),
                        resolveName(
                                row.get("user_first_name", String.class),
                                row.get("user_name", String.class)
                        ),
                        row.get("phone", String.class)
                ))
                .one()
                .defaultIfEmpty(new AccountSeed(null, null, null, null));
    }

    private Flux<BillSeed> buildBillSeeds(Organization organization, AccountSeed accountSeed) {
        List<BillSeed> seeds = new ArrayList<>();
        String orgId = organization.getId();
        String orgName = organization.getName();
        LocalDateTime now = LocalDateTime.now();

        boolean hasAccount = StringUtils.hasText(accountSeed.accountId);
        if (hasAccount) {
            BillSeed accountBill = new BillSeed(
                    UUID.randomUUID().toString(),
                    orgId,
                    nextInvoiceCode(),
                    new BigDecimal("12000"),
                    resolveName(accountSeed.customerName, orgName),
                    now.plusDays(7),
                    "account",
                    accountSeed.accountId,
                    List.of(
                            new BillItemSeed("Consulting fee", 1, new BigDecimal("7000")),
                            new BillItemSeed("Service charge", 1, new BigDecimal("5000"))
                    )
            );
            seeds.add(accountBill);
        }

        BillSeed cashBill = new BillSeed(
                UUID.randomUUID().toString(),
                orgId,
                nextInvoiceCode(),
                new BigDecimal("8000"),
                resolveName(orgName, "Walk-in customer"),
                now.plusDays(3),
                "cash",
                null,
                List.of(
                        new BillItemSeed("Invoice item A", 2, new BigDecimal("3000")),
                        new BillItemSeed("Invoice item B", 1, new BigDecimal("2000"))
                )
        );
        seeds.add(cashBill);

        return Flux.fromIterable(seeds);
    }

    private Mono<Void> insertBillSeed(BillSeed seed) {
        DatabaseClient.GenericExecuteSpec spec = entityTemplate.getDatabaseClient()
                .sql("""
                        INSERT INTO bill (id, organization_id, invoice_code, amount, customer_name, due_date,
                                          payment_mode, account_id, create_on, is_deleted)
                        VALUES (:id, :organizationId, :invoiceCode, :amount, :customerName, :dueDate,
                                :paymentMode, :accountId, :createOn, :isDeleted)
                        """)
                .bind("id", seed.id)
                .bind("organizationId", seed.organizationId)
                .bind("invoiceCode", seed.invoiceCode)
                .bind("amount", seed.amount)
                .bind("paymentMode", seed.paymentMode)
                .bind("createOn", LocalDateTime.now())
                .bind("isDeleted", false);

        if (seed.customerName != null) {
            spec = spec.bind("customerName", seed.customerName);
        } else {
            spec = spec.bindNull("customerName", String.class);
        }

        if (seed.dueDate != null) {
            spec = spec.bind("dueDate", seed.dueDate);
        } else {
            spec = spec.bindNull("dueDate", LocalDateTime.class);
        }

        if (seed.accountId != null) {
            spec = spec.bind("accountId", seed.accountId);
        } else {
            spec = spec.bindNull("accountId", String.class);
        }

        return spec.then()
                .thenMany(Flux.fromIterable(seed.items))
                .concatMap(item -> insertBillItem(seed.id, item))
                .then();
    }

    private Mono<Void> insertBillItem(String billId, BillItemSeed item) {
        return entityTemplate.getDatabaseClient()
                .sql("""
                        INSERT INTO bill_item (id, bill_id, description, quantity, amount)
                        VALUES (:id, :billId, :description, :quantity, :amount)
                        """)
                .bind("id", UUID.randomUUID().toString())
                .bind("billId", billId)
                .bind("description", item.description)
                .bind("quantity", item.quantity)
                .bind("amount", item.amount)
                .then();
    }

    private String nextInvoiceCode() {
        return "INV-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    private String resolveName(String primary, String fallback) {
        String name = primary != null ? primary.trim() : "";
        if (StringUtils.hasText(name)) {
            return name;
        }
        return fallback;
    }

    private static final class AccountSeed {
        private final String accountId;
        private final String accountNumber;
        private final String customerName;
        private final String customerPhone;

        private AccountSeed(String accountId, String accountNumber, String customerName, String customerPhone) {
            this.accountId = accountId;
            this.accountNumber = accountNumber;
            this.customerName = customerName;
            this.customerPhone = customerPhone;
        }
    }

    private static final class BillSeed {
        private final String id;
        private final String organizationId;
        private final String invoiceCode;
        private final BigDecimal amount;
        private final String customerName;
        private final LocalDateTime dueDate;
        private final String paymentMode;
        private final String accountId;
        private final List<BillItemSeed> items;

        private BillSeed(
                String id,
                String organizationId,
                String invoiceCode,
                BigDecimal amount,
                String customerName,
                LocalDateTime dueDate,
                String paymentMode,
                String accountId,
                List<BillItemSeed> items
        ) {
            this.id = id;
            this.organizationId = organizationId;
            this.invoiceCode = invoiceCode;
            this.amount = amount;
            this.customerName = customerName;
            this.dueDate = dueDate;
            this.paymentMode = paymentMode;
            this.accountId = accountId;
            this.items = items;
        }
    }

    private static final class BillItemSeed {
        private final String description;
        private final int quantity;
        private final BigDecimal amount;

        private BillItemSeed(String description, int quantity, BigDecimal amount) {
            this.description = description;
            this.quantity = quantity;
            this.amount = amount;
        }
    }
}
