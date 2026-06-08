package com.erp.cashier.service;

import com.erp.cashier.security.JwtPayload;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import org.springframework.security.core.Authentication;

/**
 * Sends error notifications to Telegram recipients based on user roles.
 */
@Service
public class ErrorNotificationService {
    private static final Logger LOGGER = LoggerFactory.getLogger(ErrorNotificationService.class);
    private static final Duration TELEGRAM_TIMEOUT = Duration.ofSeconds(3);
    private static final String ROLE_CASHIER = "cashier";
    private static final String ROLE_ADMIN = "admin";
    private static final String ROLE_AGENCY_ADMIN = "agency_admin";
    private static final String ROLE_ORGANIZATION_ADMIN = "organization_admin";

    private final R2dbcEntityTemplate entityTemplate;
    private final WebClient webClient;

    public ErrorNotificationService(
            @Qualifier("r2dbcEntityTemplate") R2dbcEntityTemplate entityTemplate,
            WebClient.Builder webClientBuilder) {
        this.entityTemplate = entityTemplate;
        this.webClient = webClientBuilder
                .baseUrl("https://api.telegram.org")
                .build();
    }

    public Mono<Void> notifyError(ServerWebExchange exchange, Throwable ex) {
        if (exchange == null || ex == null) {
            return Mono.empty();
        }
        String path = exchange.getRequest().getPath().value();
        if (!path.startsWith("/api/")) {
            return Mono.empty();
        }
        return exchange.getPrincipal()
                .ofType(Authentication.class)
                .map(Authentication::getDetails)
                .ofType(JwtPayload.class)
                .flatMap(payload -> dispatchNotification(payload, exchange, ex))
                .onErrorResume(err -> {
                    LOGGER.debug("Error notification failed", err);
                    return Mono.empty();
                });
    }

    /**
     * Sends a direct fund-request notification to agency admins.
     *
     * @param agencyId agency identifier
     * @param message message body
     * @return completion signal
     */
    public Mono<Void> notifyAgencyFundRequest(String agencyId, String message) {
        String resolvedAgencyId = trim(agencyId);
        if (!StringUtils.hasText(resolvedAgencyId) || !StringUtils.hasText(message)) {
            return Mono.empty();
        }
        return findAgencyAdminTargets(resolvedAgencyId)
                .filter(TelegramTarget::isValid)
                .distinct(TelegramTarget::key)
                .flatMap(target -> sendTelegram(target, message))
                .onErrorResume(err -> {
                    LOGGER.debug("Fund request notification failed", err);
                    return Mono.empty();
                })
                .then();
    }

    private Mono<Void> dispatchNotification(JwtPayload payload, ServerWebExchange exchange, Throwable ex) {
        if (payload == null) {
            return Mono.empty();
        }
        if (isCashier(payload)) {
            return notifyAgencyAdmins(payload, exchange, ex);
        }
        if (isAgencyAdmin(payload)) {
            return notifyOrganizationAdmins(payload, exchange, ex);
        }
        return Mono.empty();
    }

    private boolean isCashier(JwtPayload payload) {
        return payload != null && ROLE_CASHIER.equalsIgnoreCase(trim(payload.getRole()));
    }

    private boolean isAgencyAdmin(JwtPayload payload) {
        return payload != null
                && ROLE_ADMIN.equalsIgnoreCase(trim(payload.getRole()))
                && ROLE_AGENCY_ADMIN.equalsIgnoreCase(trim(payload.getRoleType()));
    }

    private Mono<Void> notifyAgencyAdmins(JwtPayload payload, ServerWebExchange exchange, Throwable ex) {
        String agencyId = trim(payload.getAgencyId());
        if (!StringUtils.hasText(agencyId)) {
            return Mono.empty();
        }
        String message = buildMessage(payload, exchange, ex);
        return findAgencyAdminTargets(agencyId)
                .filter(target -> target.isValid())
                .distinct(TelegramTarget::key)
                .flatMap(target -> sendTelegram(target, message))
                .then();
    }

    private Mono<Void> notifyOrganizationAdmins(JwtPayload payload, ServerWebExchange exchange, Throwable ex) {
        String organizationId = trim(payload.getOrganizationId());
        if (!StringUtils.hasText(organizationId)) {
            return Mono.empty();
        }
        String message = buildMessage(payload, exchange, ex);
        return findOrganizationAdminTargets(organizationId)
                .filter(target -> target.isValid())
                .distinct(TelegramTarget::key)
                .flatMap(target -> sendTelegram(target, message))
                .then();
    }

