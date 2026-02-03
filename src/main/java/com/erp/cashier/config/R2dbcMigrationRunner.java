package com.erp.cashier.config;

import io.r2dbc.spi.ConnectionFactory;
import name.nkonev.r2dbc.migrate.core.Dialect;
import name.nkonev.r2dbc.migrate.core.R2dbcMigrate;
import name.nkonev.r2dbc.migrate.core.R2dbcMigrateProperties;
import name.nkonev.r2dbc.migrate.reader.SpringResourceReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternUtils;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Runs database migrations using a reactive pipeline at application startup.
 *
 * @author ERP Cashier Team
 * @since 2025-01-20
 */
@Component
public class R2dbcMigrationRunner implements ApplicationListener<ApplicationReadyEvent> {
    private static final Logger LOGGER = LoggerFactory.getLogger(R2dbcMigrationRunner.class);

    private final Mono<Void> migration;

    /**
     * Creates the migration runner.
     *
     * @param connectionFactory reactive connection factory
     * @param resourceLoader resource loader for migration scripts
     */
    public R2dbcMigrationRunner(ConnectionFactory connectionFactory, ResourceLoader resourceLoader) {
        R2dbcMigrateProperties properties = new R2dbcMigrateProperties();
        properties.setResourcesPath("classpath:/db/migration/*.sql");
        properties.setDialect(Dialect.POSTGRESQL);
        ResourcePatternResolver resolver = ResourcePatternUtils.getResourcePatternResolver(resourceLoader);
        SpringResourceReader resourceReader = new SpringResourceReader(resolver);
        this.migration = Mono.<Void>defer(() -> R2dbcMigrate.migrate(
                        connectionFactory,
                        properties,
                        resourceReader,
                        null,
                        null
                ))
                .doOnSubscribe(subscription -> LOGGER.info("Starting reactive database migrations."))
                .doOnSuccess(ignored -> LOGGER.info("Reactive database migrations completed."))
                .doOnError(ex -> LOGGER.error("Reactive database migrations failed.", ex))
                .cache();
    }

    /**
     * Returns the migration flow for reuse by other startup tasks.
     *
     * @return migration flow
     */
    public Mono<Void> migrate() {
        return migration;
    }

    /**
     * Triggers the migration flow on application ready.
     *
     * @param event application ready event
     */
    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        migrate().subscribe();
    }
}
