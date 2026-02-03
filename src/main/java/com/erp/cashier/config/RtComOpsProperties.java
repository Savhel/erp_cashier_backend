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
}
