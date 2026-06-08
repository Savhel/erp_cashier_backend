package com.erp.cashier.facade;

import com.erp.cashier.config.CashierCoreProperties;
import com.erp.cashier.security.JwtPayload;
import com.erp.cashier.security.JwtService;
import com.erp.cashier.security.KernelTokenRelayStore;
import java.net.URI;
import java.util.List;
import java.util.Set;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class CashierCoreProxyWebFilter implements WebFilter {
    private static final String BEARER_PREFIX = "Bearer ";
    private static final Set<String> EXACT_PATHS = Set.of(
            "/api/admin/accounts", "/api/admin/documents", "/api/admin/reconciliations",
            "/api/cashier/accounts", "/api/cashier/sessions", "/api/config/denominations",
            "/api/dashboard/stat", "/api/dashboard/stats", "/api/notify-unauthorized",
            // Gestion-tiers : "organisation courante" → endpoint iwm /api/organizations/my
            "/api/organizations/my");
    private static final List<String> OWNED_PREFIXES = List.of(
            "/api/accounts/", "/api/audit", "/api/bills", "/api/cash-registers",
            "/api/cashiers", "/api/cashier/bills", "/api/cashier/fund-requests", "/api/cashier/movements",
            "/api/movements/", "/api/notifications", "/api/reconciliations/",
            "/api/reports/", "/api/sessions", "/api/transactions",
            // Gestion-tiers (B2B strict) : relayés vers iwm via le BFF — le front ne joint jamais iwm.
            "/api/clients", "/api/customers", "/api/suppliers", "/api/prospects",
            "/api/sales-agents", "/api/third-parties", "/api/products");
    private static final Set<String> HOP_HEADERS = Set.of(
            "connection", "keep-alive", "proxy-authenticate", "proxy-authorization",
            "te", "trailers", "transfer-encoding", "upgrade", "host", "content-length");

    private final CashierCoreProperties properties;
    private final KernelTokenRelayStore tokenRelayStore;
    private final JwtService jwtService;
    private final WebClient webClient;

    public CashierCoreProxyWebFilter(CashierCoreProperties properties,
            KernelTokenRelayStore tokenRelayStore, JwtService jwtService,
            WebClient.Builder webClientBuilder) {
        this.properties = properties;
        this.tokenRelayStore = tokenRelayStore;
        this.jwtService = jwtService;
        this.webClient = webClientBuilder.build();
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();
        if (!properties.isEnabled() || !isCashierCoreRoute(path)) {
            return chain.filter(exchange);
        }
        ServerHttpRequest request = exchange.getRequest();
        String facadeToken = extractToken(request.getHeaders());
        String kernelToken = tokenRelayStore.resolve(facadeToken).orElse(facadeToken);
        JwtPayload claims = parseClaims(facadeToken);
        WebClient.RequestBodySpec outbound = webClient.method(request.getMethod())
                .uri(target(request.getURI()))
                .headers(headers -> copyHeaders(request.getHeaders(), headers, kernelToken, claims));
        return outbound.body(BodyInserters.fromDataBuffers(request.getBody()))
                .exchangeToMono(response -> {
                    exchange.getResponse().setStatusCode(response.statusCode());
                    response.headers().asHttpHeaders().forEach((name, values) -> {
                        if (!isHopHeader(name)) {
                            exchange.getResponse().getHeaders().put(name, values);
                        }
                    });
                    return exchange.getResponse().writeWith(
                            response.bodyToFlux(org.springframework.core.io.buffer.DataBuffer.class));
                });
    }

    static boolean isCashierCoreRoute(String path) {
        if (!StringUtils.hasText(path)) {
            return false;
        }
        return EXACT_PATHS.contains(path)
                || OWNED_PREFIXES.stream().anyMatch(prefix -> path.equals(prefix) || path.startsWith(prefix));
    }

    private URI target(URI incoming) {
        String base = properties.getBaseUrl().replaceAll("/+$", "");
        String query = incoming.getRawQuery();
        return URI.create(base + incoming.getRawPath() + (query == null ? "" : "?" + query));
    }

    private void copyHeaders(HttpHeaders incoming, HttpHeaders outgoing, String kernelToken,
            JwtPayload claims) {
        incoming.forEach((name, values) -> {
            if (!isHopHeader(name) && !HttpHeaders.AUTHORIZATION.equalsIgnoreCase(name)) {
                outgoing.put(name, values);
            }
        });
        if (StringUtils.hasText(kernelToken)) {
            outgoing.setBearerAuth(kernelToken);
        }
        if (claims != null) {
            set(outgoing, "X-Organization-Id", claims.getOrganizationId());
            set(outgoing, "X-Agency-Id", claims.getAgencyId());
        }
    }

    private JwtPayload parseClaims(String token) {
        if (!StringUtils.hasText(token) || tokenRelayStore.resolve(token).isEmpty()) {
            return null;
        }
        try {
            return jwtService.parseToken(token);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private String extractToken(HttpHeaders headers) {
        String value = headers.getFirst(HttpHeaders.AUTHORIZATION);
        return StringUtils.hasText(value) && value.startsWith(BEARER_PREFIX)
                ? value.substring(BEARER_PREFIX.length()).trim() : null;
    }

    private void set(HttpHeaders headers, String name, String value) {
        if (StringUtils.hasText(value)) {
            headers.set(name, value);
        }
    }

    private boolean isHopHeader(String name) {
        return HOP_HEADERS.contains(name.toLowerCase());
    }
}
