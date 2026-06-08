package com.erp.cashier.service;

import com.erp.cashier.config.RtComOpsProperties;
import com.erp.cashier.dto.OrganizationMembershipResponse;
import com.erp.cashier.dto.external.RtAuthResponse;
import com.erp.cashier.dto.external.RtOrganizationMembership;
import com.erp.cashier.dto.external.RtOrganizationMember;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * RT_ComOps API client.
 *
 * @author ERP Cashier Team
 * @since 2026-01-30
 */
@Service
public class RtComOpsClient {
    private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE =
            new ParameterizedTypeReference<>() {};
    private static final ParameterizedTypeReference<java.util.List<Map<String, Object>>> LIST_MAP_TYPE =
            new ParameterizedTypeReference<>() {};

    private final WebClient webClient;

    /**
     * Creates the RT_ComOps client.
     *
     * @param webClientBuilder web client builder
     * @param properties RT_ComOps properties
     */
    private final RtComOpsProperties properties;

    public RtComOpsClient(WebClient.Builder webClientBuilder, RtComOpsProperties properties) {
        this.webClient = webClientBuilder.baseUrl(properties.getBaseUrl()).build();
        this.properties = properties;
    }

    /** Résultat normalisé du login délégué à iwm. */
    public record KernelLoginResult(String token, String userId, String username) {}

    /** Contexte caisse récupéré depuis iwm (self-profile). */
    public record KernelCashierContext(String organizationId, String agencyId, String kind) {}

