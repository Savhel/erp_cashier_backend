package com.erp.cashier.service;

import com.erp.cashier.config.ExternalPlatformProperties;
import java.util.HashMap;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

/**
 * WebClient-based external platform validation service.
 *
 * @author ERP Cashier Team
 * @since 2025-01-28
 */
@Service
@ConditionalOnProperty(name = "app.external.enabled", havingValue = "true")
public class ExternalPlatformWebClientService implements ExternalPlatformService {
    private static final String ROLE_ADMIN = "admin";
    private static final String ROLE_CASHIER = "cashier";
    private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE =
            new ParameterizedTypeReference<>() {};

    private final WebClient.Builder webClientBuilder;
    private final ExternalPlatformProperties properties;

    /**
     * Creates the WebClient integration service.
     *
     * @param webClientBuilder web client builder
     * @param properties external platform properties
     */
    public ExternalPlatformWebClientService(
            WebClient.Builder webClientBuilder,
            ExternalPlatformProperties properties
    ) {
        this.webClientBuilder = webClientBuilder;
        this.properties = properties;
    }

    /**
     * Validates login access through external platforms.
     *
     * @param context login context
     * @return completion signal
     */
    @Override
    public Mono<Void> validateLogin(ExternalLoginContext context) {
        if (context == null) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Login context is required."));
        }
        if (properties.isRequireOrganizationId() && !StringUtils.hasText(context.getOrganizationId())) {
            return Mono.error(new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Organization identifier is required."
            ));
        }
        if (properties.isRequireAgencyId() && !StringUtils.hasText(context.getAgencyId())
                && !"organization_admin".equals(context.getRoleType())) {
            return Mono.error(new ResponseStatusException(HttpStatus.FORBIDDEN, "Agency identifier is required."));
        }

        return validateOrganization(context)
                .then(validateCredentials(context))
                .then(validateAgency(context));
    }

    private Mono<Void> validateOrganization(ExternalLoginContext context) {
        ExternalPlatformProperties.Organization organization = properties.getOrganization();
        if (!StringUtils.hasText(organization.getBaseUrl())
                || !StringUtils.hasText(organization.getExistsPath())
                || !StringUtils.hasText(organization.getOpenPath())) {
            return Mono.error(new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Organization platform endpoints are not configured."
            ));
        }
        Map<String, Object> uriVars = Map.of("organizationId", context.getOrganizationId());
        return fetchBoolean(
                        organization.getBaseUrl(),
                        organization.getExistsPath(),
                        uriVars,
                        organization.getExistsField(),
                        "organization exists check"
                )
                .flatMap(exists -> {
                    if (!exists) {
                        return Mono.error(new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Organization was not found on external platform."
                        ));
                    }
                    return Mono.empty();
                })
                .then(fetchBoolean(
                        organization.getBaseUrl(),
                        organization.getOpenPath(),
                        uriVars,
                        organization.getOpenField(),
                        "organization open check"
                ))
                .flatMap(open -> {
                    if (!open) {
                        return Mono.error(new ResponseStatusException(
                                HttpStatus.FORBIDDEN,
                                "Organization is closed at this time."
                        ));
                    }
                    return Mono.empty();
                });
    }

    private Mono<Void> validateCredentials(ExternalLoginContext context) {
        ExternalPlatformProperties.Credentials credentials = properties.getCredentials();
        if (!StringUtils.hasText(credentials.getBaseUrl()) || !StringUtils.hasText(credentials.getValidatePath())) {
            return Mono.error(new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Credentials platform endpoints are not configured."
            ));
        }
        Map<String, Object> payload = new HashMap<>();
        payload.put("username", context.getUsername());
        payload.put("password", context.getPassword());
        if (StringUtils.hasText(context.getOrganizationId())) {
            payload.put("organization_id", context.getOrganizationId());
        }
        if (StringUtils.hasText(context.getAgencyId())) {
            payload.put("agency_id", context.getAgencyId());
        }

        return postForMap(credentials.getBaseUrl(), credentials.getValidatePath(), payload)
                .flatMap(response -> {
                    boolean valid = resolveBoolean(response, credentials.getValidField(), "credentials validation");
                    if (!valid) {
                        return Mono.error(new ResponseStatusException(
                                HttpStatus.UNAUTHORIZED,
                                "Invalid external credentials."
                        ));
                    }
                    String role = resolveString(response, credentials.getRoleField());
                    String roleType = resolveString(response, credentials.getRoleTypeField());
                    validateRole(context, role, roleType);
                    return Mono.empty();
                });
    }

    private Mono<Void> validateAgency(ExternalLoginContext context) {
        if ("organization_admin".equals(context.getRoleType())) {
            return Mono.empty();
        }
        ExternalPlatformProperties.Agency agency = properties.getAgency();
        if (!StringUtils.hasText(agency.getBaseUrl())
                || !StringUtils.hasText(agency.getAssignedPath())
                || !StringUtils.hasText(agency.getOpenPath())) {
            return Mono.error(new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Agency platform endpoints are not configured."
            ));
        }
        Map<String, Object> uriVars = Map.of(
                "agencyId", context.getAgencyId(),
                "userId", context.getUserId()
        );

        return fetchBoolean(
                        agency.getBaseUrl(),
                        agency.getAssignedPath(),
                        uriVars,
                        agency.getAssignedField(),
                        "agency assignment check"
                )
                .flatMap(assigned -> {
                    if (!assigned) {
                        return Mono.error(new ResponseStatusException(
                                HttpStatus.FORBIDDEN,
                                "User is not assigned to the expected agency."
                        ));
                    }
                    return Mono.empty();
                })
                .then(fetchBoolean(
                        agency.getBaseUrl(),
                        agency.getOpenPath(),
                        uriVars,
                        agency.getOpenField(),
                        "agency open check"
                ))
                .flatMap(open -> {
                    if (!open) {
                        return Mono.error(new ResponseStatusException(
                                HttpStatus.FORBIDDEN,
                                "Agency is closed at this time."
                        ));
                    }
                    return Mono.empty();
                });
    }

    private void validateRole(ExternalLoginContext context, String externalRole, String externalRoleType) {
        if (!StringUtils.hasText(externalRole)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "External role information is missing."
            );
        }
        if (!externalRole.equals(context.getRole())) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "External role does not match expected role."
            );
        }
        if (ROLE_ADMIN.equals(context.getRole())) {
            if (!StringUtils.hasText(externalRoleType)) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_GATEWAY,
                        "External role type information is missing."
                );
            }
            if (!externalRoleType.equals(context.getRoleType())) {
                throw new ResponseStatusException(
                        HttpStatus.FORBIDDEN,
                        "External role type does not match expected role type."
                );
            }
        }
        if (ROLE_CASHIER.equals(context.getRole()) && StringUtils.hasText(externalRoleType)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "External role type is not allowed for cashier."
            );
        }
    }

    private Mono<Boolean> fetchBoolean(
            String baseUrl,
            String path,
            Map<String, Object> uriVariables,
            String field,
            String label
    ) {
        return getForMap(baseUrl, path, uriVariables)
                .map(response -> resolveBoolean(response, field, label));
    }

    private Mono<Map<String, Object>> getForMap(String baseUrl, String path, Map<String, Object> uriVariables) {
        return webClient(baseUrl)
                .get()
                .uri(path, uriVariables)
                .retrieve()
                .onStatus(HttpStatusCode::isError, response -> response.bodyToMono(String.class)
                        .defaultIfEmpty("")
                        .flatMap(body -> Mono.error(new ResponseStatusException(
                                HttpStatus.BAD_GATEWAY,
                                "External platform error: " + body
                        )))
                )
                .bodyToMono(MAP_TYPE);
    }

    private Mono<Map<String, Object>> postForMap(String baseUrl, String path, Object body) {
        return webClient(baseUrl)
                .post()
                .uri(path)
                .bodyValue(body)
                .retrieve()
                .onStatus(HttpStatusCode::isError, response -> response.bodyToMono(String.class)
                        .defaultIfEmpty("")
                        .flatMap(content -> Mono.error(new ResponseStatusException(
                                HttpStatus.BAD_GATEWAY,
                                "External platform error: " + content
                        )))
                )
                .bodyToMono(MAP_TYPE);
    }

    private WebClient webClient(String baseUrl) {
        return webClientBuilder.baseUrl(baseUrl).build();
    }

    private boolean resolveBoolean(Map<String, Object> response, String field, String label) {
        if (response == null || !StringUtils.hasText(field)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "External response missing data for " + label + "."
            );
        }
        Object value = response.get(field);
        if (value instanceof Boolean boolValue) {
            return boolValue;
        }
        if (value instanceof String stringValue) {
            return Boolean.parseBoolean(stringValue);
        }
        if (value instanceof Number numberValue) {
            return numberValue.intValue() != 0;
        }
        throw new ResponseStatusException(
                HttpStatus.BAD_GATEWAY,
                "External response has invalid data for " + label + "."
        );
    }

    private String resolveString(Map<String, Object> response, String field) {
        if (response == null || !StringUtils.hasText(field)) {
            return null;
        }
        Object value = response.get(field);
        if (value == null) {
            return null;
        }
        return value.toString();
    }
}
