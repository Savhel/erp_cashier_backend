package com.erp.cashier.service;

import com.erp.cashier.dto.AdminAssignmentResponse;
import com.erp.cashier.dto.AgencySummaryResponse;
import com.erp.cashier.dto.CashRegisterSummaryResponse;
import com.erp.cashier.dto.CashierAgencyAssignmentRequest;
import com.erp.cashier.dto.CashierAgencyAssignmentResponse;
import com.erp.cashier.dto.PersonSummaryResponse;
import com.erp.cashier.model.CashierAgencyAssignment;
import com.erp.cashier.repository.CashierAgencyAssignmentRepository;
import java.time.LocalDate;
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
 * Service for admin assignments.
 *
 * @author ERP Cashier Team
 * @since 2025-01-20
 */
@Service
public class AssignmentService {
    private final R2dbcEntityTemplate entityTemplate;
    private final CashierAgencyAssignmentRepository assignmentRepository;
    private final AuditService auditService;

    /**
     * Creates the assignment service.
     *
     * @param entityTemplate entity template
     * @param assignmentRepository assignment repository
     * @param auditService audit service
     */
    public AssignmentService(
            R2dbcEntityTemplate entityTemplate,
            CashierAgencyAssignmentRepository assignmentRepository,
            AuditService auditService
    ) {
        this.entityTemplate = entityTemplate;
        this.assignmentRepository = assignmentRepository;
        this.auditService = auditService;
    }

    /**
     * Lists register assignments.
     *
     * @param organizationId organization identifier
     * @param agencyId agency identifier
     * @param restrictToOrganization true when scoped to an organization
     * @param restrictToAgency true when scoped to an agency
     * @return assignments
     */
    public Flux<AdminAssignmentResponse> listRegisterAssignments(
            String organizationId,
            String agencyId,
            boolean restrictToOrganization,
            boolean restrictToAgency
    ) {
        if (restrictToOrganization && !StringUtils.hasText(organizationId)) {
            return Flux.error(new ResponseStatusException(HttpStatus.FORBIDDEN, "Organization scope is required."));
        }
        if (restrictToAgency && !StringUtils.hasText(agencyId)) {
            return Flux.error(new ResponseStatusException(HttpStatus.FORBIDDEN, "Agency scope is required."));
        }
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT cm.id, cm.day, ");
        sql.append("p.id AS person_id, p.user_name, p.user_first_name, ");
        sql.append("r.id AS register_id, r.town, r.country, r.neighborhood ");
        sql.append("FROM cashier_manage_cash_register cm ");
        sql.append("JOIN person p ON p.id = cm.user_id ");
        sql.append("JOIN cash_register r ON r.id = cm.cash_register_id ");
        sql.append("LEFT JOIN agency a ON a.id = r.agency_id ");
        sql.append("WHERE 1=1 ");
        if (restrictToOrganization) {
            sql.append("AND a.organization_id = :organizationId ");
        }
        if (restrictToAgency) {
            sql.append("AND a.id = :agencyId ");
        }
        sql.append("ORDER BY cm.day DESC");

        DatabaseClient.GenericExecuteSpec spec = entityTemplate.getDatabaseClient().sql(sql.toString());
        if (restrictToOrganization) {
            spec = spec.bind("organizationId", organizationId);
        }
        if (restrictToAgency) {
            spec = spec.bind("agencyId", agencyId);
        }
        return spec.map((row, meta) -> new AdminAssignmentResponse(
                        row.get("id", String.class),
                        row.get("day", LocalDateTime.class),
                        new PersonSummaryResponse(
                                row.get("person_id", String.class),
                                row.get("user_name", String.class),
                                row.get("user_first_name", String.class)
                        ),
                        new CashRegisterSummaryResponse(
                                row.get("register_id", String.class),
                                row.get("town", String.class),
                                row.get("country", String.class),
                                row.get("neighborhood", String.class)
                        )
                ))
                .all();
    }

