package com.erp.cashier.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Extra R2DBC pools configuration.
 *
 * @author ERP Cashier Team
 * @since 2026-02-04
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.r2dbc")
public class ExtraR2dbcProperties {
    private PoolConfig rt = new PoolConfig();
    private PoolConfig thirdparty = new PoolConfig();

    @Data
    public static class PoolConfig {
        private String url;
        private String username;
        private String password;
        private Pool pool = new Pool();
    }

    @Data
    public static class Pool {
        private Integer maxSize = 10;
    }
}
