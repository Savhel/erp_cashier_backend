package com.erp.cashier.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration properties for external accounting sync.
 *
 * @author ERP Cashier Team
 * @since 2026-02-05
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.accounting")
public class AccountingProperties {
    private boolean enabled = true;
    private String baseUrl = "https://yowyob-erp-backend-2duy.onrender.com";
    private String cashMovementsPath = "/api/v1/accounting/cash-movements";
    private int timeoutSeconds = 5;
}
