package com.erp.cashier.security;

import com.erp.cashier.dto.ErrorResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.server.ServerAuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Authentication entry point returning JSON error payloads.
 *
 * @author ERP Cashier Team
 * @since 2026-01-30
 */
@Component
public class RestAuthenticationEntryPoint implements ServerAuthenticationEntryPoint {
    private final ObjectMapper objectMapper;

    /**
     * Creates the entry point.
     *
     * @param objectMapper object mapper
     */
    public RestAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> commence(ServerWebExchange exchange, AuthenticationException ex) {
        String message = ex != null && StringUtils.hasText(ex.getMessage())
                ? ex.getMessage()
                : "Unauthorized";
        ErrorResponse payload = new ErrorResponse(message);
        byte[] bytes = toJson(payload);

        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        return exchange.getResponse().writeWith(Mono.just(exchange.getResponse()
                .bufferFactory()
                .wrap(bytes)));
    }

    private byte[] toJson(Object payload) {
        try {
            return objectMapper.writeValueAsBytes(payload);
        } catch (JsonProcessingException ex) {
            return "{\"error\":\"Unauthorized\"}".getBytes(StandardCharsets.UTF_8);
        }
    }
}
