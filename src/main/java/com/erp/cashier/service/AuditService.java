package com.erp.cashier.service;

import com.erp.cashier.dto.AuditAuthorProfileResponse;
import com.erp.cashier.dto.AuditAuthorResponse;
import com.erp.cashier.dto.AuditLogResponse;
import com.erp.cashier.dto.AuditRequest;
import com.erp.cashier.dto.NotifyUnauthorizedRequest;
import com.erp.cashier.model.CashRegisterEvent;
import com.erp.cashier.model.CashRegisterMovement;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Service for audit logging.
 *
 * @author ERP Cashier Team
 * @since 2025-01-20
 */
@Service
public class AuditService {
    private static final String TYPE_AUDIT = "audit";
    private static final String TYPE_UNAUTHORIZED = "unauthorized";
    private static final int DEFAULT_LIMIT = 200;
    private static final String SUBJECT_MOVEMENT = "movement";
    private static final DateTimeFormatter HASH_DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final R2dbcEntityTemplate entityTemplate;
    private final ObjectMapper objectMapper;

    /**
     * Creates the audit service.
     *
     * @param entityTemplate entity template
     * @param objectMapper object mapper
     */
    public AuditService(R2dbcEntityTemplate entityTemplate, ObjectMapper objectMapper) {
        this.entityTemplate = entityTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * Lists audit logs.
     *
     * @param limit limit
     * @param organizationId organization identifier
     * @param agencyId agency identifier
     * @return audit logs
     */
    public Flux<AuditLogResponse> listAudit(Integer limit, String organizationId, String agencyId) {
        int resolvedLimit = limit != null && limit > 0 ? limit : DEFAULT_LIMIT;
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT e.id, e.type, e.date_time, e.payload, ");
        sql.append("p.user_first_name AS author_first_name, ");
        sql.append("p.user_name AS author_user_name, ");
        sql.append("ap.role_type AS author_role_type, ");
        sql.append("ap.agency_id AS author_agency_id ");
        sql.append("FROM cash_register_event e ");
        sql.append("LEFT JOIN person p ON p.id = e.author_id ");
        sql.append("LEFT JOIN admin_profile ap ON ap.person_id = e.author_id ");
        sql.append("LEFT JOIN agency ag_sub ON e.subject_type = 'agency' AND e.subject_id = ag_sub.id ");
        sql.append("WHERE 1=1 ");
        if (StringUtils.hasText(agencyId)) {
            sql.append("AND (ap.agency_id = :agencyId ");
            sql.append("OR (e.subject_type = 'agency' AND e.subject_id = :agencyId)) ");
            if (StringUtils.hasText(organizationId)) {
                sql.append("AND (ap.organization_id = :organizationId ");
                sql.append("OR ag_sub.organization_id = :organizationId ");
                sql.append("OR (e.subject_type = 'organization' AND e.subject_id = :organizationId)) ");
            }
        } else if (StringUtils.hasText(organizationId)) {
            sql.append("AND (ap.organization_id = :organizationId ");
            sql.append("OR (e.subject_type = 'organization' AND e.subject_id = :organizationId) ");
            sql.append("OR (e.subject_type = 'agency' AND ag_sub.organization_id = :organizationId)) ");
        }
        sql.append("ORDER BY e.date_time DESC ");
        sql.append("LIMIT ").append(resolvedLimit);

        DatabaseClient.GenericExecuteSpec spec = entityTemplate.getDatabaseClient()
                .sql(sql.toString());
        if (StringUtils.hasText(organizationId)) {
            spec = spec.bind("organizationId", organizationId);
        }
        if (StringUtils.hasText(agencyId)) {
            spec = spec.bind("agencyId", agencyId);
        }

        return spec.map((row, meta) -> {
            String payload = row.get("payload", String.class);
            AuditAuthorProfileResponse adminProfile = null;
            String roleType = row.get("author_role_type", String.class);
            String agency = row.get("author_agency_id", String.class);
            if (StringUtils.hasText(roleType) || StringUtils.hasText(agency)) {
                adminProfile = new AuditAuthorProfileResponse(roleType, agency);
            }
            AuditAuthorResponse author = new AuditAuthorResponse(
                    row.get("author_first_name", String.class),
                    row.get("author_user_name", String.class),
                    adminProfile
            );
            return new AuditLogResponse(
                    row.get("id", String.class),
                    row.get("type", String.class),
                    row.get("date_time", LocalDateTime.class),
                    payload,
                    author
            );
        }).all();
    }

    /**
     * Records an audit log.
     *
     * @param request audit request
     * @param authorId author identifier
     * @return completion signal
     */
    public Mono<Void> recordAudit(AuditRequest request, String authorId) {
        if (request == null) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Audit payload is required."));
        }
        String resolvedType = trimToNull(request.getType());
        String eventType = StringUtils.hasText(resolvedType) ? resolvedType : TYPE_AUDIT;
        return recordEvent(eventType, authorId, request);
    }

