package com.erp.cashier.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration properties for RT_ComOps integration.
 *
 * @author ERP Cashier Team
 * @since 2026-01-30
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.rt")
public class RtComOpsProperties {
    private String baseUrl = "http://localhost:8080";
    /** Tenant par défaut injecté dans X-Tenant-Id lors du login délégué à iwm. */
    private String tenantId = "11111111-1111-1111-1111-111111111111";
}
