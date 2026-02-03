package com.erp.cashier.service;

import com.erp.cashier.dto.AuditAuthorProfileResponse;
import com.erp.cashier.dto.AuditAuthorResponse;
import com.erp.cashier.dto.AuditLogResponse;
import com.erp.cashier.dto.AuditRequest;
import com.erp.cashier.dto.NotifyUnauthorizedRequest;
import com.erp.cashier.model.CashRegisterEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
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
        return entityTemplate.insert(CashRegisterEvent.class)
                .using(event)
                .then();
    }

    private CashRegisterEvent buildEvent(String type, String authorId, Object payload) {
        CashRegisterEvent event = new CashRegisterEvent();
        event.setId(UUID.randomUUID().toString());
        event.setType(trimToNull(type));
        event.setAuthorId(trimToNull(authorId));
        event.setDateTime(LocalDateTime.now());
        event.setPayload(serializePayload(payload));
        SubjectRef subject = resolveSubject(payload);
        if (subject != null) {
            event.setSubjectType(subject.type());
            event.setSubjectId(subject.id());
        }
        return event;
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
}
