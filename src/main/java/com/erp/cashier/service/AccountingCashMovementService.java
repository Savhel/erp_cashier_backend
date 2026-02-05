package com.erp.cashier.service;

import com.erp.cashier.config.AccountingProperties;
import com.erp.cashier.dto.AccountingCashMovementRequest;
import com.erp.cashier.dto.AccountingCashMovementResponse;
import com.erp.cashier.model.CashRegisterMovement;
import java.time.Duration;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

/**
 * Service to sync cash movements with external accounting.
 *
 * @author ERP Cashier Team
 * @since 2026-02-05
 */
@Service
public class AccountingCashMovementService {
    private static final Logger LOGGER = LoggerFactory.getLogger(AccountingCashMovementService.class);
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    private final WebClient webClient;
    private final AccountingProperties properties;
    private final DatabaseClient databaseClient;

    /**
     * Creates the accounting sync service.
     *
     * @param webClientBuilder web client builder
     * @param properties accounting properties
     * @param databaseClient database client
     */
    public AccountingCashMovementService(
            WebClient.Builder webClientBuilder,
            AccountingProperties properties,
            DatabaseClient databaseClient
    ) {
        this.webClient = webClientBuilder.baseUrl(properties.getBaseUrl()).build();
        this.properties = properties;
        this.databaseClient = databaseClient;
    }

    /**
     * Sends a cash movement to the external accounting system and updates it if accepted.
     *
     * @param request movement payload
     * @return external response
     */
    public Mono<AccountingCashMovementResponse> syncCashMovement(AccountingCashMovementRequest request) {
        if (request == null) {
            return Mono.error(new ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST,
                    "Cash movement payload is required."
            ));
        }
        String movementId = request.getId();
        if (!StringUtils.hasText(movementId)) {
            return Mono.error(new ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST,
                    "Movement id is required."
            ));
        }

        return sendToAccounting(request)
                .flatMap(response -> {
                    if (Boolean.TRUE.equals(response.getSuccess())) {
                        return markAccounted(movementId).thenReturn(response);
                    }
                    return Mono.just(response);
                });
    }

    /**
     * Builds and sends a movement to accounting (best effort).
     *
     * @param movement movement entity
     * @param recipientAccountingAccount accounting account for recipient
     * @param emitterAccountingAccount accounting account for emitter
     * @return external response
     */
    public Mono<AccountingCashMovementResponse> syncMovement(
            CashRegisterMovement movement,
            String recipientAccountingAccount,
            String emitterAccountingAccount
    ) {
        if (movement == null || !StringUtils.hasText(movement.getId())) {
            return Mono.just(AccountingCashMovementResponse.failure("Movement payload is required."));
        }
        AccountingCashMovementRequest request = buildRequest(
                movement,
                recipientAccountingAccount,
                emitterAccountingAccount
        );
        return syncCashMovement(request);
    }

    /**
     * Fire-and-forget accounting sync.
     *
     * @param movement movement entity
     * @param recipientAccountingAccount recipient accounting account
     * @param emitterAccountingAccount emitter accounting account
     */
    public void syncMovementAsync(
            CashRegisterMovement movement,
            String recipientAccountingAccount,
            String emitterAccountingAccount
    ) {
        syncMovement(movement, recipientAccountingAccount, emitterAccountingAccount)
                .onErrorResume(ex -> {
                    LOGGER.warn("Accounting sync failed: {}", ex.getMessage());
                    return Mono.empty();
                })
                .subscribe();
    }

    private Mono<AccountingCashMovementResponse> sendToAccounting(AccountingCashMovementRequest request) {
        if (!properties.isEnabled()) {
            return Mono.just(AccountingCashMovementResponse.failure("Accounting sync disabled."));
        }
        Duration timeout = Duration.ofSeconds(Math.max(1, properties.getTimeoutSeconds()));
        return webClient.post()
                .uri(properties.getCashMovementsPath())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .retrieve()
                .onStatus(HttpStatusCode::isError, response -> response.bodyToMono(String.class)
                        .defaultIfEmpty("")
                        .flatMap(body -> Mono.error(new ResponseStatusException(
                                response.statusCode(),
                                "Accounting sync failed: " + body
                        )))
                )
                .bodyToMono(AccountingCashMovementResponse.class)
                .timeout(timeout)
                .onErrorResume(ex -> {
                    LOGGER.warn("Accounting sync failed: {}", ex.getMessage());
                    return Mono.just(AccountingCashMovementResponse.failure(
                            "Accounting sync failed or timed out."
                    ));
                });
    }

    private Mono<Void> markAccounted(String movementId) {
        return databaseClient.sql("UPDATE cash_register_movement SET is_accounted = true WHERE id = :id")
                .bind("id", movementId)
                .fetch()
                .rowsUpdated()
                .then();
    }

    private AccountingCashMovementRequest buildRequest(
            CashRegisterMovement movement,
            String recipientAccountingAccount,
            String emitterAccountingAccount
    ) {
        AccountingCashMovementRequest request = new AccountingCashMovementRequest();
        request.setId(movement.getId());
        request.setSessionId(movement.getSessionId());
        request.setSense(movement.getSense());
        request.setAmount(movement.getAmount());
        request.setReason(movement.getReason());
        request.setRecipientId(movement.getRecipientId());
        request.setEmitterId(movement.getEmitterId());
        request.setIsAccounted(Boolean.TRUE.equals(movement.getIsAccounted()));
        request.setEventTicketingDetails(Boolean.TRUE.equals(movement.getEventTicketingDetails()));
        request.setExternalReference(movement.getExternalReference());
        if (movement.getCreateOn() != null) {
            request.setCreateOn(movement.getCreateOn().atOffset(ZoneOffset.UTC).format(DATE_TIME_FORMATTER));
        }
        request.setCreateBy(movement.getCreateBy());
        request.setEmitterAccountingAccount(trimToNull(emitterAccountingAccount));
        request.setRecipientAccountingAccount(trimToNull(recipientAccountingAccount));
        request.setIsDeleted(Boolean.TRUE.equals(movement.getIsDeleted()));
        return request;
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }
}
