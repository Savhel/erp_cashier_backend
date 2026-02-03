package com.erp.cashier.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration properties for third-party accounts integration.
 *
 * @author ERP Cashier Team
 * @since 2026-02-03
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.thirdparty")
public class ThirdPartyProperties {
    private boolean enabled = true;
    private String baseUrl = "http://localhost:8082";
    private String authPath = "/auth/login";
    private String accountsPath = "/api/v1/thirdparty-accounts";
    private String email = "";
    private String password = "";
    private Sync sync = new Sync();

    /**
     * Scheduling configuration for third-party sync.
     */
    @Data
    public static class Sync {
        private boolean enabled = true;
        private String fixedDelay = "PT1H";
        private String initialDelay = "PT1M";
    }
}
