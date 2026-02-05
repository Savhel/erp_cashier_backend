package com.erp.cashier.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import com.erp.cashier.service.ErrorNotificationService;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebExceptionHandler;
import reactor.core.publisher.Mono;

/**
 * Logs the original exception before any response is written.
 *
 * @author ERP Cashier Team
 * @since 2026-02-04
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestExceptionLoggingHandler implements WebExceptionHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(RequestExceptionLoggingHandler.class);
    private static final String LOGGED_ATTRIBUTE = RequestExceptionLoggingHandler.class.getName() + ".LOGGED";
    private final ErrorNotificationService errorNotificationService;

    public RequestExceptionLoggingHandler(ErrorNotificationService errorNotificationService) {
        this.errorNotificationService = errorNotificationService;
    }

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        if (exchange.getAttribute(LOGGED_ATTRIBUTE) == null) {
            exchange.getAttributes().put(LOGGED_ATTRIBUTE, Boolean.TRUE);
            logException(exchange, ex);
            errorNotificationService.notifyError(exchange, ex)
                    .subscribe(null, err -> LOGGER.debug("Error notification failed", err));
        }

        return Mono.error(ex);
    }

    private void logException(ServerWebExchange exchange, Throwable ex) {
        String path = exchange.getRequest().getPath().value();
        String method = exchange.getRequest().getMethod() != null
                ? exchange.getRequest().getMethod().name()
                : "UNKNOWN";
        HttpStatusCode status = exchange.getResponse().getStatusCode();
        boolean committed = exchange.getResponse().isCommitted();
        LOGGER.error(
                "Unhandled exception {} {} (status={}, committed={})",
                method,
                path,
                status != null ? status.value() : "n/a",
                committed,
                ex
        );
    }
}