    /**
     * Lists cashier agency assignments.
     *
     * @param organizationId organization identifier
     * @param agencyId agency identifier
     * @param restrictToOrganization true when scoped to an organization
     * @param restrictToAgency true when scoped to an agency
     * @return assignments
     */
    public Flux<CashierAgencyAssignmentResponse> listCashierAgencyAssignments(
            String organizationId,
            String agencyId,
            boolean restrictToOrganization,
            boolean restrictToAgency
    ) {
        if (restrictToOrganization && !StringUtils.hasText(organizationId)) {
            return Flux.error(new ResponseStatusException(HttpStatus.FORBIDDEN, "Organization scope is required."));
        }
        if (restrictToAgency && !StringUtils.hasText(agencyId)) {
            return Flux.error(new ResponseStatusException(HttpStatus.FORBIDDEN, "Agency scope is required."));
        }
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT ca.id, ca.start_on, ca.end_on, ca.assigned_on, ");
        sql.append("p.id AS person_id, p.user_name, p.user_first_name, ");
        sql.append("ag.id AS agency_id, ag.name AS agency_name, ag.country AS agency_country, ");
        sql.append("ag.town AS agency_town, ag.neighborhood AS agency_neighborhood, ");
        sql.append("ag.organization_id AS agency_organization_id ");
        sql.append("FROM cashier_agency_assignment ca ");
        sql.append("JOIN person p ON p.id = ca.cashier_id ");
        sql.append("JOIN agency ag ON ag.id = ca.agency_id ");
        sql.append("WHERE 1=1 ");
        if (restrictToOrganization) {
            sql.append("AND ag.organization_id = :organizationId ");
        }
        if (restrictToAgency) {
            sql.append("AND ag.id = :agencyId ");
        }
        sql.append("ORDER BY ca.assigned_on DESC");
        DatabaseClient.GenericExecuteSpec spec = entityTemplate.getDatabaseClient().sql(sql.toString());
        if (restrictToOrganization) {
            spec = spec.bind("organizationId", organizationId);
        }
        if (restrictToAgency) {
            spec = spec.bind("agencyId", agencyId);
        }
        return spec.map((row, meta) -> new CashierAgencyAssignmentResponse(
                        row.get("id", String.class),
                        new PersonSummaryResponse(
                                row.get("person_id", String.class),
                                row.get("user_name", String.class),
                                row.get("user_first_name", String.class)
                        ),
                        new AgencySummaryResponse(
                                row.get("agency_id", String.class),
                                row.get("agency_name", String.class),
                                row.get("agency_country", String.class),
                                row.get("agency_town", String.class),
                                row.get("agency_neighborhood", String.class),
                                row.get("agency_organization_id", String.class)
                        ),
                        row.get("start_on", LocalDateTime.class),
                        row.get("end_on", LocalDateTime.class),
                        row.get("assigned_on", LocalDateTime.class)
                ))
                .all();
    }

