package com.erp.cashier.service;

import com.erp.cashier.dto.CashRegisterSummaryResponse;
import com.erp.cashier.dto.ReconciliationInfoResponse;
import com.erp.cashier.dto.ReconciliationJustifyRequest;
import com.erp.cashier.dto.ReconciliationResponse;
import com.erp.cashier.dto.ReconciliationReviewRequest;
import com.erp.cashier.dto.ReconciliationSessionResponse;
import com.erp.cashier.dto.ReconciliationUserResponse;
import io.r2dbc.spi.Row;
import io.r2dbc.spi.RowMetadata;
import java.time.LocalDateTime;
import org.springframework.http.HttpStatus;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Service for reconciliation workflows.
 *
 * @author ERP Cashier Team
 * @since 2025-01-20
 */
@Service
public class ReconciliationService {
    private final R2dbcEntityTemplate entityTemplate;

    /**
     * Creates the reconciliation service.
     *
     * @param entityTemplate entity template
     */
    public ReconciliationService(R2dbcEntityTemplate entityTemplate) {
        this.entityTemplate = entityTemplate;
    }

    /**
     * Lists all reconciliations for admins.
     *
     * @return reconciliations
     */
    public Flux<ReconciliationResponse> listAdminReconciliations() {
        String sql = baseSelect() + "ORDER BY rec.create_on DESC";
        return entityTemplate.getDatabaseClient()
                .sql(sql)
                .map(this::mapRow)
                .all();
    }

    /**
     * Lists reconciliations for a cashier.
     *
     * @param cashierId cashier identifier
     * @return reconciliations
     */
    public Flux<ReconciliationResponse> listCashierReconciliations(String cashierId) {
        if (!StringUtils.hasText(cashierId)) {
            return Flux.error(new ResponseStatusException(HttpStatus.FORBIDDEN, "Cashier scope is required."));
        }
        String sql = baseSelect() + "WHERE s.open_by = :cashierId ORDER BY rec.create_on DESC";
        return entityTemplate.getDatabaseClient()
                .sql(sql)
                .bind("cashierId", cashierId)
                .map(this::mapRow)
                .all();
    }

