package com.erp.cashier.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * Logs request errors before the response is committed.
 *
 * @author ERP Cashier Team
 * @since 2026-02-04
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestErrorLoggingWebFilter implements WebFilter {
    private static final Logger LOGGER = LoggerFactory.getLogger(RequestErrorLoggingWebFilter.class);

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        if (!path.startsWith("/api/")) {
            return chain.filter(exchange);
        }
        String method = exchange.getRequest().getMethod() != null
                ? exchange.getRequest().getMethod().name()
                : "UNKNOWN";
        String requestId = exchange.getRequest().getId();
        return chain.filter(exchange)
                .doOnError(ex -> logError(exchange, method, path, requestId, ex));
    }

    private void logError(
            ServerWebExchange exchange,
            String method,
            String path,
            String requestId,
            Throwable error
    ) {
        HttpStatusCode status = exchange.getResponse().getStatusCode();
        boolean committed = exchange.getResponse().isCommitted();
        LOGGER.error(
                "Request error {} {} (id={}, status={}, committed={})",
                method,
                path,
                requestId,
                status != null ? status.value() : "n/a",
                committed,
                error
        );
    }
}
