package com.erp.cashier.service;

import com.erp.cashier.dto.RegisterReportRequest;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Map;
import java.util.Objects;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.springframework.http.HttpStatus;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Service for generating PDF reports.
 *
 * @author ERP Cashier Team
 * @since 2025-01-20
 */
@Service
public class ReportService {
    private static final int TRANSACTION_LIMIT = 500;
    private static final int MOVEMENT_LIMIT = 500;
    private static final int AUDIT_DEFAULT_LIMIT = 200;
    private static final int AUDIT_MAX_LIMIT = 500;
    private static final float PAGE_MARGIN = 40f;
    private static final float HEADER_HEIGHT = 24f;
    private static final float LOGO_SIZE = 10f;
    private static final float FOOTER_HEIGHT = 20f;
    private static final float TABLE_FONT_SIZE = 8.5f;
    private static final float HEADER_FONT_SIZE = 10.5f;
    private static final float TITLE_FONT_SIZE = 16f;
    private static final float SUBTITLE_FONT_SIZE = 9.5f;
    private static final DateTimeFormatter DATE_TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.ROOT);
    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.ROOT);
    private static final DecimalFormat AMOUNT_FORMAT;

    static {
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.ROOT);
        AMOUNT_FORMAT = new DecimalFormat("#,##0", symbols);
    }

    private final R2dbcEntityTemplate entityTemplate;
    private final AuditService auditService;

    /**
     * Creates the report service.
     *
     * @param entityTemplate entity template
     * @param auditService audit service
     */
    public ReportService(R2dbcEntityTemplate entityTemplate, AuditService auditService) {
        this.entityTemplate = entityTemplate;
        this.auditService = auditService;
    }

    /**
     * Generates a transactions report for the scoped organization/agency.
     *
     * @param organizationId organization identifier
     * @param agencyId agency identifier
     * @param userId author identifier
     * @param startDate start date filter
     * @param endDate end date filter
     * @param type transaction type filter
     * @param registerId cash register filter
     * @param cashierId cashier filter
     * @return PDF bytes
     */
    public Mono<byte[]> generateTransactionsReport(
            String organizationId,
            String agencyId,
            String userId,
            String startDate,
            String endDate,
            String type,
            String registerId,
            String cashierId
    ) {
        requireOrganization(organizationId);
        LocalDateTime startOn = parseStartDateTime(startDate);
        LocalDateTime endOn = parseEndDateTime(endDate);
        String resolvedType = trimToNull(type);
        String resolvedRegisterId = trimToNull(registerId);
        String resolvedCashierId = trimToNull(cashierId);
        Mono<ReportHeader> headerMono = resolveHeader(organizationId, agencyId);
        Mono<List<TransactionRow>> rowsMono = fetchTransactions(
                        organizationId,
                        agencyId,
                        startOn,
                        endOn,
                        resolvedType,
                        resolvedRegisterId,
                        resolvedCashierId,
                        TRANSACTION_LIMIT
                )
                .collectList();

        return Mono.zip(headerMono, rowsMono)
                .flatMap(tuple -> {
                    ReportHeader header = tuple.getT1();
                    List<TransactionRow> rows = tuple.getT2();
                    byte[] pdf = buildTransactionsPdf(
                            header,
                            rows,
                            TRANSACTION_LIMIT,
                            startOn,
                            endOn,
                            resolvedType,
                            resolvedRegisterId,
                            resolvedCashierId
                    );
                    Map<String, Object> payload = new LinkedHashMap<>();
                    payload.put("report_type", "transactions");
                    payload.put("organization_id", organizationId);
                    payload.put("agency_id", agencyId);
                    payload.put("row_count", rows.size());
                    payload.put("limit", TRANSACTION_LIMIT);
                    putIfPresent(payload, "start_on", startDate);
                    putIfPresent(payload, "end_on", endDate);
                    putIfPresent(payload, "type", resolvedType);
                    putIfPresent(payload, "register_id", resolvedRegisterId);
                    putIfPresent(payload, "cashier_id", resolvedCashierId);
                    return recordReportEvent(userId, payload).thenReturn(pdf);
                });
    }

    /**
     * Generates a transactions report for the scoped organization/agency.
     *
     * @param organizationId organization identifier
     * @param agencyId agency identifier
     * @param userId author identifier
     * @return PDF bytes
     */
    public Mono<byte[]> generateTransactionsReport(String organizationId, String agencyId, String userId) {
        return generateTransactionsReport(organizationId, agencyId, userId, null, null, null, null, null);
    }

    /**
     * Generates a cash register report for the scoped organization/agency.
     *
     * @param registerId register identifier
     * @param request report filter
     * @param organizationId organization identifier
     * @param agencyId agency identifier
     * @param userId author identifier
     * @return PDF bytes
     */
    public Mono<byte[]> generateRegisterReport(
            String registerId,
            RegisterReportRequest request,
            String organizationId,
            String agencyId,
            String userId
    ) {
        requireOrganization(organizationId);
        String resolvedRegisterId = trimToNull(registerId);
        if (!StringUtils.hasText(resolvedRegisterId)) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "register id is required"));
        }
        LocalDateTime startOn = parseStartDateTime(request != null ? request.getStartDate() : null);
        LocalDateTime endOn = parseEndDateTime(request != null ? request.getEndDate() : null);
        Mono<RegisterInfo> registerMono = fetchRegisterInfo(resolvedRegisterId)
                .flatMap(info -> validateScope(info.organizationId(), info.agencyId(), organizationId, agencyId)
                        .thenReturn(info))
                .cache();

        Mono<List<MovementRow>> movementsMono = registerMono
                .flatMap(info -> fetchRegisterMovements(info.id(), startOn, endOn, MOVEMENT_LIMIT)
                        .collectList());

        return Mono.zip(registerMono, movementsMono)
                .flatMap(tuple -> {
                    RegisterInfo info = tuple.getT1();
                    List<MovementRow> movements = tuple.getT2();
                    ReportHeader header = new ReportHeader(info.organizationName(), info.agencyName());
                    byte[] pdf = buildRegisterPdf(header, info, movements, startOn, endOn, MOVEMENT_LIMIT);
                    Map<String, Object> payload = new LinkedHashMap<>();
                    payload.put("report_type", "register");
                    payload.put("register_id", info.id());
                    payload.put("organization_id", info.organizationId());
                    payload.put("agency_id", info.agencyId());
                    payload.put("start_on", formatDateTime(startOn));
                    payload.put("end_on", formatDateTime(endOn));
                    payload.put("row_count", movements.size());
                    payload.put("limit", MOVEMENT_LIMIT);
                    return recordReportEvent(userId, payload).thenReturn(pdf);
                });
    }

    /**
     * Generates a session report for the scoped organization/agency.
     *
     * @param sessionId session identifier
     * @param organizationId organization identifier
     * @param agencyId agency identifier
     * @param userId author identifier
     * @return PDF bytes
     */
    public Mono<byte[]> generateSessionReport(
            String sessionId,
            String organizationId,
            String agencyId,
            String userId
    ) {
        requireOrganization(organizationId);
        String resolvedSessionId = trimToNull(sessionId);
        if (!StringUtils.hasText(resolvedSessionId)) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "session id is required"));
        }
        Mono<SessionInfo> sessionMono = fetchSessionInfo(resolvedSessionId)
                .flatMap(info -> validateScope(info.organizationId(), info.agencyId(), organizationId, agencyId)
                        .thenReturn(info))
                .cache();

        Mono<List<MovementRow>> movementsMono = sessionMono
                .flatMap(info -> fetchSessionMovements(info.id(), MOVEMENT_LIMIT).collectList());
        Mono<Optional<ReconciliationRow>> reconciliationMono = sessionMono
                .flatMap(info -> fetchReconciliation(info.id()))
                .map(Optional::of)
                .defaultIfEmpty(Optional.empty());

        return Mono.zip(sessionMono, movementsMono, reconciliationMono)
                .flatMap(tuple -> {
                    SessionInfo info = tuple.getT1();
                    List<MovementRow> movements = tuple.getT2();
                    ReconciliationRow reconciliation = tuple.getT3().orElse(null);
                    ReportHeader header = new ReportHeader(info.organizationName(), info.agencyName());
                    byte[] pdf = buildSessionPdf(header, info, movements, reconciliation, MOVEMENT_LIMIT);
                    Map<String, Object> payload = new LinkedHashMap<>();
                    payload.put("report_type", "session");
                    payload.put("session_id", info.id());
                    payload.put("organization_id", info.organizationId());
                    payload.put("agency_id", info.agencyId());
                    payload.put("row_count", movements.size());
                    payload.put("limit", MOVEMENT_LIMIT);
                    return recordReportEvent(userId, payload).thenReturn(pdf);
                });
    }

    /**
     * Generates an audit report for the scoped organization/agency.
     *
     * @param type audit type filter
     * @param startOn start date filter
     * @param endOn end date filter
     * @param limit row limit
     * @param organizationId organization identifier
     * @param agencyId agency identifier
     * @param userId author identifier
     * @return PDF bytes
     */
    public Mono<byte[]> generateAuditReport(
            String type,
            String startOn,
            String endOn,
            Integer limit,
            String organizationId,
            String agencyId,
            String userId
    ) {
        requireOrganization(organizationId);
        LocalDateTime startDateTime = parseStartDateTime(startOn);
        LocalDateTime endDateTime = parseEndDateTime(endOn);
        int resolvedLimit = clampLimit(limit, AUDIT_DEFAULT_LIMIT, AUDIT_MAX_LIMIT);

        Mono<ReportHeader> headerMono = resolveHeader(organizationId, agencyId);
        Mono<List<AuditRow>> rowsMono = fetchAuditRows(
                type,
                startDateTime,
                endDateTime,
                resolvedLimit,
                organizationId,
                agencyId
        ).collectList();

        return Mono.zip(headerMono, rowsMono)
                .flatMap(tuple -> {
                    ReportHeader header = tuple.getT1();
                    List<AuditRow> rows = tuple.getT2();
                    byte[] pdf = buildAuditPdf(header, rows, type, startDateTime, endDateTime, resolvedLimit);
                    Map<String, Object> payload = new LinkedHashMap<>();
                    payload.put("report_type", "audit");
                    payload.put("organization_id", organizationId);
                    payload.put("agency_id", agencyId);
                    payload.put("type", trimToNull(type));
                    payload.put("start_on", formatDateTime(startDateTime));
                    payload.put("end_on", formatDateTime(endDateTime));
                    payload.put("row_count", rows.size());
                    payload.put("limit", resolvedLimit);
                    return recordReportEvent(userId, payload).thenReturn(pdf);
                });
    }

    private Mono<Void> recordReportEvent(String userId, Map<String, Object> payload) {
        return auditService.recordEvent("report_generated", userId, payload);
    }

    private void requireOrganization(String organizationId) {
        if (!StringUtils.hasText(organizationId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Organization scope is required.");
        }
    }

    private Mono<Void> validateScope(
            String resourceOrgId,
            String resourceAgencyId,
            String organizationId,
            String agencyId
    ) {
        if (!StringUtils.hasText(resourceOrgId)) {
            return Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Resource not found."));
        }
        if (!Objects.equals(resourceOrgId, organizationId)) {
            return Mono.error(new ResponseStatusException(HttpStatus.FORBIDDEN, "Forbidden."));
        }
        if (StringUtils.hasText(agencyId) && !Objects.equals(resourceAgencyId, agencyId)) {
            return Mono.error(new ResponseStatusException(HttpStatus.FORBIDDEN, "Forbidden."));
        }
        return Mono.empty();
    }

    private Mono<ReportHeader> resolveHeader(String organizationId, String agencyId) {
        Mono<String> orgName = resolveOrganizationName(organizationId);
        Mono<String> agencyName = resolveAgencyName(agencyId);
        return Mono.zip(orgName, agencyName)
                .map(tuple -> new ReportHeader(tuple.getT1(), tuple.getT2()));
    }

    private Mono<String> resolveOrganizationName(String organizationId) {
        if (!StringUtils.hasText(organizationId)) {
            return Mono.just("Unknown organization");
        }
        String sql = "SELECT name FROM organization WHERE id = :id";
        return entityTemplate.getDatabaseClient()
                .sql(sql)
                .bind("id", organizationId)
                .map((row, meta) -> row.get("name", String.class))
                .one()
                .defaultIfEmpty("Unknown organization");
    }

    private Mono<String> resolveAgencyName(String agencyId) {
        if (!StringUtils.hasText(agencyId)) {
            return Mono.just("All agencies");
        }
        String sql = "SELECT name FROM agency WHERE id = :id";
        return entityTemplate.getDatabaseClient()
                .sql(sql)
                .bind("id", agencyId)
                .map((row, meta) -> row.get("name", String.class))
                .one()
                .defaultIfEmpty("Unknown agency");
    }

    private Flux<TransactionRow> fetchTransactions(
            String organizationId,
            String agencyId,
            LocalDateTime startOn,
            LocalDateTime endOn,
            String type,
            String registerId,
            String cashierId,
            int limit
    ) {
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT m.create_on, m.sense, m.amount, m.reason, m.external_reference, ");
        sql.append("p.user_first_name AS cashier_first_name, ");
        sql.append("p.user_name AS cashier_user_name, ");
        sql.append("r.town AS register_name, ");
        sql.append("ag.name AS agency_name ");
        sql.append("FROM cash_register_movement m ");
        sql.append("LEFT JOIN person p ON p.id = m.create_by ");
        sql.append("LEFT JOIN cash_register_session s ON s.id = m.session_id ");
        sql.append("LEFT JOIN cash_register r ON r.id = s.cash_register_id ");
        sql.append("LEFT JOIN agency ag ON ag.id = r.agency_id ");
        sql.append("WHERE m.is_deleted = false ");
        sql.append("AND ag.organization_id = :organizationId ");
        if (StringUtils.hasText(agencyId)) {
            sql.append("AND ag.id = :agencyId ");
        }
        if (StringUtils.hasText(registerId)) {
            sql.append("AND r.id = :registerId ");
        }
        if (StringUtils.hasText(cashierId)) {
            sql.append("AND m.create_by = :cashierId ");
        }
        if (StringUtils.hasText(type)) {
            sql.append("AND m.reason = :type ");
        }
        if (startOn != null) {
            sql.append("AND m.create_on >= :startOn ");
        }
        if (endOn != null) {
            sql.append("AND m.create_on <= :endOn ");
        }
        sql.append("ORDER BY m.create_on DESC ");
        sql.append("LIMIT :limit");

        DatabaseClient.GenericExecuteSpec spec = entityTemplate.getDatabaseClient()
                .sql(sql.toString())
                .bind("organizationId", organizationId)
                .bind("limit", limit);
        if (StringUtils.hasText(agencyId)) {
            spec = spec.bind("agencyId", agencyId);
        }
        if (StringUtils.hasText(registerId)) {
            spec = spec.bind("registerId", registerId);
        }
        if (StringUtils.hasText(cashierId)) {
            spec = spec.bind("cashierId", cashierId);
        }
        if (StringUtils.hasText(type)) {
            spec = spec.bind("type", type);
        }
        if (startOn != null) {
            spec = spec.bind("startOn", startOn);
        }
        if (endOn != null) {
            spec = spec.bind("endOn", endOn);
        }
        return spec.map((row, meta) -> new TransactionRow(
                        row.get("create_on", LocalDateTime.class),
                        row.get("sense", String.class),
                        row.get("amount", BigDecimal.class),
                        row.get("reason", String.class),
                        resolvePersonName(
                                row.get("cashier_first_name", String.class),
                                row.get("cashier_user_name", String.class)
                        ),
                        combineRegisterLabel(
                                row.get("register_name", String.class),
                                row.get("agency_name", String.class)
                        ),
                        row.get("external_reference", String.class)
                ))
                .all();
    }

    private Mono<RegisterInfo> fetchRegisterInfo(String registerId) {
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT r.id, r.town AS name, r.town, r.country, r.agency_id, ");
        sql.append("ag.name AS agency_name, ag.organization_id, ");
        sql.append("org.name AS organization_name ");
        sql.append("FROM cash_register r ");
        sql.append("LEFT JOIN agency ag ON ag.id = r.agency_id ");
        sql.append("LEFT JOIN organization org ON org.id = ag.organization_id ");
        sql.append("WHERE r.id = :registerId");

        return entityTemplate.getDatabaseClient()
                .sql(sql.toString())
                .bind("registerId", registerId)
                .map((row, meta) -> new RegisterInfo(
                        row.get("id", String.class),
                        row.get("name", String.class),
                        row.get("town", String.class),
                        row.get("country", String.class),
                        row.get("agency_id", String.class),
                        row.get("agency_name", String.class),
                        row.get("organization_id", String.class),
                        row.get("organization_name", String.class)
                ))
                .one()
                .switchIfEmpty(Mono.error(new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Cash register not found."
                )));
    }

    private Flux<MovementRow> fetchRegisterMovements(
            String registerId,
            LocalDateTime startOn,
            LocalDateTime endOn,
            int limit
    ) {
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT m.create_on, m.sense, m.amount, m.reason, m.external_reference, ");
        sql.append("p.user_first_name AS cashier_first_name, ");
        sql.append("p.user_name AS cashier_user_name ");
        sql.append("FROM cash_register_movement m ");
        sql.append("LEFT JOIN person p ON p.id = m.create_by ");
        sql.append("LEFT JOIN cash_register_session s ON s.id = m.session_id ");
        sql.append("WHERE s.cash_register_id = :registerId ");
        sql.append("AND m.is_deleted = false ");
        if (startOn != null) {
            sql.append("AND m.create_on >= :startOn ");
        }
        if (endOn != null) {
            sql.append("AND m.create_on <= :endOn ");
        }
        sql.append("ORDER BY m.create_on DESC ");
        sql.append("LIMIT :limit");

        DatabaseClient.GenericExecuteSpec spec = entityTemplate.getDatabaseClient()
                .sql(sql.toString())
                .bind("registerId", registerId)
                .bind("limit", limit);
        if (startOn != null) {
            spec = spec.bind("startOn", startOn);
        }
        if (endOn != null) {
            spec = spec.bind("endOn", endOn);
        }
        return spec.map((row, meta) -> new MovementRow(
                        row.get("create_on", LocalDateTime.class),
                        row.get("sense", String.class),
                        row.get("amount", BigDecimal.class),
                        row.get("reason", String.class),
                        resolvePersonName(
                                row.get("cashier_first_name", String.class),
                                row.get("cashier_user_name", String.class)
                        ),
                        row.get("external_reference", String.class)
                ))
                .all();
    }

    private Mono<SessionInfo> fetchSessionInfo(String sessionId) {
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT s.id, s.state, s.open_on, s.close_on, ");
        sql.append("r.id AS register_id, r.town AS register_name, ");
        sql.append("r.town AS register_town, r.country AS register_country, ");
        sql.append("ag.id AS agency_id, ag.name AS agency_name, ");
        sql.append("org.id AS organization_id, org.name AS organization_name, ");
        sql.append("op.user_first_name AS opener_first_name, ");
        sql.append("op.user_name AS opener_user_name ");
        sql.append("FROM cash_register_session s ");
        sql.append("LEFT JOIN cash_register r ON r.id = s.cash_register_id ");
        sql.append("LEFT JOIN agency ag ON ag.id = r.agency_id ");
        sql.append("LEFT JOIN organization org ON org.id = ag.organization_id ");
        sql.append("LEFT JOIN person op ON op.id = s.open_by ");
        sql.append("WHERE s.id = :sessionId");

        return entityTemplate.getDatabaseClient()
                .sql(sql.toString())
                .bind("sessionId", sessionId)
                .map((row, meta) -> new SessionInfo(
                        row.get("id", String.class),
                        row.get("state", String.class),
                        row.get("open_on", LocalDateTime.class),
                        row.get("close_on", LocalDateTime.class),
                        resolvePersonName(
                                row.get("opener_first_name", String.class),
                                row.get("opener_user_name", String.class)
                        ),
                        row.get("register_name", String.class),
                        row.get("register_town", String.class),
                        row.get("register_country", String.class),
                        row.get("agency_id", String.class),
                        row.get("agency_name", String.class),
                        row.get("organization_id", String.class),
                        row.get("organization_name", String.class)
                ))
                .one()
                .switchIfEmpty(Mono.error(new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Session not found."
                )));
    }

    private Flux<MovementRow> fetchSessionMovements(String sessionId, int limit) {
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT m.create_on, m.sense, m.amount, m.reason, m.external_reference, ");
        sql.append("p.user_first_name AS cashier_first_name, ");
        sql.append("p.user_name AS cashier_user_name ");
        sql.append("FROM cash_register_movement m ");
        sql.append("LEFT JOIN person p ON p.id = m.create_by ");
        sql.append("WHERE m.session_id = :sessionId ");
        sql.append("AND m.is_deleted = false ");
        sql.append("ORDER BY m.create_on DESC ");
        sql.append("LIMIT :limit");

        return entityTemplate.getDatabaseClient()
                .sql(sql.toString())
                .bind("sessionId", sessionId)
                .bind("limit", limit)
                .map((row, meta) -> new MovementRow(
                        row.get("create_on", LocalDateTime.class),
                        row.get("sense", String.class),
                        row.get("amount", BigDecimal.class),
                        row.get("reason", String.class),
                        resolvePersonName(
                                row.get("cashier_first_name", String.class),
                                row.get("cashier_user_name", String.class)
                        ),
                        row.get("external_reference", String.class)
                ))
                .all();
    }

    private Mono<ReconciliationRow> fetchReconciliation(String sessionId) {
        String sql = "SELECT theorical_total, physical_total, difference "
                + "FROM cash_reconciliation WHERE session_id = :sessionId";
        return entityTemplate.getDatabaseClient()
                .sql(sql)
                .bind("sessionId", sessionId)
                .map((row, meta) -> new ReconciliationRow(
                        row.get("theorical_total", BigDecimal.class),
                        row.get("physical_total", BigDecimal.class),
                        row.get("difference", BigDecimal.class)
                ))
                .one();
    }

    private Flux<AuditRow> fetchAuditRows(
            String type,
            LocalDateTime startOn,
            LocalDateTime endOn,
            int limit,
            String organizationId,
            String agencyId
    ) {
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT e.date_time, e.type, e.payload, e.subject_type, e.subject_id, ");
        sql.append("p.user_first_name AS author_first_name, ");
        sql.append("p.user_name AS author_user_name ");
        sql.append("FROM cash_register_event e ");
        sql.append("LEFT JOIN person p ON p.id = e.author_id ");
        sql.append("LEFT JOIN admin_profile ap ON ap.person_id = e.author_id ");
        sql.append("LEFT JOIN agency ag_sub ON e.subject_type = 'agency' AND e.subject_id = ag_sub.id ");
        sql.append("WHERE 1=1 ");
        if (StringUtils.hasText(agencyId)) {
            sql.append("AND (ap.agency_id = :agencyId ");
            sql.append("OR (e.subject_type = 'agency' AND e.subject_id = :agencyId)) ");
            sql.append("AND (ap.organization_id = :organizationId ");
            sql.append("OR ag_sub.organization_id = :organizationId ");
            sql.append("OR (e.subject_type = 'organization' AND e.subject_id = :organizationId)) ");
        } else {
            sql.append("AND (ap.organization_id = :organizationId ");
            sql.append("OR (e.subject_type = 'organization' AND e.subject_id = :organizationId) ");
            sql.append("OR (e.subject_type = 'agency' AND ag_sub.organization_id = :organizationId)) ");
        }
        if (StringUtils.hasText(type)) {
            sql.append("AND e.type = :type ");
        }
        if (startOn != null) {
            sql.append("AND e.date_time >= :startOn ");
        }
        if (endOn != null) {
            sql.append("AND e.date_time <= :endOn ");
        }
        sql.append("ORDER BY e.date_time DESC ");
        sql.append("LIMIT :limit");

        DatabaseClient.GenericExecuteSpec spec = entityTemplate.getDatabaseClient()
                .sql(sql.toString())
                .bind("organizationId", organizationId)
                .bind("limit", limit);
        if (StringUtils.hasText(agencyId)) {
            spec = spec.bind("agencyId", agencyId);
        }
        if (StringUtils.hasText(type)) {
            spec = spec.bind("type", type);
        }
        if (startOn != null) {
            spec = spec.bind("startOn", startOn);
        }
        if (endOn != null) {
            spec = spec.bind("endOn", endOn);
        }

        return spec.map((row, meta) -> new AuditRow(
                        row.get("date_time", LocalDateTime.class),
                        row.get("type", String.class),
                        resolvePersonName(
                                row.get("author_first_name", String.class),
                                row.get("author_user_name", String.class)
                        ),
                        row.get("payload", String.class),
                        row.get("subject_type", String.class),
                        row.get("subject_id", String.class)
                ))
                .all();
    }

    private byte[] buildTransactionsPdf(
            ReportHeader header,
            List<TransactionRow> rows,
            int limit,
            LocalDateTime startOn,
            LocalDateTime endOn,
            String type,
            String registerId,
            String cashierId
    ) {
        try (PdfReportBuilder builder = new PdfReportBuilder()) {
            builder.addTitle("Transactions Report");
            builder.addSubtitle(buildHeaderLine(header));
            builder.addSubtitle("Generated on: " + formatDateTime(LocalDateTime.now()));
            builder.addSubtitle("Showing latest " + limit + " movements.");
            if (startOn != null || endOn != null) {
                builder.addSubtitle("Period: " + formatDate(startOn) + " - " + formatDate(endOn));
            }
            if (StringUtils.hasText(type)) {
                builder.addSubtitle("Type: " + type);
            }
            if (StringUtils.hasText(registerId)) {
                builder.addSubtitle("Register: " + registerId);
            }
            if (StringUtils.hasText(cashierId)) {
                builder.addSubtitle("Cashier: " + cashierId);
            }
            builder.addDivider();
            builder.addSectionTitle("Transactions");

            String[] headers = new String[] {"Date", "Sense", "Amount", "Reason", "Cashier", "Register", "Ref"};
            float[] widths = new float[] {70f, 40f, 55f, 140f, 70f, 80f, 60f};
            List<String[]> tableRows = new ArrayList<>();
            for (TransactionRow row : rows) {
                tableRows.add(new String[] {
                        formatDateTime(row.createdOn()),
                        normalizeSense(row.sense()),
                        formatAmount(row.amount()),
                        safe(row.reason()),
                        safe(row.cashierName()),
                        safe(row.registerName()),
                        safe(row.externalReference())
                });
            }
            builder.addTable(headers, widths, tableRows);
            builder.addDivider();
            builder.addTextLine("Total entries: " + formatAmount(sumAmountsTransactions(rows, "entree")) + " XAF");
            builder.addTextLine("Total exits: " + formatAmount(sumAmountsTransactions(rows, "sortie")) + " XAF");
            return builder.toBytes();
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to generate report.", ex);
        }
    }

    private byte[] buildRegisterPdf(
            ReportHeader header,
            RegisterInfo info,
            List<MovementRow> movements,
            LocalDateTime startOn,
            LocalDateTime endOn,
            int limit
    ) {
        try (PdfReportBuilder builder = new PdfReportBuilder()) {
            builder.addTitle("Cash Register Report");
            builder.addSubtitle(buildHeaderLine(header));
            builder.addSubtitle("Generated on: " + formatDateTime(LocalDateTime.now()));
            builder.addSubtitle("Showing latest " + limit + " movements.");
            builder.addDivider();
            builder.addSectionTitle("Register Details");
            builder.addTextLine("Register: " + safe(info.name()));
            builder.addTextLine("Location: " + safe(info.town()) + ", " + safe(info.country()));
            builder.addTextLine("Agency: " + safe(info.agencyName()));
            if (startOn != null || endOn != null) {
                builder.addTextLine("Period: " + formatDate(startOn) + " - " + formatDate(endOn));
            }
            builder.addDivider();
            builder.addSectionTitle("Movements");

            String[] headers = new String[] {"Date", "Sense", "Amount", "Reason", "Cashier", "Ref"};
            float[] widths = new float[] {75f, 40f, 55f, 160f, 90f, 75f};
            List<String[]> tableRows = new ArrayList<>();
            for (MovementRow row : movements) {
                tableRows.add(new String[] {
                        formatDateTime(row.createdOn()),
                        normalizeSense(row.sense()),
                        formatAmount(row.amount()),
                        safe(row.reason()),
                        safe(row.cashierName()),
                        safe(row.externalReference())
                });
            }
            builder.addTable(headers, widths, tableRows);
            builder.addDivider();
            builder.addTextLine("Total entries: " + formatAmount(sumAmounts(movements, "entree")) + " XAF");
            builder.addTextLine("Total exits: " + formatAmount(sumAmounts(movements, "sortie")) + " XAF");
            return builder.toBytes();
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to generate report.", ex);
        }
    }

    private byte[] buildSessionPdf(
            ReportHeader header,
            SessionInfo info,
            List<MovementRow> movements,
            ReconciliationRow reconciliation,
            int limit
    ) {
        try (PdfReportBuilder builder = new PdfReportBuilder()) {
            builder.addTitle("Session Report");
            builder.addSubtitle(buildHeaderLine(header));
            builder.addSubtitle("Generated on: " + formatDateTime(LocalDateTime.now()));
            builder.addSubtitle("Showing latest " + limit + " movements.");
            builder.addDivider();
            builder.addSectionTitle("Session Details");
            builder.addTextLine("Session ID: " + safe(info.id()));
            builder.addTextLine("State: " + safe(info.state()));
            builder.addTextLine("Opened on: " + formatDateTime(info.openOn()));
            builder.addTextLine("Closed on: " + formatDateTime(info.closeOn()));
            builder.addTextLine("Opener: " + safe(info.openerName()));
            builder.addTextLine("Register: " + safe(info.registerName()));
            builder.addTextLine("Agency: " + safe(info.agencyName()));
            builder.addDivider();
            builder.addSectionTitle("Movements");

            String[] headers = new String[] {"Date", "Sense", "Amount", "Reason", "Cashier", "Ref"};
            float[] widths = new float[] {75f, 40f, 55f, 160f, 90f, 75f};
            List<String[]> tableRows = new ArrayList<>();
            for (MovementRow row : movements) {
                tableRows.add(new String[] {
                        formatDateTime(row.createdOn()),
                        normalizeSense(row.sense()),
                        formatAmount(row.amount()),
                        safe(row.reason()),
                        safe(row.cashierName()),
                        safe(row.externalReference())
                });
            }
            builder.addTable(headers, widths, tableRows);
            builder.addDivider();
            builder.addTextLine("Total entries: " + formatAmount(sumAmounts(movements, "entree")) + " XAF");
            builder.addTextLine("Total exits: " + formatAmount(sumAmounts(movements, "sortie")) + " XAF");
            if (reconciliation != null) {
                builder.addDivider();
                builder.addSectionTitle("Reconciliation");
                builder.addTextLine("Theorical: " + formatAmount(reconciliation.theoricalTotal()) + " XAF");
                builder.addTextLine("Physical: " + formatAmount(reconciliation.physicalTotal()) + " XAF");
                builder.addTextLine("Difference: " + formatAmount(reconciliation.difference()) + " XAF");
            }
            return builder.toBytes();
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to generate report.", ex);
        }
    }

    private byte[] buildAuditPdf(
            ReportHeader header,
            List<AuditRow> rows,
            String type,
            LocalDateTime startOn,
            LocalDateTime endOn,
            int limit
    ) {
        try (PdfReportBuilder builder = new PdfReportBuilder()) {
            builder.addTitle("Audit Report");
            builder.addSubtitle(buildHeaderLine(header));
            builder.addSubtitle("Generated on: " + formatDateTime(LocalDateTime.now()));
            builder.addSubtitle("Showing latest " + limit + " events.");
            if (StringUtils.hasText(type) || startOn != null || endOn != null) {
                builder.addSubtitle("Filters: " + buildAuditFilters(type, startOn, endOn));
            }
            builder.addDivider();
            builder.addSectionTitle("Audit Events");

            String[] headers = new String[] {"Date", "Type", "Author", "Subject", "Payload"};
            float[] widths = new float[] {85f, 60f, 80f, 80f, 210f};
            List<String[]> tableRows = new ArrayList<>();
            for (AuditRow row : rows) {
                tableRows.add(new String[] {
                        formatDateTime(row.dateTime()),
                        safe(row.type()),
                        safe(row.author()),
                        safe(buildSubjectLabel(row.subjectType(), row.subjectId())),
                        safe(truncate(row.payload(), 140))
                });
            }
            builder.addTable(headers, widths, tableRows);
            return builder.toBytes();
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to generate report.", ex);
        }
    }

    private String buildAuditFilters(String type, LocalDateTime startOn, LocalDateTime endOn) {
        List<String> parts = new ArrayList<>();
        if (StringUtils.hasText(type)) {
            parts.add("type=" + type.trim());
        }
        if (startOn != null) {
            parts.add("from=" + formatDateTime(startOn));
        }
        if (endOn != null) {
            parts.add("to=" + formatDateTime(endOn));
        }
        return String.join(", ", parts);
    }

    private BigDecimal sumAmounts(List<MovementRow> movements, String sense) {
        BigDecimal total = BigDecimal.ZERO;
        for (MovementRow row : movements) {
            if (row.amount() == null) {
                continue;
            }
            if (StringUtils.hasText(row.sense()) && row.sense().trim().equalsIgnoreCase(sense)) {
                total = total.add(row.amount());
            }
        }
        return total;
    }

    private BigDecimal sumAmountsTransactions(List<TransactionRow> rows, String sense) {
        BigDecimal total = BigDecimal.ZERO;
        for (TransactionRow row : rows) {
            if (row.amount() == null) {
                continue;
            }
            if (StringUtils.hasText(row.sense()) && row.sense().trim().equalsIgnoreCase(sense)) {
                total = total.add(row.amount());
            }
        }
        return total;
    }

    private String resolvePersonName(String firstName, String userName) {
        if (StringUtils.hasText(firstName)) {
            return firstName.trim();
        }
        if (StringUtils.hasText(userName)) {
            return userName.trim();
        }
        return null;
    }

    private String combineRegisterLabel(String registerName, String agencyName) {
        String resolvedRegister = trimToNull(registerName);
        String resolvedAgency = trimToNull(agencyName);
        if (resolvedRegister != null && resolvedAgency != null) {
            return resolvedRegister + " / " + resolvedAgency;
        }
        if (resolvedRegister != null) {
            return resolvedRegister;
        }
        return resolvedAgency;
    }

    private String buildSubjectLabel(String subjectType, String subjectId) {
        if (StringUtils.hasText(subjectType) && StringUtils.hasText(subjectId)) {
            return subjectType.trim() + ":" + subjectId.trim();
        }
        if (StringUtils.hasText(subjectType)) {
            return subjectType.trim();
        }
        return subjectId;
    }

    private String normalizeSense(String sense) {
        if (!StringUtils.hasText(sense)) {
            return null;
        }
        String normalized = sense.trim().toLowerCase(Locale.ROOT);
        if ("entree".equals(normalized)) {
            return "entry";
        }
        if ("sortie".equals(normalized)) {
            return "exit";
        }
        return normalized;
    }

    private int clampLimit(Integer limit, int defaultLimit, int maxLimit) {
        int resolved = limit != null && limit > 0 ? limit : defaultLimit;
        return Math.min(resolved, maxLimit);
    }

    private LocalDateTime parseStartDateTime(String value) {
        return parseDateTime(value, false);
    }

    private LocalDateTime parseEndDateTime(String value) {
        return parseDateTime(value, true);
    }

    private LocalDateTime parseDateTime(String value, boolean endOfDay) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String trimmed = value.trim();
        try {
            if (trimmed.contains("T")) {
                if (trimmed.endsWith("Z")) {
                    Instant instant = Instant.parse(trimmed);
                    return LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
                }
                return LocalDateTime.parse(trimmed);
            }
            LocalDate date = LocalDate.parse(trimmed);
            LocalTime time = endOfDay ? LocalTime.of(23, 59, 59) : LocalTime.MIN;
            return LocalDateTime.of(date, time);
        } catch (DateTimeParseException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid date format.", ex);
        }
    }

    private String formatDateTime(LocalDateTime value) {
        if (value == null) {
            return "-";
        }
        return DATE_TIME_FORMAT.format(value);
    }

    private String formatDate(LocalDateTime value) {
        if (value == null) {
            return "-";
        }
        return DATE_FORMAT.format(value);
    }

    private void putIfPresent(Map<String, Object> payload, String key, String value) {
        if (StringUtils.hasText(value)) {
            payload.put(key, value);
        }
    }

    private String formatAmount(BigDecimal amount) {
        if (amount == null) {
            return "0";
        }
        return AMOUNT_FORMAT.format(amount);
    }

    private String safe(String value) {
        if (!StringUtils.hasText(value)) {
            return "-";
        }
        return value.trim();
    }

    private String truncate(String value, int max) {
        if (!StringUtils.hasText(value)) {
            return value;
        }
        String trimmed = value.trim();
        if (trimmed.length() <= max) {
            return trimmed;
        }
        return trimmed.substring(0, Math.max(0, max - 3)) + "...";
    }

    private String buildHeaderLine(ReportHeader header) {
        String orgName = safe(header.organizationName());
        String agencyName = safe(header.agencyName());
        return "Organization: " + orgName + " | Agency: " + agencyName;
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private record ReportHeader(String organizationName, String agencyName) {
    }

    private record TransactionRow(
            LocalDateTime createdOn,
            String sense,
            BigDecimal amount,
            String reason,
            String cashierName,
            String registerName,
            String externalReference
    ) {
    }

    private record RegisterInfo(
            String id,
            String name,
            String town,
            String country,
            String agencyId,
            String agencyName,
            String organizationId,
            String organizationName
    ) {
    }

    private record SessionInfo(
            String id,
            String state,
            LocalDateTime openOn,
            LocalDateTime closeOn,
            String openerName,
            String registerName,
            String registerTown,
            String registerCountry,
            String agencyId,
            String agencyName,
            String organizationId,
            String organizationName
    ) {
    }

    private record MovementRow(
            LocalDateTime createdOn,
            String sense,
            BigDecimal amount,
            String reason,
            String cashierName,
            String externalReference
    ) {
    }

    private record ReconciliationRow(
            BigDecimal theoricalTotal,
            BigDecimal physicalTotal,
            BigDecimal difference
    ) {
    }

    private record AuditRow(
            LocalDateTime dateTime,
            String type,
            String author,
            String payload,
            String subjectType,
            String subjectId
    ) {
    }

    private static final class PdfReportBuilder implements AutoCloseable {
        private final PDDocument document;
        private PDPage page;
        private PDPageContentStream contentStream;
        private float cursorY;
        private int pageNumber;

        private PdfReportBuilder() throws IOException {
            this.document = new PDDocument();
            newPage();
        }

        private void addTitle(String title) throws IOException {
            writeLine(title, PDType1Font.HELVETICA_BOLD, TITLE_FONT_SIZE);
        }

        private void addSubtitle(String subtitle) throws IOException {
            writeLine(subtitle, PDType1Font.HELVETICA, SUBTITLE_FONT_SIZE);
        }

        private void addSectionTitle(String title) throws IOException {
            addSpacing(6f);
            writeLine(title, PDType1Font.HELVETICA_BOLD, HEADER_FONT_SIZE);
        }

        private void addTextLine(String text) throws IOException {
            writeLine(text, PDType1Font.HELVETICA, SUBTITLE_FONT_SIZE);
        }

        private void addDivider() throws IOException {
            addSpacing(4f);
            float width = page.getMediaBox().getWidth() - (2 * PAGE_MARGIN);
            ensureSpace(6f);
            contentStream.setStrokingColor(180);
            contentStream.moveTo(PAGE_MARGIN, cursorY);
            contentStream.lineTo(PAGE_MARGIN + width, cursorY);
            contentStream.stroke();
            cursorY -= 8f;
        }

        private void addTable(String[] headers, float[] widths, List<String[]> rows) throws IOException {
            if (headers == null || widths == null || headers.length != widths.length) {
                throw new IOException("Invalid table configuration.");
            }
            if (rows == null) {
                rows = List.of();
            }
            addSpacing(4f);
            float rowHeight = TABLE_FONT_SIZE + 4f;
            ensureSpace(rowHeight + 8f);
            drawRow(headers, widths, PDType1Font.HELVETICA_BOLD, TABLE_FONT_SIZE);
            addDivider();
            if (rows.isEmpty()) {
                addTextLine("No data available.");
                return;
            }
            for (String[] row : rows) {
                if (ensureSpace(rowHeight)) {
                    drawRow(headers, widths, PDType1Font.HELVETICA_BOLD, TABLE_FONT_SIZE);
                    addDivider();
                }
                drawRow(row, widths, PDType1Font.HELVETICA, TABLE_FONT_SIZE);
            }
        }

        private void drawRow(String[] columns, float[] widths, PDType1Font font, float size) throws IOException {
            float rowHeight = size + 4f;
            float startX = PAGE_MARGIN;
            for (int i = 0; i < widths.length; i++) {
                String value = i < columns.length ? columns[i] : "";
                String truncated = truncateToWidth(value, font, size, widths[i] - 2f);
                writeTextAt(startX, cursorY, truncated, font, size);
                startX += widths[i];
            }
            cursorY -= rowHeight;
        }

        private void writeLine(String text, PDType1Font font, float size) throws IOException {
            ensureSpace(size + 6f);
            writeTextAt(PAGE_MARGIN, cursorY, text, font, size);
            cursorY -= size + 6f;
        }

        private void addSpacing(float space) {
            cursorY -= space;
        }

        private void writeTextAt(float x, float y, String text, PDType1Font font, float size) throws IOException {
            contentStream.beginText();
            contentStream.setFont(font, size);
            contentStream.newLineAtOffset(x, y);
            contentStream.showText(sanitize(text));
            contentStream.endText();
        }

        private boolean ensureSpace(float required) throws IOException {
            if (cursorY - required < PAGE_MARGIN + FOOTER_HEIGHT) {
                newPage();
                return true;
            }
            return false;
        }

        private void newPage() throws IOException {
            if (contentStream != null) {
                drawFooter();
                contentStream.close();
            }
            page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            contentStream = new PDPageContentStream(document, page);
            pageNumber++;
            cursorY = page.getMediaBox().getHeight() - PAGE_MARGIN;
            drawHeader();
        }

        private byte[] toBytes() throws IOException {
            if (contentStream != null) {
                drawFooter();
                contentStream.close();
                contentStream = null;
            }
            try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                document.save(output);
                return output.toByteArray();
            }
        }

        @Override
        public void close() throws IOException {
            if (contentStream != null) {
                drawFooter();
                contentStream.close();
            }
            document.close();
        }

        private void drawHeader() throws IOException {
            float pageWidth = page.getMediaBox().getWidth();
            float startY = cursorY;
            float logoY = startY - LOGO_SIZE + 2f;
            contentStream.setNonStrokingColor(20, 60, 90);
            contentStream.addRect(PAGE_MARGIN, logoY, LOGO_SIZE, LOGO_SIZE);
            contentStream.fill();
            contentStream.setNonStrokingColor(0);

            float textX = PAGE_MARGIN + LOGO_SIZE + 6f;
            writeTextAt(textX, startY, "ERP Cashier", PDType1Font.HELVETICA_BOLD, HEADER_FONT_SIZE);
            String right = "Confidential";
            float rightWidth = textWidth(PDType1Font.HELVETICA, SUBTITLE_FONT_SIZE, right);
            writeTextAt(pageWidth - PAGE_MARGIN - rightWidth, startY, right, PDType1Font.HELVETICA, SUBTITLE_FONT_SIZE);

            cursorY = startY - HEADER_HEIGHT;
            contentStream.setStrokingColor(180);
            contentStream.moveTo(PAGE_MARGIN, cursorY);
            contentStream.lineTo(pageWidth - PAGE_MARGIN, cursorY);
            contentStream.stroke();
            cursorY -= 8f;
        }

        private void drawFooter() throws IOException {
            float pageWidth = page.getMediaBox().getWidth();
            String footer = "Page " + pageNumber;
            float footerWidth = textWidth(PDType1Font.HELVETICA, 8f, footer);
            writeTextAt(pageWidth - PAGE_MARGIN - footerWidth, PAGE_MARGIN - 12f, footer, PDType1Font.HELVETICA, 8f);
        }

        private float textWidth(PDType1Font font, float size, String text) throws IOException {
            if (!StringUtils.hasText(text)) {
                return 0f;
            }
            return font.getStringWidth(text) / 1000f * size;
        }

        private String truncateToWidth(String text, PDType1Font font, float size, float maxWidth) {
            if (!StringUtils.hasText(text)) {
                return "";
            }
            String value = text.trim();
            try {
                float textWidth = font.getStringWidth(value) / 1000f * size;
                if (textWidth <= maxWidth) {
                    return value;
                }
                String ellipsis = "...";
                float ellipsisWidth = font.getStringWidth(ellipsis) / 1000f * size;
                int end = value.length();
                while (end > 0) {
                    String candidate = value.substring(0, end);
                    float width = font.getStringWidth(candidate) / 1000f * size;
                    if (width + ellipsisWidth <= maxWidth) {
                        return candidate + ellipsis;
                    }
                    end--;
                }
                return value.substring(0, 1);
            } catch (IOException ex) {
                return value;
            }
        }

        private String sanitize(String text) {
            if (!StringUtils.hasText(text)) {
                return "";
            }
            StringBuilder builder = new StringBuilder();
            for (int i = 0; i < text.length(); i++) {
                char ch = text.charAt(i);
                builder.append(ch <= 0x7F ? ch : '?');
            }
            return builder.toString();
        }
    }
}