    /**
     * Login délégué à iwm (contrat /api/auth/login : principal + X-Tenant-Id, réponse ApiResponse.data).
     * Renvoie le JWT iwm + l'identité de base. Aucune table locale.
     */
    @SuppressWarnings("unchecked")
    public Mono<KernelLoginResult> loginViaKernel(String email, String password) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("principal", email);
        payload.put("password", password);
        return webClient.post()
                .uri("/api/auth/login")
                .header("X-Tenant-Id", properties.getTenantId())
                .bodyValue(payload)
                .retrieve()
                .onStatus(HttpStatusCode::isError, response -> response.bodyToMono(String.class).defaultIfEmpty("")
                        .flatMap(body -> Mono.error(new ResponseStatusException(response.statusCode(),
                                "iwm login failed: " + body))))
                .bodyToMono(MAP_TYPE)
                .map(body -> {
                    Map<String, Object> data = body.get("data") instanceof Map ? (Map<String, Object>) body.get("data")
                            : body;
                    String token = str(data.get("accessToken"));
                    if (!StringUtils.hasText(token)) {
                        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "iwm login returned no token");
                    }
                    return new KernelLoginResult(token, str(data.get("id")), str(data.get("username")));
                });
    }

    /** Récupère le contexte caisse de l'utilisateur courant depuis iwm (org, agence, kind). */
    @SuppressWarnings("unchecked")
    public Mono<KernelCashierContext> fetchCashierContext(String email, String token) {
        return webClient.get()
                .uri(uriBuilder -> uriBuilder.path("/api/cashiers/self-profile")
                        .queryParam("principalEmail", email).build())
                .header("X-Tenant-Id", properties.getTenantId())
                .headers(h -> h.setBearerAuth(token))
                .retrieve()
                .onStatus(HttpStatusCode::isError, response -> Mono.empty())
                .bodyToMono(MAP_TYPE)
                .map(data -> new KernelCashierContext(
                        str(data.get("organizationId")), str(data.get("agencyId")), str(data.get("kind"))))
                .defaultIfEmpty(new KernelCashierContext(null, null, null));
    }

    private static String str(Object value) {
        return value == null ? null : value.toString();
    }

    /**
     * Authenticates a user on RT_ComOps.
     *
     * @param email email address
     * @param password password
     * @return auth response
     */
    public Mono<RtAuthResponse> login(String email, String password) {
        if (!StringUtils.hasText(email) || !StringUtils.hasText(password)) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email and password are required."));
        }
        Map<String, Object> payload = new HashMap<>();
        payload.put("email", email);
        payload.put("password", password);

        return webClient.post()
                .uri("/auth/login")
                .bodyValue(payload)
                .retrieve()
                .onStatus(HttpStatusCode::isError, response -> response.bodyToMono(String.class)
                        .defaultIfEmpty("")
                        .flatMap(body -> Mono.error(new ResponseStatusException(
                                response.statusCode(),
                                "RT_ComOps login failed: " + body
                        )))
                )
                .bodyToMono(RtAuthResponse.class);
    }

    /**
     * Lists organizations for a user.
     *
     * @param userId user identifier
     * @param token bearer token
     * @return organization memberships
     */
    public Flux<OrganizationMembershipResponse> listUserOrganizations(String userId, String token) {
        if (!StringUtils.hasText(userId)) {
            return Flux.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "User identifier is required."));
        }
        if (!StringUtils.hasText(token)) {
            return Flux.error(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Access token is required."));
        }
        return webClient.get()
                .uri("/employees/{userId}/organizations", userId)
                .headers(headers -> headers.setBearerAuth(token))
                .retrieve()
                .onStatus(HttpStatusCode::isError, response -> response.bodyToMono(String.class)
                        .defaultIfEmpty("")
                        .flatMap(body -> Mono.error(new ResponseStatusException(
                                response.statusCode(),
                                "RT_ComOps organizations lookup failed: " + body
                        )))
                )
                .bodyToFlux(RtOrganizationMembership.class)
                .map(this::mapOrganizationMembership);
    }

    /**
     * Fetches the current organization.
     *
     * @param token bearer token
     * @return organization payload
     */
    public Mono<Map<String, Object>> getCurrentOrganization(String token) {
        if (!StringUtils.hasText(token)) {
            return Mono.error(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Access token is required."));
        }
        return webClient.get()
                .uri("/organizations/current")
                .headers(headers -> headers.setBearerAuth(token))
                .retrieve()
                .onStatus(HttpStatusCode::isError, response -> response.bodyToMono(String.class)
                        .defaultIfEmpty("")
                        .flatMap(body -> Mono.error(new ResponseStatusException(
                                response.statusCode(),
                                "RT_ComOps current organization lookup failed: " + body
                        )))
                )
                .bodyToMono(MAP_TYPE);
    }

    /**
     * Lists organizations with warehouses overview.
     *
     * @param token bearer token
     * @return overview payloads
     */
    public Flux<Map<String, Object>> listOrganizationsOverview(String token) {
        if (!StringUtils.hasText(token)) {
            return Flux.error(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Access token is required."));
        }
        return webClient.get()
                .uri("/organizations/with-warehouses/overview")
                .headers(headers -> headers.setBearerAuth(token))
                .retrieve()
                .onStatus(HttpStatusCode::isError, response -> response.bodyToMono(String.class)
                        .defaultIfEmpty("")
                        .flatMap(body -> Mono.error(new ResponseStatusException(
                                response.statusCode(),
                                "RT_ComOps organizations overview lookup failed: " + body
                        )))
                )
                .bodyToMono(LIST_MAP_TYPE)
                .flatMapMany(Flux::fromIterable);
    }

    /**
     * Lists organizations for the current user.
     *
     * @param token bearer token
     * @return organizations
     */
    public Flux<Map<String, Object>> listMyOrganizations(String token) {
        if (!StringUtils.hasText(token)) {
            return Flux.error(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Access token is required."));
        }
        return webClient.get()
                .uri("/organizations/my")
                .headers(headers -> headers.setBearerAuth(token))
                .retrieve()
                .onStatus(HttpStatusCode::isError, response -> response.bodyToMono(String.class)
                        .defaultIfEmpty("")
                        .flatMap(body -> Mono.error(new ResponseStatusException(
                                response.statusCode(),
                                "RT_ComOps organizations lookup failed: " + body
                        )))
                )
                .bodyToMono(LIST_MAP_TYPE)
                .flatMapMany(Flux::fromIterable);
    }

    /**
     * Fetches organization details by identifier.
     *
     * @param token bearer token
     * @param organizationId organization identifier
     * @return organization payload
     */
    public Mono<Map<String, Object>> getOrganizationById(String token, String organizationId) {
        if (!StringUtils.hasText(organizationId)) {
            return Mono.error(new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Organization identifier is required."
            ));
        }
        return listMyOrganizations(token)
                .filter(item -> organizationId.equals(String.valueOf(item.get("id"))))
                .next()
                .switchIfEmpty(Mono.error(new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Organization not found on RT_ComOps."
                )));
    }

    /**
     * Lists warehouses for an organization.
     *
     * @param token bearer token
     * @param organizationId organization identifier
     * @return warehouses
     */
    public Flux<Map<String, Object>> listWarehouses(String token, String organizationId) {
        validateOrgContext(token, organizationId);
        return webClient.get()
                .uri("/warehouses")
                .headers(headers -> {
                    headers.setBearerAuth(token);
                    headers.add("X-Tenant-ID", organizationId);
                })
                .retrieve()
                .onStatus(HttpStatusCode::isError, response -> response.bodyToMono(String.class)
                        .defaultIfEmpty("")
                        .flatMap(body -> Mono.error(new ResponseStatusException(
                                response.statusCode(),
                                "RT_ComOps warehouses lookup failed: " + body
                        )))
                )
                .bodyToMono(LIST_MAP_TYPE)
                .flatMapMany(Flux::fromIterable);
    }

    /**
     * Lists employees for an organization.
     *
     * @param token bearer token
     * @param organizationId organization identifier
     * @return organization members
     */
    public Flux<RtOrganizationMember> listEmployees(String token, String organizationId) {
        validateOrgContext(token, organizationId);
        return webClient.get()
                .uri("/employees")
                .headers(headers -> {
                    headers.setBearerAuth(token);
                    headers.add("X-Tenant-ID", organizationId);
                })
                .retrieve()
                .onStatus(HttpStatusCode::isError, response -> response.bodyToMono(String.class)
                        .defaultIfEmpty("")
                        .flatMap(body -> Mono.error(new ResponseStatusException(
                                response.statusCode(),
                                "RT_ComOps employees lookup failed: " + body
                        )))
                )
                .bodyToFlux(RtOrganizationMember.class);
    }

    /**
     * Finds an employee by email.
     *
     * @param email employee email
     * @param token bearer token
     * @param organizationId organization identifier
     * @return employee info
     */
    public Mono<Map<String, Object>> getEmployeeByEmail(String email, String token, String organizationId) {
        if (!StringUtils.hasText(email)) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email is required."));
        }
        validateOrgContext(token, organizationId);
        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/employees/by-email")
                        .queryParam("email", email)
                        .build())
                .headers(headers -> {
                    headers.setBearerAuth(token);
                    headers.add("X-Tenant-ID", organizationId);
                })
                .retrieve()
                .onStatus(HttpStatusCode::isError, response -> response.bodyToMono(String.class)
                        .defaultIfEmpty("")
                        .flatMap(body -> Mono.error(new ResponseStatusException(
                                response.statusCode(),
                                "RT_ComOps employee lookup failed: " + body
                        )))
                )
                .bodyToMono(MAP_TYPE);
    }

    /**
     * Lists available employee roles.
     *
     * @param token bearer token
     * @param organizationId organization identifier
     * @return roles
     */
    public Flux<Map<String, Object>> listEmployeeRoles(String token, String organizationId) {
        validateOrgContext(token, organizationId);
        return webClient.get()
                .uri("/employees/roles")
                .headers(headers -> {
                    headers.setBearerAuth(token);
                    headers.add("X-Tenant-ID", organizationId);
                })
                .retrieve()
                .onStatus(HttpStatusCode::isError, response -> response.bodyToMono(String.class)
                        .defaultIfEmpty("")
                        .flatMap(body -> Mono.error(new ResponseStatusException(
                                response.statusCode(),
                                "RT_ComOps roles lookup failed: " + body
                        )))
                )
                .bodyToMono(LIST_MAP_TYPE)
                .flatMapMany(Flux::fromIterable);
    }

    /**
     * Invites an employee.
     *
     * @param payload invite payload
     * @param token bearer token
     * @param organizationId organization identifier
     * @return invited employee
     */
    public Mono<Map<String, Object>> inviteEmployee(
            Map<String, Object> payload,
            String token,
            String organizationId
    ) {
        validateOrgContext(token, organizationId);
        return webClient.post()
                .uri("/employees/invite")
                .headers(headers -> {
                    headers.setBearerAuth(token);
                    headers.add("X-Tenant-ID", organizationId);
                })
                .bodyValue(payload != null ? payload : Map.of())
                .retrieve()
                .onStatus(HttpStatusCode::isError, response -> response.bodyToMono(String.class)
                        .defaultIfEmpty("")
                        .flatMap(body -> Mono.error(new ResponseStatusException(
                                response.statusCode(),
                                "RT_ComOps invite failed: " + body
                        )))
                )
                .bodyToMono(MAP_TYPE);
    }

    /**
     * Lists warehouses for the current organization.
     *
     * @param token bearer token
     * @param organizationId organization identifier
     * @param queryParams optional query parameters
     * @return warehouses
     */
    public Flux<Map<String, Object>> listWarehouses(
            String token,
            String organizationId,
            Map<String, String> queryParams
    ) {
        validateOrgContext(token, organizationId);
        WebClient.RequestHeadersUriSpec<?> request = webClient.get();
        WebClient.RequestHeadersSpec<?> spec = request.uri(uriBuilder -> {
            uriBuilder.path("/warehouses");
            Optional.ofNullable(queryParams).ifPresent(map -> map.forEach(uriBuilder::queryParam));
            return uriBuilder.build();
        });
        return spec.headers(headers -> {
            headers.setBearerAuth(token);
            headers.add("X-Tenant-ID", organizationId);
        })
                .retrieve()
                .onStatus(HttpStatusCode::isError, response -> response.bodyToMono(String.class)
                        .defaultIfEmpty("")
                        .flatMap(body -> Mono.error(new ResponseStatusException(
                                response.statusCode(),
                                "RT_ComOps warehouses lookup failed: " + body
                        )))
                )
                .bodyToMono(LIST_MAP_TYPE)
                .flatMapMany(Flux::fromIterable);
    }

    /**
     * Creates a warehouse.
     *
     * @param payload warehouse payload
     * @param token bearer token
     * @param organizationId organization identifier
     * @return created warehouse
     */
    public Mono<Map<String, Object>> createWarehouse(
            Map<String, Object> payload,
            String token,
            String organizationId
    ) {
        validateOrgContext(token, organizationId);
        return webClient.post()
                .uri("/warehouses")
                .headers(headers -> {
                    headers.setBearerAuth(token);
                    headers.add("X-Tenant-ID", organizationId);
                })
                .bodyValue(payload != null ? payload : Map.of())
                .retrieve()
                .onStatus(HttpStatusCode::isError, response -> response.bodyToMono(String.class)
                        .defaultIfEmpty("")
                        .flatMap(body -> Mono.error(new ResponseStatusException(
                                response.statusCode(),
                                "RT_ComOps warehouse creation failed: " + body
                        )))
                )
                .bodyToMono(MAP_TYPE);
    }

    /**
     * Gets a warehouse by identifier.
     *
     * @param warehouseId warehouse identifier
     * @param token bearer token
     * @param organizationId organization identifier
     * @return warehouse payload
     */
    public Mono<Map<String, Object>> getWarehouse(String warehouseId, String token, String organizationId) {
        if (!StringUtils.hasText(warehouseId)) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Warehouse identifier is required."));
        }
        validateOrgContext(token, organizationId);
        return webClient.get()
                .uri("/warehouses/{id}", warehouseId)
                .headers(headers -> {
                    headers.setBearerAuth(token);
                    headers.add("X-Tenant-ID", organizationId);
                })
                .retrieve()
                .onStatus(HttpStatusCode::isError, response -> response.bodyToMono(String.class)
                        .defaultIfEmpty("")
                        .flatMap(body -> Mono.error(new ResponseStatusException(
                                response.statusCode(),
                                "RT_ComOps warehouse lookup failed: " + body
                        )))
                )
                .bodyToMono(MAP_TYPE);
    }

    /**
     * Updates a warehouse.
     *
     * @param warehouseId warehouse identifier
     * @param payload warehouse payload
     * @param token bearer token
     * @param organizationId organization identifier
     * @return updated warehouse
     */
    public Mono<Map<String, Object>> updateWarehouse(
            String warehouseId,
            Map<String, Object> payload,
            String token,
            String organizationId
    ) {
        if (!StringUtils.hasText(warehouseId)) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Warehouse identifier is required."));
        }
        validateOrgContext(token, organizationId);
        return webClient.patch()
                .uri("/warehouses/{id}", warehouseId)
                .headers(headers -> headers.setBearerAuth(token))
                .bodyValue(payload != null ? payload : Map.of())
                .retrieve()
                .onStatus(HttpStatusCode::isError, response -> response.bodyToMono(String.class)
                        .defaultIfEmpty("")
                        .flatMap(body -> Mono.error(new ResponseStatusException(
                                response.statusCode(),
                                "RT_ComOps warehouse update failed: " + body
                        )))
                )
                .bodyToMono(MAP_TYPE);
    }

    /**
     * Deletes a warehouse.
     *
     * @param warehouseId warehouse identifier
     * @param token bearer token
     * @param organizationId organization identifier
     * @return completion signal
     */
    public Mono<Void> deleteWarehouse(String warehouseId, String token, String organizationId) {
        if (!StringUtils.hasText(warehouseId)) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Warehouse identifier is required."));
        }
        validateOrgContext(token, organizationId);
        return webClient.delete()
                .uri("/warehouses/{id}", warehouseId)
                .headers(headers -> headers.setBearerAuth(token))
                .retrieve()
                .onStatus(HttpStatusCode::isError, response -> response.bodyToMono(String.class)
                        .defaultIfEmpty("")
                        .flatMap(body -> Mono.error(new ResponseStatusException(
                                response.statusCode(),
                                "RT_ComOps warehouse deletion failed: " + body
                        )))
                )
                .bodyToMono(Void.class);
    }

    private OrganizationMembershipResponse mapOrganizationMembership(RtOrganizationMembership membership) {
        if (membership == null) {
            return null;
        }
        OrganizationMembershipResponse response = new OrganizationMembershipResponse();
        response.setOrganizationId(membership.getOrganizationId());
        response.setOrganizationName(membership.getOrganizationName());
        response.setRoleId(membership.getRoleId());
        response.setRoleName(membership.getRoleName());
        response.setAgencyId(membership.getAgencyId());
        response.setAgencyName(membership.getAgencyName());
        response.setIsActive(membership.getIsActive());
        response.setJoinedAt(membership.getJoinedAt());
        return response;
    }

    private void validateOrgContext(String token, String organizationId) {
        if (!StringUtils.hasText(token)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Access token is required.");
        }
        if (!StringUtils.hasText(organizationId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Organization context is required.");
        }
    }
}
