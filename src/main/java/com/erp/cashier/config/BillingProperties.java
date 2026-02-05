package com.erp.cashier.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration properties for external billing platform.
 *
 * @author ERP Cashier Team
 * @since 2026-02-05
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.billing")
public class BillingProperties {
    private boolean enabled = true;
    private String baseUrl = "https://billing-f6l8.onrender.com";
    private String allBillsPath = "/all-bill";
    private int timeoutSeconds = 5;
}