    /**
     * Reviews a reconciliation as an admin.
     *
     * @param reconciliationId reconciliation identifier
     * @param request review request
     * @param adminId admin identifier
     * @return updated reconciliation
     */
    public Mono<ReconciliationResponse> reviewReconciliation(
            String reconciliationId,
            ReconciliationReviewRequest request,
            String adminId
    ) {
        String resolvedId = trimToNull(reconciliationId);
        if (!StringUtils.hasText(resolvedId)) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "reconciliation id is required"));
        }
        String action = request != null ? trimToNull(request.getAction()) : null;
        if (!StringUtils.hasText(action)
                || (!"valide".equalsIgnoreCase(action) && !"rejete".equalsIgnoreCase(action))) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "action must be valide or rejete"));
        }
        String comment = request != null ? trimToNull(request.getAdminComment()) : null;

        StringBuilder sql = new StringBuilder();
        sql.append("UPDATE cash_reconciliation ");
        sql.append("SET statut = :statut, check_on = :checkOn, check_by = :checkBy ");
        if (StringUtils.hasText(comment)) {
            sql.append(", justification = :comment ");
        }
        sql.append("WHERE id = :id");

        DatabaseClient.GenericExecuteSpec spec = entityTemplate.getDatabaseClient()
                .sql(sql.toString())
                .bind("statut", action.toLowerCase())
                .bind("checkOn", LocalDateTime.now())
                .bind("checkBy", trimToNull(adminId))
                .bind("id", resolvedId);
        if (StringUtils.hasText(comment)) {
            spec = spec.bind("comment", comment);
        }

        return spec.fetch()
                .rowsUpdated()
                .flatMap(rows -> {
                    if (rows == 0) {
                        return Mono.error(new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Reconciliation not found."
                        ));
                    }
                    return findById(resolvedId);
                });
    }

    /**
     * Justifies a reconciliation as a cashier.
     *
     * @param reconciliationId reconciliation identifier
     * @param request justify request
     * @return updated reconciliation
     */
    public Mono<ReconciliationResponse> justifyReconciliation(
            String reconciliationId,
            ReconciliationJustifyRequest request
    ) {
        String resolvedId = trimToNull(reconciliationId);
        if (!StringUtils.hasText(resolvedId)) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "reconciliation id is required"));
        }
        String justification = request != null ? trimToNull(request.getJustification()) : null;
        if (!StringUtils.hasText(justification)) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "justification is required"));
        }

        String sql = "UPDATE cash_reconciliation SET justification = :justification, statut = :statut WHERE id = :id";
        return entityTemplate.getDatabaseClient()
                .sql(sql)
                .bind("justification", justification)
                .bind("statut", "justifie")
                .bind("id", resolvedId)
                .fetch()
                .rowsUpdated()
                .flatMap(rows -> {
                    if (rows == 0) {
                        return Mono.error(new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Reconciliation not found."
                        ));
                    }
                    return findById(resolvedId);
                });
    }

    private Mono<ReconciliationResponse> findById(String reconciliationId) {
        String sql = baseSelect() + "WHERE rec.id = :id";
        return entityTemplate.getDatabaseClient()
                .sql(sql)
                .bind("id", reconciliationId)
                .map(this::mapRow)
                .one()
                .switchIfEmpty(Mono.error(new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Reconciliation not found."
                )));
    }

    private String baseSelect() {
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT rec.id AS reconciliation_id, rec.physical_total, rec.theorical_total, ");
        sql.append("rec.difference, rec.statut, rec.justification, rec.create_on, rec.check_on, ");
        sql.append("s.id AS session_id, s.state, s.open_on, s.close_on, ");
        sql.append("r.id AS register_id, r.town AS register_town, r.country AS register_country, ");
        sql.append("r.neighborhood AS register_neighborhood, ");
        sql.append("op.id AS opener_id, op.user_name AS opener_user_name, ");
        sql.append("op.user_first_name AS opener_user_first_name, ");
        sql.append("cl.id AS closer_id, cl.user_name AS closer_user_name, ");
        sql.append("cl.user_first_name AS closer_user_first_name, ");
        sql.append("creator.id AS creator_id, creator.user_name AS creator_user_name, ");
        sql.append("creator.user_first_name AS creator_user_first_name ");
        sql.append("FROM cash_reconciliation rec ");
        sql.append("LEFT JOIN cash_register_session s ON s.id = rec.session_id ");
        sql.append("LEFT JOIN cash_register r ON r.id = s.cash_register_id ");
        sql.append("LEFT JOIN person op ON op.id = s.open_by ");
        sql.append("LEFT JOIN person cl ON cl.id = s.close_by ");
        sql.append("LEFT JOIN person creator ON creator.id = rec.create_by ");
        return sql.toString();
    }

    private ReconciliationResponse mapRow(Row row, RowMetadata metadata) {
        ReconciliationInfoResponse info = new ReconciliationInfoResponse(
                row.get("reconciliation_id", String.class),
                row.get("physical_total", java.math.BigDecimal.class),
                row.get("theorical_total", java.math.BigDecimal.class),
                row.get("difference", java.math.BigDecimal.class),
                row.get("statut", String.class),
                row.get("justification", String.class),
                row.get("create_on", LocalDateTime.class),
                row.get("check_on", LocalDateTime.class)
        );
        ReconciliationSessionResponse session = new ReconciliationSessionResponse(
                row.get("session_id", String.class),
                row.get("state", String.class),
                row.get("open_on", LocalDateTime.class),
                row.get("close_on", LocalDateTime.class)
        );
        CashRegisterSummaryResponse register = new CashRegisterSummaryResponse(
                row.get("register_id", String.class),
                row.get("register_town", String.class),
                row.get("register_country", String.class),
                row.get("register_neighborhood", String.class)
        );
        return new ReconciliationResponse(
                info,
                session,
                register,
                mapUser(row, "opener"),
                mapUser(row, "closer"),
                mapUser(row, "creator")
        );
    }

    private ReconciliationUserResponse mapUser(Row row, String prefix) {
        String id = row.get(prefix + "_id", String.class);
        String userName = row.get(prefix + "_user_name", String.class);
        String userFirstName = row.get(prefix + "_user_first_name", String.class);
        if (!StringUtils.hasText(id) && !StringUtils.hasText(userName) && !StringUtils.hasText(userFirstName)) {
            return null;
        }
        return new ReconciliationUserResponse(id, userName, userFirstName);
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }
}
