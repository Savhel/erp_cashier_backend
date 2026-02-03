package com.erp.cashier.security;

import com.erp.cashier.service.AuditService;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * Web filter that logs API activity into the audit table.
 *
 * @author ERP Cashier Team
 * @since 2026-02-03
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class AuditLoggingWebFilter implements WebFilter {
    private static final Logger LOGGER = LoggerFactory.getLogger(AuditLoggingWebFilter.class);
    private static final Set<String> EXCLUDED_PATH_PREFIXES = Set.of(
            "/api/audit",
            "/api/notify-unauthorized"
    );

    private final AuditService auditService;

    /**
     * Creates the audit logging filter.
     *
     * @param auditService audit service
     */
    public AuditLoggingWebFilter(AuditService auditService) {
        this.auditService = auditService;
    }

    /**
     * Logs each API request and response for audit tracking.
     *
     * @param exchange server exchange
     * @param chain web filter chain
     * @return completion signal
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        if (!path.startsWith("/api/") || shouldSkip(path)) {
            return chain.filter(exchange);
        }
        Instant start = Instant.now();
        AtomicReference<Throwable> errorRef = new AtomicReference<>();

        return chain.filter(exchange)
                .doOnError(errorRef::set)
                .doFinally(signalType -> logExchange(exchange, path, start, errorRef.get()));
    }

    private boolean shouldSkip(String path) {
        for (String prefix : EXCLUDED_PATH_PREFIXES) {
            if (path.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private void logExchange(ServerWebExchange exchange, String path, Instant start, Throwable error) {
        HttpMethod method = exchange.getRequest().getMethod();
        HttpStatus status = resolveStatus(exchange.getResponse().getStatusCode(), error);
        long durationMs = Duration.between(start, Instant.now()).toMillis();

        exchange.getPrincipal()
                .ofType(Authentication.class)
                .defaultIfEmpty(new UsernamePasswordAuthenticationToken(null, null))
                .flatMap(authentication -> {
                    JwtPayload payload = resolvePayload(authentication);
                    String authorId = payload != null ? payload.getUserId() : null;
                    Map<String, Object> auditPayload = new LinkedHashMap<>();
                    auditPayload.put("path", path);
                    auditPayload.put("method", method != null ? method.name() : null);
                    auditPayload.put("ip", resolveClientIp(exchange.getRequest()));
                    auditPayload.put("status", status != null ? status.value() : null);
                    auditPayload.put("duration_ms", durationMs);
                    auditPayload.put("query", exchange.getRequest().getQueryParams().toSingleValueMap());
                    String userAgent = exchange.getRequest().getHeaders().getFirst("User-Agent");
                    if (StringUtils.hasText(userAgent)) {
                        auditPayload.put("user_agent", userAgent);
                    }
                    if (payload != null) {
                        putIfPresent(auditPayload, "organization_id", payload.getOrganizationId());
                        putIfPresent(auditPayload, "agency_id", payload.getAgencyId());
                    }
                    if (error != null) {
                        auditPayload.put("error", error.getMessage());
                    }
                    String type = resolveType(path, method, status);
                    return auditService.recordEvent(type, authorId, auditPayload);
                })
                .onErrorResume(ex -> {
                    LOGGER.debug("Failed to record audit event for {}", path, ex);
                    return Mono.empty();
                })
                .subscribe();
    }

    private JwtPayload resolvePayload(Authentication authentication) {
        if (authentication == null) {
            return null;
        }
        Object details = authentication.getDetails();
        if (details instanceof JwtPayload payload) {
            return payload;
        }
        return null;
    }

    private HttpStatus resolveStatus(HttpStatusCode status, Throwable error) {
        if (status != null) {
            return HttpStatus.resolve(status.value());
        }
        if (error instanceof ResponseStatusException responseStatusException) {
            return HttpStatus.resolve(responseStatusException.getStatusCode().value());
        }
        return error != null ? HttpStatus.INTERNAL_SERVER_ERROR : null;
    }

    private String resolveType(String path, HttpMethod method, HttpStatus status) {
        if (path.startsWith("/api/auth/login")) {
            if (status != null && status.is2xxSuccessful()) {
                return "login_success";
            }
            if (status != null && status.is4xxClientError()) {
                return "login_fail";
            }
            return "login_attempt";
        }
        if (path.contains("/sessions/") && path.endsWith("/lock")) {
            if (HttpMethod.POST.equals(method)) {
                return "session_lock";
            }
            if (HttpMethod.DELETE.equals(method)) {
                return "session_unlock";
            }
        }
        if (status != null && (status.value() == 401 || status.value() == 403)) {
            return "unauthorized";
        }
        return "api_call";
    }

    private String resolveClientIp(ServerHttpRequest request) {
        String forwarded = request.getHeaders().getFirst("X-Forwarded-For");
        if (StringUtils.hasText(forwarded)) {
            return forwarded.split(",")[0].trim();
        }
        String realIp = request.getHeaders().getFirst("X-Real-IP");
        if (StringUtils.hasText(realIp)) {
            return realIp.trim();
        }
        InetSocketAddress remoteAddress = request.getRemoteAddress();
        if (remoteAddress != null && remoteAddress.getAddress() != null) {
            return remoteAddress.getAddress().getHostAddress();
        }
        return null;
    }

    private void putIfPresent(Map<String, Object> payload, String key, String value) {
        if (StringUtils.hasText(value)) {
            payload.put(key, value);
        }
    }
}
