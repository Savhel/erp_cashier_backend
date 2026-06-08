package com.erp.cashier.config;

import io.r2dbc.pool.ConnectionPool;
import io.r2dbc.pool.ConnectionPoolConfiguration;
import io.r2dbc.spi.ConnectionFactories;
import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.ConnectionFactoryOptions;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.r2dbc.autoconfigure.R2dbcProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.util.StringUtils;

import static io.r2dbc.spi.ConnectionFactoryOptions.PASSWORD;
import static io.r2dbc.spi.ConnectionFactoryOptions.USER;

/**
 * Configures additional R2DBC pools for schedulers.
 *
 * @author ERP Cashier Team
 * @since 2026-02-04
 */
@Configuration
public class R2dbcPoolsConfig {
    private final R2dbcProperties primaryProperties;
    private final ExtraR2dbcProperties extraProperties;

    public R2dbcPoolsConfig(R2dbcProperties primaryProperties, ExtraR2dbcProperties extraProperties) {
        this.primaryProperties = primaryProperties;
        this.extraProperties = extraProperties;
    }

    @Bean
    @Primary
    @Qualifier("primaryConnectionFactory")
    public ConnectionFactory primaryConnectionFactory() {
        Integer maxSize = primaryProperties.getPool() != null
                ? primaryProperties.getPool().getMaxSize()
                : null;
        return buildPool(
                primaryProperties.getUrl(),
                primaryProperties.getUsername(),
                primaryProperties.getPassword(),
                maxSize != null ? maxSize : 10,
                "primary-pool"
        );
    }

    @Bean(name = "r2dbcEntityTemplate")
    @Primary
    @Qualifier("primaryEntityTemplate")
    public R2dbcEntityTemplate primaryEntityTemplate(
            @Qualifier("primaryConnectionFactory") ConnectionFactory connectionFactory
    ) {
        return new R2dbcEntityTemplate(connectionFactory);
    }

    @Bean
    @Qualifier("rtConnectionFactory")
    public ConnectionFactory rtConnectionFactory() {
        return buildPool(extraProperties.getRt(), "rt-pool");
    }

    @Bean
    @Qualifier("thirdpartyConnectionFactory")
    public ConnectionFactory thirdpartyConnectionFactory() {
        return buildPool(extraProperties.getThirdparty(), "thirdparty-pool");
    }

    @Bean
    @Qualifier("rtEntityTemplate")
    public R2dbcEntityTemplate rtEntityTemplate(
            @Qualifier("rtConnectionFactory") ConnectionFactory connectionFactory
    ) {
        return new R2dbcEntityTemplate(connectionFactory);
    }

    @Bean
    @Qualifier("thirdpartyEntityTemplate")
    public R2dbcEntityTemplate thirdpartyEntityTemplate(
            @Qualifier("thirdpartyConnectionFactory") ConnectionFactory connectionFactory
    ) {
        return new R2dbcEntityTemplate(connectionFactory);
    }

    private ConnectionFactory buildPool(ExtraR2dbcProperties.PoolConfig config, String poolName) {
        String url = resolve(config.getUrl(), primaryProperties.getUrl());
        String username = resolve(config.getUsername(), primaryProperties.getUsername());
        String password = resolve(config.getPassword(), primaryProperties.getPassword());
        Integer maxSize = config != null && config.getPool() != null
                ? config.getPool().getMaxSize()
                : null;

        return buildPool(url, username, password, maxSize, poolName);
    }

    private ConnectionFactory buildPool(
            String url,
            String username,
            String password,
            Integer maxSize,
            String poolName
    ) {
        ConnectionFactoryOptions.Builder optionsBuilder = ConnectionFactoryOptions.parse(url).mutate();
        if (StringUtils.hasText(username)) {
            optionsBuilder.option(USER, username);
        }
        if (StringUtils.hasText(password)) {
            optionsBuilder.option(PASSWORD, password);
        }

        ConnectionFactory connectionFactory = ConnectionFactories.get(optionsBuilder.build());
        ConnectionPoolConfiguration.Builder poolBuilder = ConnectionPoolConfiguration.builder(connectionFactory)
                .name(poolName)
                .maxAcquireTime(Duration.ofSeconds(5))
                .maxCreateConnectionTime(Duration.ofSeconds(5));
        if (maxSize != null && maxSize > 0) {
            poolBuilder.maxSize(maxSize);
        }
        return new ConnectionPool(poolBuilder.build());
    }

    private String resolve(String value, String fallback) {
        return StringUtils.hasText(value) ? value : fallback;
    }
}