    private Flux<TelegramTarget> findAgencyAdminTargets(String agencyId) {
        String sql = "SELECT p.telegram_chat_id, ag.telegram_bot_token AS agency_bot_token, "
                + "o.telegram_bot_token AS org_bot_token "
                + "FROM admin_profile ap "
                + "JOIN person p ON p.id = ap.person_id "
                + "LEFT JOIN agency ag ON ag.id = ap.agency_id "
                + "LEFT JOIN organization o ON o.id = ag.organization_id "
                + "WHERE ap.role_type = :roleType AND ap.agency_id = :agencyId";
        return entityTemplate.getDatabaseClient()
                .sql(sql)
                .bind("roleType", ROLE_AGENCY_ADMIN)
                .bind("agencyId", agencyId)
                .map((row, meta) -> {
                    String chatId = row.get("telegram_chat_id", String.class);
                    String agencyToken = row.get("agency_bot_token", String.class);
                    String orgToken = row.get("org_bot_token", String.class);
                    String token = StringUtils.hasText(agencyToken) ? agencyToken : orgToken;
                    return new TelegramTarget(chatId, token);
                })
                .all();
    }

    private Flux<TelegramTarget> findOrganizationAdminTargets(String organizationId) {
        String sql = "SELECT p.telegram_chat_id, o.telegram_bot_token AS org_bot_token "
                + "FROM admin_profile ap "
                + "JOIN person p ON p.id = ap.person_id "
                + "JOIN organization o ON o.id = ap.organization_id "
                + "WHERE ap.role_type = :roleType AND ap.organization_id = :organizationId";
        return entityTemplate.getDatabaseClient()
                .sql(sql)
                .bind("roleType", ROLE_ORGANIZATION_ADMIN)
                .bind("organizationId", organizationId)
                .map((row, meta) -> new TelegramTarget(
                        row.get("telegram_chat_id", String.class),
                        row.get("org_bot_token", String.class)))
                .all();
    }

    private Mono<Void> sendTelegram(TelegramTarget target, String message) {
        if (target == null || !target.isValid() || !StringUtils.hasText(message)) {
            return Mono.empty();
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("chat_id", target.chatId());
        body.put("text", message);

        return webClient.post()
                .uri(uriBuilder -> uriBuilder.path("/bot{token}/sendMessage").build(target.botToken()))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(String.class)
                .timeout(TELEGRAM_TIMEOUT)
                .onErrorResume(err -> Mono.empty())
                .then();
    }

    private String buildMessage(JwtPayload payload, ServerWebExchange exchange, Throwable ex) {
        StringBuilder builder = new StringBuilder();
        builder.append("ERP Cashier error\n");
        builder.append("Time: ").append(Instant.now()).append("\n");
        builder.append("User: ").append(trim(payload.getUserId())).append("\n");
        builder.append("Role: ").append(trim(payload.getRole()));
        if (StringUtils.hasText(payload.getRoleType())) {
            builder.append(" (").append(trim(payload.getRoleType())).append(")");
        }
        builder.append("\n");
        if (StringUtils.hasText(payload.getOrganizationId())) {
            builder.append("Org: ").append(trim(payload.getOrganizationId())).append("\n");
        }
        if (StringUtils.hasText(payload.getAgencyId())) {
            builder.append("Agency: ").append(trim(payload.getAgencyId())).append("\n");
        }
        String method = exchange.getRequest().getMethod() != null
                ? exchange.getRequest().getMethod().name()
                : "UNKNOWN";
        builder.append("Request: ").append(method).append(" ")
                .append(exchange.getRequest().getURI().getPath()).append("\n");
        builder.append("RequestId: ").append(exchange.getRequest().getId()).append("\n");
        HttpStatusCode status = exchange.getResponse().getStatusCode();
        if (status != null) {
            builder.append("Status: ").append(status.value()).append("\n");
        }
        builder.append("Error: ").append(ex.getClass().getSimpleName());
        String message = ex.getMessage();
        if (!StringUtils.hasText(message) && ex instanceof ResponseStatusException responseStatusException) {
            message = responseStatusException.getReason();
        }
        if (StringUtils.hasText(message)) {
            builder.append(" - ").append(message);
        }
        return truncate(builder.toString(), 3500);
    }

    private String truncate(String value, int max) {
        if (!StringUtils.hasText(value) || value.length() <= max) {
            return value;
        }
        return value.substring(0, max - 3) + "...";
    }

    private String trim(String value) {
        return StringUtils.hasText(value) ? value.trim() : "";
    }

    record TelegramTarget(String chatId, String botToken) {
        boolean isValid() {
            return StringUtils.hasText(chatId) && StringUtils.hasText(botToken);
        }

        String key() {
            return chatId + ":" + botToken;
        }
    }
}