    /**
     * Records an unauthorized access attempt.
     *
     * @param request unauthorized request
     * @return completion signal
     */
    public Mono<Void> recordUnauthorized(NotifyUnauthorizedRequest request) {
        if (request == null) {
            return Mono.error(new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Unauthorized payload is required."
            ));
        }
        return recordEvent(TYPE_UNAUTHORIZED, request.getUserId(), request);
    }

    /**
     * Records an audit event.
     *
     * @param type event type
     * @param authorId author identifier
     * @param payload event payload
     * @return completion signal
     */
    public Mono<Void> recordEvent(String type, String authorId, Object payload) {
        CashRegisterEvent event = buildEvent(type, authorId, payload);
        return insertWithHash(event);
    }

    /**
     * Records an audit event with explicit subject context.
     *
     * @param type event type
     * @param authorId author identifier
     * @param sessionId session identifier (optional)
     * @param accountId account identifier (optional)
     * @param subjectType subject type
     * @param subjectId subject identifier
     * @param idempotency idempotency key
     * @param payload event payload
     * @return completion signal
     */
    public Mono<Void> recordSubjectEvent(
            String type,
            String authorId,
            String sessionId,
            String accountId,
            String subjectType,
            String subjectId,
            String idempotency,
            Object payload
    ) {
        CashRegisterEvent event = new CashRegisterEvent();
        event.setId(UUID.randomUUID().toString());
        event.setType(trimToNull(type));
        event.setAuthorId(trimToNull(authorId));
        event.setSessionId(trimToNull(sessionId));
        event.setAccountId(trimToNull(accountId));
        event.setSubjectType(trimToNull(subjectType));
        event.setSubjectId(trimToNull(subjectId));
        event.setIdempotency(trimToNull(idempotency));
        event.setDateTime(LocalDateTime.now());
        event.setPayload(serializePayload(payload));
        return insertWithHash(event);
    }

    /**
     * Records an authentication event with optional session context.
     *
     * @param type event type
     * @param authorId author identifier
     * @param sessionId session identifier (optional)
     * @param payload event payload
     * @return completion signal
     */
    public Mono<Void> recordAuthEvent(String type, String authorId, String sessionId, Object payload) {
        CashRegisterEvent event = new CashRegisterEvent();
        event.setId(UUID.randomUUID().toString());
        event.setType(trimToNull(type));
        event.setAuthorId(trimToNull(authorId));
        event.setSessionId(trimToNull(sessionId));
        event.setDateTime(LocalDateTime.now());
        event.setPayload(serializePayload(payload));
        event.setSubjectType("user");
        event.setSubjectId(trimToNull(authorId));
        event.setIdempotency(UUID.randomUUID().toString());
        return insertWithHash(event);
    }

    /**
     * Records a cash register movement event with hash chaining (per session).
     *
     * @param movement movement entity
     * @return completion signal
     */
    public Mono<Void> recordMovementEvent(CashRegisterMovement movement) {
        if (movement == null) {
            return Mono.empty();
        }
        CashRegisterEvent event = new CashRegisterEvent();
        event.setId(UUID.randomUUID().toString());
        event.setSessionId(trimToNull(movement.getSessionId()));
        event.setAccountId(resolveMovementAccountId(movement));
        String type = trimToNull(movement.getReason());
        event.setType(StringUtils.hasText(type) ? type : SUBJECT_MOVEMENT);
        event.setIdempotency(trimToNull(movement.getId()));
        event.setDateTime(LocalDateTime.now());
        event.setAuthorId(resolveAuthorId(movement));
        event.setSubjectType(SUBJECT_MOVEMENT);
        event.setSubjectId(trimToNull(movement.getId()));
        event.setPayload(serializePayload(buildMovementPayload(movement)));

        return insertWithHash(event);
    }

    /**
     * Records a movement event in a fire-and-forget fashion.
     *
     * @param movement movement entity
     */
    public void recordMovementEventAsync(CashRegisterMovement movement) {
        recordMovementEvent(movement)
                .onErrorResume(ex -> Mono.empty())
                .subscribe();
    }

    private CashRegisterEvent buildEvent(String type, String authorId, Object payload) {
        CashRegisterEvent event = new CashRegisterEvent();
        event.setId(UUID.randomUUID().toString());
        event.setType(trimToNull(type));
        event.setAuthorId(trimToNull(authorId));
        event.setDateTime(LocalDateTime.now());
        event.setPayload(serializePayload(payload));
        event.setIdempotency(UUID.randomUUID().toString());
        SubjectRef subject = resolveSubject(payload);
        if (subject != null) {
            event.setSubjectType(subject.type());
            event.setSubjectId(subject.id());
        }
        return event;
    }

    private Mono<Void> insertWithHash(CashRegisterEvent event) {
        if (event != null && !StringUtils.hasText(event.getIdempotency())) {
            event.setIdempotency(UUID.randomUUID().toString());
        }
        return resolvePreviousHash(event.getSessionId())
                .switchIfEmpty(Mono.just(""))
                .flatMap(previousHash -> {
                    event.setPreviousHash(StringUtils.hasText(previousHash) ? previousHash : null);
                    event.setHash(computeHash(event));
                    return entityTemplate.insert(CashRegisterEvent.class)
                            .using(event)
                            .then();
                })
                .onErrorResume(DuplicateKeyException.class, ex -> Mono.empty());
    }

    private Mono<String> resolvePreviousHash(String sessionId) {
        String resolved = trimToNull(sessionId);
        if (!StringUtils.hasText(resolved)) {
            return Mono.empty();
        }
        String sql = "SELECT hash FROM cash_register_event "
                + "WHERE session_id = :sessionId "
                + "ORDER BY date_time DESC, id DESC LIMIT 1";
        return entityTemplate.getDatabaseClient()
                .sql(sql)
                .bind("sessionId", resolved)
                .map((row, meta) -> row.get("hash", String.class))
                .one();
    }

    private String computeHash(CashRegisterEvent event) {
        String payload = event.getPayload() != null ? event.getPayload() : "";
        String date = event.getDateTime() != null
                ? event.getDateTime().format(HASH_DATE_FORMATTER)
                : "";
        String base = String.join("|",
                nullToEmpty(event.getPreviousHash()),
                nullToEmpty(event.getId()),
                nullToEmpty(event.getType()),
                nullToEmpty(event.getSessionId()),
                nullToEmpty(event.getAccountId()),
                nullToEmpty(event.getAuthorId()),
                nullToEmpty(event.getSubjectType()),
                nullToEmpty(event.getSubjectId()),
                nullToEmpty(event.getIdempotency()),
                date,
                payload
        );
        return sha256(base);
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                builder.append(String.format("%02x", b));
            }
            return builder.toString();
        } catch (Exception ex) {
            return null;
        }
    }

    private Map<String, Object> buildMovementPayload(CashRegisterMovement movement) {
        Map<String, Object> payload = new java.util.LinkedHashMap<>();
        putIfPresent(payload, "movement_id", movement.getId());
        putIfPresent(payload, "session_id", movement.getSessionId());
        putIfPresent(payload, "sense", movement.getSense());
        putIfPresent(payload, "amount", movement.getAmount());
        putIfPresent(payload, "reason", movement.getReason());
        putIfPresent(payload, "external_reference", movement.getExternalReference());
        putIfPresent(payload, "emitter_id", movement.getEmitterId());
        putIfPresent(payload, "recipient_id", movement.getRecipientId());
        putIfPresent(payload, "payment_method", movement.getPaymentMethod());
        putIfPresent(payload, "is_accounted", movement.getIsAccounted());
        putIfPresent(payload, "event_ticketing_details", movement.getEventTicketingDetails());
        return payload;
    }

    private String resolveMovementAccountId(CashRegisterMovement movement) {
        if (movement == null) {
            return null;
        }
        String reason = trimToNull(movement.getReason());
        String sense = trimToNull(movement.getSense());
        String emitter = trimToNull(movement.getEmitterId());
        String recipient = trimToNull(movement.getRecipientId());

        if ("deposit".equalsIgnoreCase(reason)) {
            return emitter;
        }
        if ("withdrawal".equalsIgnoreCase(reason)) {
            return recipient;
        }
        if ("bill".equalsIgnoreCase(reason)) {
            return StringUtils.hasText(emitter) ? emitter : recipient;
        }
        if ("p2p_transfer".equalsIgnoreCase(reason) || "transfer".equalsIgnoreCase(reason)) {
            if (isEntree(sense)) {
                return StringUtils.hasText(recipient) ? recipient : emitter;
            }
            if (isSortie(sense)) {
                return StringUtils.hasText(emitter) ? emitter : recipient;
            }
            return StringUtils.hasText(emitter) ? emitter : recipient;
        }
        if (isEntree(sense)) {
            return StringUtils.hasText(recipient) ? recipient : emitter;
        }
        if (isSortie(sense)) {
            return StringUtils.hasText(emitter) ? emitter : recipient;
        }
        return StringUtils.hasText(recipient) ? recipient : emitter;
    }

    private boolean isEntree(String sense) {
        if (!StringUtils.hasText(sense)) {
            return false;
        }
        String normalized = sense.trim().toLowerCase();
        return "entree".equals(normalized) || "in".equals(normalized);
    }

    private boolean isSortie(String sense) {
        if (!StringUtils.hasText(sense)) {
            return false;
        }
        String normalized = sense.trim().toLowerCase();
        return "sortie".equals(normalized) || "out".equals(normalized) || "transfert".equals(normalized);
    }

    private String resolveAuthorId(CashRegisterMovement movement) {
        String author = trimToNull(movement.getCreateBy());
        if (StringUtils.hasText(author)) {
            return author;
        }
        return trimToNull(movement.getEmitterId());
    }

    private SubjectRef resolveSubject(Object payload) {
        if (!(payload instanceof Map<?, ?> map)) {
            return null;
        }
        Object agency = map.get("agency_id");
        if (agency == null) {
            agency = map.get("agencyId");
        }
        if (agency != null) {
            return new SubjectRef("agency", String.valueOf(agency));
        }
        Object organization = map.get("organization_id");
        if (organization == null) {
            organization = map.get("organizationId");
        }
        if (organization != null) {
            return new SubjectRef("organization", String.valueOf(organization));
        }
        return null;
    }

    private record SubjectRef(String type, String id) {
    }

    private String serializePayload(Object payload) {
        try {
            return payload != null ? objectMapper.writeValueAsString(payload) : "{}";
        } catch (Exception ex) {
            return "{}";
        }
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private String nullToEmpty(String value) {
        return value != null ? value : "";
    }

    private void putIfPresent(Map<String, Object> payload, String key, Object value) {
        if (value != null) {
            payload.put(key, value);
        }
    }
}
