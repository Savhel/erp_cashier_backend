package com.erp.cashier.config;

import java.time.Duration;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration properties for RT_ComOps synchronization.
 *
 * @author ERP Cashier Team
 * @since 2026-02-01
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.sync.rt")
public class RtSyncProperties {
    private boolean enabled = true;
    private Duration fixedDelay = Duration.ofHours(1);
    private Duration initialDelay = Duration.ofMinutes(1);
    private boolean syncOnLogin = false;
    private String superadminEmail = "superadmin@gmail.com";
    private String superadminPassword = "qwerty";
}