    /**
     * Creates a cashier agency assignment.
     *
     * @param request assignment request
     * @param assignedBy assigned by user identifier
     * @param organizationId organization identifier
     * @param agencyScopeId agency identifier when scoped
     * @param restrictToOrganization true when scoped to an organization
     * @param restrictToAgency true when scoped to an agency
     * @return created assignment
     */
    public Mono<CashierAgencyAssignmentResponse> createCashierAgencyAssignment(
            CashierAgencyAssignmentRequest request,
            String assignedBy,
            String organizationId,
            String agencyScopeId,
            boolean restrictToOrganization,
            boolean restrictToAgency
    ) {
        if (request == null) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Assignment payload is required."));
        }
        String cashierId = trimToNull(request.getCashierId());
        String agencyId = trimToNull(request.getAgencyId());
        if (!StringUtils.hasText(cashierId) || !StringUtils.hasText(agencyId)) {
            return Mono.error(new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "cashier_id and agency_id are required"
            ));
        }
        LocalDateTime startOn = parseDate(request.getStartOn());
        LocalDateTime endOn = parseDate(request.getEndOn());
        if (startOn != null && endOn != null && endOn.isBefore(startOn)) {
            return Mono.error(new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "end_on must be after or equal to start_on"
            ));
        }
        if (restrictToAgency && (!StringUtils.hasText(agencyScopeId) || !agencyScopeId.equals(agencyId))) {
            return Mono.error(new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Unauthorized for this agency."
            ));
        }

        CashierAgencyAssignment assignment = new CashierAgencyAssignment();
        assignment.setId(UUID.randomUUID().toString());
        assignment.setCashierId(cashierId);
        assignment.setAgencyId(agencyId);
        assignment.setStartOn(startOn);
        assignment.setEndOn(endOn);
        assignment.setAssignedOn(LocalDateTime.now());
        assignment.setAssignedBy(trimToNull(assignedBy));

        return assertAgencyScope(agencyId, organizationId, restrictToOrganization)
                .then(assertNoOverlappingAssignment(cashierId, startOn, endOn))
                .then(entityTemplate.insert(CashierAgencyAssignment.class).using(assignment))
                .then(recordAgencyAssignmentEvent("assignation_agence", assignment, assignedBy))
                .then(fetchAssignment(assignment.getId()));
    }

    /**
     * Ends a cashier agency assignment.
     *
     * @param assignmentId assignment identifier
     * @param actorId authenticated user identifier
     * @param organizationId organization identifier
     * @param agencyScopeId agency identifier when scoped
     * @param restrictToOrganization true when scoped to an organization
     * @param restrictToAgency true when scoped to an agency
     * @return updated assignment
     */
    public Mono<Void> endCashierAgencyAssignment(
            String assignmentId,
            String actorId,
            String organizationId,
            String agencyScopeId,
            boolean restrictToOrganization,
            boolean restrictToAgency
    ) {
        String resolvedId = trimToNull(assignmentId);
        if (!StringUtils.hasText(resolvedId)) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Assignment ID is required."));
        }
        return fetchAssignmentScope(resolvedId)
                .flatMap(scope -> {
                    if (restrictToOrganization && !StringUtils.hasText(organizationId)) {
                        return Mono.error(new ResponseStatusException(
                                HttpStatus.FORBIDDEN,
                                "Organization scope is required."
                        ));
                    }
                    if (restrictToOrganization && !organizationId.equals(scope.organizationId())) {
                        return Mono.error(new ResponseStatusException(
                                HttpStatus.FORBIDDEN,
                                "Unauthorized for this organization."
                        ));
                    }
                    if (restrictToAgency && (!StringUtils.hasText(agencyScopeId)
                            || !agencyScopeId.equals(scope.agencyId()))) {
                        return Mono.error(new ResponseStatusException(
                                HttpStatus.FORBIDDEN,
                                "Unauthorized for this agency."
                        ));
                    }
                    return assignmentRepository.findById(resolvedId)
                            .switchIfEmpty(Mono.error(new ResponseStatusException(
                                    HttpStatus.NOT_FOUND,
                                    "Assignment not found."
                            )))
                            .flatMap(existing -> {
                                existing.setEndOn(LocalDateTime.now());
                                return assignmentRepository.save(existing)
                                        .then(recordAgencyAssignmentEvent(
                                                "fin_assignation_agence",
                                                existing,
                                                actorId
                                        ));
                            });
                })
                .then();
    }

    private Mono<Void> recordAgencyAssignmentEvent(
            String type,
            CashierAgencyAssignment assignment,
            String authorId
    ) {
        if (assignment == null) {
            return Mono.empty();
        }
        Map<String, Object> payload = new java.util.LinkedHashMap<>();
        putIfPresent(payload, "assignment_id", assignment.getId());
        putIfPresent(payload, "cashier_id", assignment.getCashierId());
        putIfPresent(payload, "agency_id", assignment.getAgencyId());
        putIfPresent(payload, "start_on", assignment.getStartOn());
        putIfPresent(payload, "end_on", assignment.getEndOn());
        putIfPresent(payload, "assigned_on", assignment.getAssignedOn());
        putIfPresent(payload, "assigned_by", assignment.getAssignedBy());
        return auditService.recordSubjectEvent(
                type,
                trimToNull(authorId),
                null,
                null,
                "cashier_agency_assignment",
                assignment.getId(),
                "assignment:agency:" + assignment.getId(),
                payload
        ).onErrorResume(ex -> Mono.empty());
    }

    private void putIfPresent(Map<String, Object> payload, String key, Object value) {
        if (payload == null || !StringUtils.hasText(key) || value == null) {
            return;
        }
        payload.put(key, value);
    }

    private Mono<Void> assertAgencyScope(
            String agencyId,
            String organizationId,
            boolean restrictToOrganization
    ) {
        if (!restrictToOrganization) {
            return Mono.empty();
        }
        if (!StringUtils.hasText(organizationId)) {
            return Mono.error(new ResponseStatusException(HttpStatus.FORBIDDEN, "Organization scope is required."));
        }
        String sql = "SELECT organization_id FROM agency WHERE id = :agencyId";
        return entityTemplate.getDatabaseClient()
                .sql(sql)
                .bind("agencyId", agencyId)
                .map((row, meta) -> row.get("organization_id", String.class))
                .one()
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Agency not found.")))
                .flatMap(orgId -> {
                    if (!organizationId.equals(orgId)) {
                        return Mono.error(new ResponseStatusException(
                                HttpStatus.FORBIDDEN,
                                "Unauthorized for this organization."
                        ));
                    }
                    return Mono.empty();
                });
    }

    private Mono<AssignmentScope> fetchAssignmentScope(String assignmentId) {
        String sql = "SELECT ca.id, ca.agency_id, ag.organization_id "
                + "FROM cashier_agency_assignment ca "
                + "JOIN agency ag ON ag.id = ca.agency_id "
                + "WHERE ca.id = :assignmentId";
        return entityTemplate.getDatabaseClient()
                .sql(sql)
                .bind("assignmentId", assignmentId)
                .map((row, meta) -> new AssignmentScope(
                        row.get("id", String.class),
                        row.get("agency_id", String.class),
                        row.get("organization_id", String.class)
                ))
                .one()
                .switchIfEmpty(Mono.error(new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Assignment not found."
                )));
    }

    private Mono<CashierAgencyAssignmentResponse> fetchAssignment(String assignmentId) {
        String sql = "SELECT ca.id, ca.start_on, ca.end_on, ca.assigned_on, "
                + "p.id AS person_id, p.user_name, p.user_first_name, "
                + "ag.id AS agency_id, ag.name AS agency_name, ag.country AS agency_country, "
                + "ag.town AS agency_town, ag.neighborhood AS agency_neighborhood, "
                + "ag.organization_id AS agency_organization_id "
                + "FROM cashier_agency_assignment ca "
                + "JOIN person p ON p.id = ca.cashier_id "
                + "JOIN agency ag ON ag.id = ca.agency_id "
                + "WHERE ca.id = $1";

        DatabaseClient client = entityTemplate.getDatabaseClient();
        return client.sql(sql)
                .bind(0, assignmentId)
                .map((row, meta) -> new CashierAgencyAssignmentResponse(
                        row.get("id", String.class),
                        new PersonSummaryResponse(
                                row.get("person_id", String.class),
                                row.get("user_name", String.class),
                                row.get("user_first_name", String.class)
                        ),
                        new AgencySummaryResponse(
                                row.get("agency_id", String.class),
                                row.get("agency_name", String.class),
                                row.get("agency_country", String.class),
                                row.get("agency_town", String.class),
                                row.get("agency_neighborhood", String.class),
                                row.get("agency_organization_id", String.class)
                        ),
                        row.get("start_on", LocalDateTime.class),
                        row.get("end_on", LocalDateTime.class),
                        row.get("assigned_on", LocalDateTime.class)
                ))
                .one()
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Assignment not found.")));
    }

    private LocalDateTime parseDate(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return LocalDate.parse(value).atStartOfDay();
    }

    private Mono<Void> assertNoOverlappingAssignment(
            String cashierId,
            LocalDateTime startOn,
            LocalDateTime endOn
    ) {
        LocalDateTime startBound = startOn != null ? startOn : LocalDateTime.of(1970, 1, 1, 0, 0);
        LocalDateTime endBound = endOn != null ? endOn : LocalDateTime.of(3000, 1, 1, 0, 0);
        String sql = "SELECT 1 FROM cashier_agency_assignment ca "
                + "WHERE ca.cashier_id = :cashierId "
                + "AND (ca.start_on IS NULL OR ca.start_on <= :endOn) "
                + "AND (ca.end_on IS NULL OR ca.end_on >= :startOn) "
                + "LIMIT 1";
        return entityTemplate.getDatabaseClient()
                .sql(sql)
                .bind("cashierId", cashierId)
                .bind("startOn", startBound)
                .bind("endOn", endBound)
                .map((row, meta) -> 1)
                .first()
                .flatMap(ignore -> Mono.error(new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Cashier already has an assignment in the selected period."
                )))
                .then();
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private record AssignmentScope(String id, String agencyId, String organizationId) {
    }
}
