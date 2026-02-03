package com.erp.cashier.service;

import com.erp.cashier.config.ExternalPlatformStubProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

/**
 * Stub implementation for external platform validation.
 *
 * @author ERP Cashier Team
 * @since 2025-01-28
 */
@Service
@ConditionalOnProperty(name = "app.external.enabled", havingValue = "false", matchIfMissing = true)
public class ExternalPlatformStubService implements ExternalPlatformService {
    private static final String ROLE_ADMIN = "admin";
    private static final String ROLE_CASHIER = "cashier";

    private final ExternalPlatformStubProperties properties;

    /**
     * Creates the stub service.
     *
     * @param properties stub configuration
     */
    public ExternalPlatformStubService(ExternalPlatformStubProperties properties) {
        this.properties = properties;
    }

    /**
     * Validates login access via stub configuration.
     *
     * @param context login context
     * @return completion signal
     */
    @Override
    public Mono<Void> validateLogin(ExternalLoginContext context) {
        if (context == null) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Login context is required."));
        }
        if (!properties.isEnabled()) {
            return Mono.error(new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "External platforms are not configured."
            ));
        }
        if (properties.isRequireOrganizationId() && !StringUtils.hasText(context.getOrganizationId())) {
            return Mono.error(new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Organization identifier is required."
            ));
        }
        if (!properties.getOrganization().isExists()) {
            return Mono.error(new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Organization was not found on external platform."
            ));
        }
        if (!properties.getOrganization().isOpen()) {
            return Mono.error(new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Organization is closed at this time."
            ));
        }
        if (!properties.getCredentials().isValid()) {
            return Mono.error(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid external credentials."));
        }

        validateRole(context);
        validateAgency(context);
        return Mono.empty();
    }

    private void validateRole(ExternalLoginContext context) {
        if (ROLE_CASHIER.equals(context.getRole()) && !properties.getRoles().isCashier()) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "User is not a cashier on external platform."
            );
        }
        if (ROLE_ADMIN.equals(context.getRole())) {
            String roleType = context.getRoleType();
            if ("agency_admin".equals(roleType) && !properties.getRoles().isAgencyAdmin()) {
                throw new ResponseStatusException(
                        HttpStatus.FORBIDDEN,
                        "User is not an agency administrator on external platform."
                );
            }
            if ("organization_admin".equals(roleType) && !properties.getRoles().isOrganizationAdmin()) {
                throw new ResponseStatusException(
                        HttpStatus.FORBIDDEN,
                        "User is not an organization administrator on external platform."
                );
            }
            if ("superadmin".equals(roleType) && !properties.getRoles().isSuperadmin()) {
                throw new ResponseStatusException(
                        HttpStatus.FORBIDDEN,
                        "User is not an ERP administrator on external platform."
                );
            }
        }
    }

    private void validateAgency(ExternalLoginContext context) {
        if ("organization_admin".equals(context.getRoleType())) {
            return;
        }
        if (properties.isRequireAgencyId() && !StringUtils.hasText(context.getAgencyId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Agency identifier is required.");
        }
        if (!ROLE_CASHIER.equals(context.getRole()) && !"agency_admin".equals(context.getRoleType())) {
            return;
        }
        if (!properties.getAgency().isAssigned()) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "User is not assigned to the expected agency."
            );
        }
        if (!properties.getAgency().isOpen()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Agency is closed at this time.");
        }
    }
}
