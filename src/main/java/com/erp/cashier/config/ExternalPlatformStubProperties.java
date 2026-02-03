package com.erp.cashier.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration properties for the external platform stub.
 *
 * @author ERP Cashier Team
 * @since 2025-01-28
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.external-stub")
public class ExternalPlatformStubProperties {
    private boolean enabled = true;
    private boolean requireOrganizationId = false;
    private boolean requireAgencyId = false;
    private Organization organization = new Organization();
    private Credentials credentials = new Credentials();
    private Agency agency = new Agency();
    private Roles roles = new Roles();

    /**
     * Organization stub configuration.
     */
    @Data
    public static class Organization {
        private boolean exists = true;
        private boolean open = true;
    }

    /**
     * Credential validation stub configuration.
     */
    @Data
    public static class Credentials {
        private boolean valid = true;
    }

    /**
     * Agency stub configuration.
     */
    @Data
    public static class Agency {
        private boolean assigned = true;
        private boolean open = true;
    }

    /**
     * Role validation stub configuration.
     */
    @Data
    public static class Roles {
        private boolean cashier = true;
        private boolean agencyAdmin = true;
        private boolean organizationAdmin = true;
        private boolean superadmin = true;
    }
}
