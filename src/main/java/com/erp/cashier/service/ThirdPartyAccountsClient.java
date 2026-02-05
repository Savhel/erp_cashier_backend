package com.erp.cashier.service;

import com.erp.cashier.config.ThirdPartyProperties;
import com.erp.cashier.dto.external.ThirdPartyAccountResponse;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Client for third-party account APIs.
 *
 * @author ERP Cashier Team
 * @since 2026-02-03
 */
@Service
public class ThirdPartyAccountsClient {
    private static final Logger LOGGER = LoggerFactory.getLogger(ThirdPartyAccountsClient.class);
    private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE =
            new ParameterizedTypeReference<>() {};
    private static final TypeReference<List<Map<String, Object>>> LIST_MAP_TYPE =
            new TypeReference<>() {};

    private final WebClient webClient;
    private final ThirdPartyProperties properties;
    private final ObjectMapper objectMapper;

    /**
     * Creates the third-party accounts client.
     *
     * @param webClientBuilder web client builder
     * @param properties third-party properties
     */
    public ThirdPartyAccountsClient(
            WebClient.Builder webClientBuilder,
            ThirdPartyProperties properties,
            ObjectMapper objectMapper
    ) {
        this.webClient = webClientBuilder.baseUrl(properties.getBaseUrl()).build();
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    /**
     * Authenticates against the third-party API.
     *
     * @param email user email
     * @param password user password
     * @return bearer token
     */
    public Mono<String> login(String email, String password) {
        if (!StringUtils.hasText(email) || !StringUtils.hasText(password)) {
            return Mono.error(new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Third-party credentials are required."
            ));
        }
        Map<String, Object> payload = Map.of(
                "email", email,
                "password", password
        );
        return webClient.post()
                .uri(properties.getAuthPath())
                .bodyValue(payload)
                .retrieve()
                .onStatus(HttpStatusCode::isError, response -> response.bodyToMono(String.class)
                        .defaultIfEmpty("")
                        .flatMap(body -> Mono.error(new ResponseStatusException(
                                response.statusCode(),
                                "Third-party login failed: " + body
                        )))
                )
                .bodyToMono(MAP_TYPE)
                .map(this::extractToken)
                .switchIfEmpty(Mono.error(new ResponseStatusException(
                        HttpStatus.BAD_GATEWAY,
                        "Third-party login returned empty response."
                )));
    }

    /**
     * Lists third-party accounts for the provided agencies.
     *
     * @param token bearer token
     * @param agencyIds agency identifiers
     * @return accounts
     */
    public Flux<ThirdPartyAccountResponse> listAccountsByAgencies(String token, List<String> agencyIds) {
        if (!StringUtils.hasText(token)) {
            return Flux.error(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Third-party token is required."));
        }
        if (agencyIds == null || agencyIds.isEmpty()) {
            return Flux.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Agency identifiers are required."));
        }
        List<String> sanitized = agencyIds.stream()
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
        if (sanitized.isEmpty()) {
            return Flux.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Agency identifiers are required."));
        }
        Map<String, Object> payload = Map.of("agencyIds", sanitized);
        return webClient.post()
                .uri(properties.getAccountsPath())
                .contentType(MediaType.APPLICATION_JSON)
                .headers(headers -> {
                    headers.setBearerAuth(token);
                })
                .bodyValue(payload)
                .retrieve()
                .onStatus(HttpStatusCode::isError, response -> response.bodyToMono(String.class)
                        .defaultIfEmpty("")
                        .flatMap(body -> Mono.error(new ResponseStatusException(
                                response.statusCode(),
                                "Third-party accounts lookup failed: " + body
                        )))
                )
                .bodyToMono(String.class)
                .defaultIfEmpty("[]")
                .doOnNext(raw -> LOGGER.info("Third-party accounts raw response:\n{}", raw))
                .flatMap(raw -> {
                    try {
                        return Mono.just(objectMapper.readValue(raw, LIST_MAP_TYPE));
                    } catch (Exception ex) {
                        return Mono.error(new ResponseStatusException(
                                HttpStatus.BAD_GATEWAY,
                                "Third-party accounts parsing failed: " + ex.getMessage(),
                                ex
                        ));
                    }
                })
                .flatMapMany(Flux::fromIterable)
                .map(this::mapAccount);
    }

    private ThirdPartyAccountResponse mapAccount(Map<String, Object> payload) {
        ThirdPartyAccountResponse response = new ThirdPartyAccountResponse();
        if (payload == null) {
            return response;
        }
        response.setId(stringValue(payload.get("id")));
        Object tenantId = payload.get("tenantId");
        if (tenantId == null) {
            tenantId = payload.get("tenant_id");
        }
        response.setTenantId(stringValue(tenantId));
        response.setCode(stringValue(payload.get("code")));
        response.setName(stringValue(payload.get("name")));
        response.setAccountingAccount(stringValue(payload.get("accountingAccount")));
        response.setBankAccountNumber(stringValue(payload.get("bankAccountNumber")));
        response.setKind(stringValue(payload.get("kind")));
        return response;
    }

    private String extractToken(Map<String, Object> payload) {
        if (payload == null || payload.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Third-party token is missing.");
        }
        Object token = payload.get("access_token");
        if (token == null) {
            token = payload.get("accessToken");
        }
        if (token == null) {
            token = payload.get("token");
        }
        String resolved = stringValue(token);
        if (!StringUtils.hasText(resolved)) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Third-party token is missing.");
        }
        return resolved;
    }

    private String stringValue(Object value) {
        return value != null ? String.valueOf(value) : null;
    }
}
