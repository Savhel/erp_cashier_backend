package com.erp.cashier.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration properties for external platform integration.
 *
 * @author ERP Cashier Team
 * @since 2025-01-28
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.external")
public class ExternalPlatformProperties {
    private boolean enabled = false;
    private boolean requireOrganizationId = false;
    private boolean requireAgencyId = false;
    private Organization organization = new Organization();
    private Credentials credentials = new Credentials();
    private Agency agency = new Agency();

    /**
     * Organization platform configuration.
     */
    @Data
    public static class Organization {
        private String baseUrl;
        private String existsPath = "/organizations/{organizationId}/exists";
        private String openPath = "/organizations/{organizationId}/open";
        private String existsField = "exists";
        private String openField = "open";
    }

    /**
     * Credential platform configuration.
     */
    @Data
    public static class Credentials {
        private String baseUrl;
        private String validatePath = "/auth/validate";
        private String validField = "valid";
        private String roleField = "role";
        private String roleTypeField = "role_type";
    }

    /**
     * Agency platform configuration.
     */
    @Data
    public static class Agency {
        private String baseUrl;
        private String assignedPath = "/agencies/{agencyId}/assignments/{userId}";
        private String openPath = "/agencies/{agencyId}/open";
        private String assignedField = "assigned";
        private String openField = "open";
    }
}
