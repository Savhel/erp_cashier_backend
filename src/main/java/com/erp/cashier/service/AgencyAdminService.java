package com.erp.cashier.service;

import com.erp.cashier.dto.AgencyCashRegisterResponse;
import com.erp.cashier.dto.AgencyCashRegisterSessionResponse;
import com.erp.cashier.dto.AgencyResponse;
import com.erp.cashier.dto.CreateAgencyRequest;
import com.erp.cashier.dto.UpdateAgencyRequest;
import com.erp.cashier.model.Agency;
import com.erp.cashier.repository.AgencyRepository;
import com.erp.cashier.repository.OrganizationRepository;
import io.r2dbc.spi.Row;
import io.r2dbc.spi.RowMetadata;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
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
 * Admin service for managing agencies.
 *
 * @author ERP Cashier Team
 * @since 2025-01-02
 */
@Service
public class AgencyAdminService {
    private static final String STATE_OPEN = "ouverte";

    private final AgencyRepository agencyRepository;
    private final OrganizationRepository organizationRepository;
    private final R2dbcEntityTemplate entityTemplate;

    /**
     * Creates the agency admin service.
     *
     * @param agencyRepository agency repository
     * @param organizationRepository organization repository
     * @param entityTemplate entity template
     */
    public AgencyAdminService(
            AgencyRepository agencyRepository,
            OrganizationRepository organizationRepository,
            R2dbcEntityTemplate entityTemplate
    ) {
        this.agencyRepository = agencyRepository;
        this.organizationRepository = organizationRepository;
        this.entityTemplate = entityTemplate;
    }

    /**
     * Lists agencies filtered by admin scope.
     *
     * @param organizationId organization identifier
     * @param agencyId agency identifier when scoped
     * @param restrictToOrganization true when scoped to an organization
     * @param restrictToAgency true when scoped to an agency
     * @param country optional country filter
     * @param town optional town filter
     * @return agencies
     */
    public Flux<AgencyResponse> listAgencies(
            String organizationId,
            String agencyId,
            boolean restrictToOrganization,
            boolean restrictToAgency,
            String country,
            String town
    ) {
        String trimmedOrganizationId = trimToNull(organizationId);
        if (restrictToOrganization && !StringUtils.hasText(trimmedOrganizationId)) {
            return Flux.error(new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Organization scope is required."
            ));
        }
        String trimmedAgencyId = trimToNull(agencyId);
        if (restrictToAgency && !StringUtils.hasText(trimmedAgencyId)) {
            return Flux.error(new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Agency scope is required."
            ));
        }
        String trimmedCountry = trimToNull(country);
        String trimmedTown = trimToNull(town);

        StringBuilder sql = new StringBuilder();
        sql.append("SELECT a.id, a.name, a.country, a.town, a.neighborhood, a.address, a.location_hint, ");
        sql.append("a.is_active, a.requires_admin_assignment, a.organization_id, a.telegram_bot_token, a.create_on, ");
        sql.append("EXISTS (SELECT 1 FROM cash_register_session s ");
        sql.append("JOIN cash_register r ON r.id = s.cash_register_id ");
        sql.append("WHERE r.agency_id = a.id AND (s.state = :openState OR s.is_locked = true)) ");
        sql.append("AS has_blocking_session, ");
        sql.append("r.id AS register_id, ");
        sql.append("s.state AS session_state, s.is_locked AS session_locked ");
        sql.append("FROM agency a ");
        sql.append("LEFT JOIN cash_register r ON r.agency_id = a.id ");
        sql.append("LEFT JOIN LATERAL (");
        sql.append("SELECT s1.state, s1.is_locked ");
        sql.append("FROM cash_register_session s1 ");
        sql.append("WHERE s1.cash_register_id = r.id ");
        sql.append("ORDER BY s1.open_on DESC NULLS LAST ");
        sql.append("LIMIT 1");
        sql.append(") s ON true ");
        sql.append("WHERE 1=1 ");
        if (StringUtils.hasText(trimmedCountry)) {
            sql.append("AND a.country = :country ");
        }
        if (StringUtils.hasText(trimmedTown)) {
            sql.append("AND a.town = :town ");
        }
        if (restrictToOrganization) {
            sql.append("AND a.organization_id = :organizationId ");
        }
        if (restrictToAgency) {
            sql.append("AND a.id = :agencyId ");
        }
        sql.append("ORDER BY a.name ASC, a.id ASC, r.id ASC");

        DatabaseClient.GenericExecuteSpec spec = entityTemplate.getDatabaseClient()
                .sql(sql.toString())
                .bind("openState", STATE_OPEN);
        if (StringUtils.hasText(trimmedCountry)) {
            spec = spec.bind("country", trimmedCountry);
        }
        if (StringUtils.hasText(trimmedTown)) {
            spec = spec.bind("town", trimmedTown);
        }
        if (restrictToOrganization) {
            spec = spec.bind("organizationId", trimmedOrganizationId);
        }
        if (restrictToAgency) {
            spec = spec.bind("agencyId", trimmedAgencyId);
        }
        return spec.map(this::mapAgencyRow)
                .all()
                .collectList()
                .flatMapMany(rows -> {
                    if (rows.isEmpty()) {
                        return Flux.empty();
                    }
                    List<AgencyResponse> responses = new ArrayList<>();
                    List<AgencyRow> bucket = new ArrayList<>();
                    String currentId = null;
                    for (AgencyRow row : rows) {
                        if (currentId == null || !currentId.equals(row.id())) {
                            if (!bucket.isEmpty()) {
                                responses.add(mergeAgencyRows(bucket));
                                bucket.clear();
                            }
                            currentId = row.id();
                        }
                        bucket.add(row);
                    }
                    if (!bucket.isEmpty()) {
                        responses.add(mergeAgencyRows(bucket));
                    }
                    return Flux.fromIterable(responses);
                });
    }

    /**
     * Creates an agency for the caller scope.
     *
     * @param request create request
     * @param organizationId organization identifier
     * @param restrictToOrganization true when scoped to an organization
     * @param restrictToAgency true when scoped to an agency
     * @return created agency
     */
    public Mono<AgencyResponse> createAgency(
            CreateAgencyRequest request,
            String organizationId,
            boolean restrictToOrganization,
            boolean restrictToAgency
    ) {
        if (request == null) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Agency payload is required."));
        }
        if (restrictToAgency) {
            return Mono.error(new ResponseStatusException(HttpStatus.FORBIDDEN, "Agency scope is not allowed."));
        }
        String name = trimToNull(request.getName());
        if (!StringUtils.hasText(name)) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Agency name is required."));
        }
        String country = trimToNull(request.getCountry());
        if (!StringUtils.hasText(country)) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Agency country is required."));
        }
        String town = trimToNull(request.getTown());
        if (!StringUtils.hasText(town)) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Agency town is required."));
        }
        String resolvedOrganizationId = restrictToOrganization
                ? trimToNull(organizationId)
                : trimToNull(request.getOrganizationId());
        if (restrictToOrganization && !StringUtils.hasText(resolvedOrganizationId)) {
            return Mono.error(new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Organization scope is required."
            ));
        }

        Agency agency = new Agency();
        agency.setId(UUID.randomUUID().toString());
        agency.setName(name);
        agency.setCountry(country);
        agency.setTown(town);
        agency.setNeighborhood(trimToNull(request.getNeighborhood()));
        agency.setAddress(trimToNull(request.getAddress()));
        agency.setLocationHint(trimToNull(request.getLocationHint()));
        agency.setIsActive(request.getIsActive() != null ? request.getIsActive() : Boolean.TRUE);
        agency.setRequiresAdminAssignment(
                request.getRequiresAdminAssignment() != null ? request.getRequiresAdminAssignment() : Boolean.FALSE
        );
        agency.setOrganizationId(resolvedOrganizationId);
        agency.setTelegramBotToken(trimToNull(request.getTelegramBotToken()));
        agency.setCreateOn(LocalDateTime.now());

        return ensureOrganizationExists(resolvedOrganizationId)
                .then(entityTemplate.insert(Agency.class).using(agency))
                .map(saved -> toResponse(saved, Boolean.FALSE, new ArrayList<>()));
    }

    /**
     * Updates an agency for the caller scope.
     *
     * @param agencyId agency identifier
     * @param request update request
     * @param organizationId organization identifier
     * @param agencyScopeId agency identifier when scoped
     * @param restrictToOrganization true when scoped to an organization
     * @param restrictToAgency true when scoped to an agency
     * @return updated agency
     */
    public Mono<AgencyResponse> updateAgency(
            String agencyId,
            UpdateAgencyRequest request,
            String organizationId,
            String agencyScopeId,
            boolean restrictToOrganization,
            boolean restrictToAgency
    ) {
        if (request == null) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Agency payload is required."));
        }
        return resolveScopedAgency(agencyId, organizationId, agencyScopeId, restrictToOrganization, restrictToAgency)
                .flatMap(agency ->
                        assertNoBlockingSession(
                                agencyId,
                                "Cannot edit this agency because one of its registers has an open or locked session."
                        ).thenReturn(agency)
                )
                .flatMap(agency -> {
                    if (request.getName() != null) {
                        String name = trimToNull(request.getName());
                        if (!StringUtils.hasText(name)) {
                            return Mono.error(new ResponseStatusException(
                                    HttpStatus.BAD_REQUEST,
                                    "Agency name cannot be empty."
                            ));
                        }
                        agency.setName(name);
                    }
                    if (request.getNeighborhood() != null) {
                        agency.setNeighborhood(trimToNull(request.getNeighborhood()));
                    }
                    if (request.getAddress() != null) {
                        agency.setAddress(trimToNull(request.getAddress()));
                    }
                    if (request.getLocationHint() != null) {
                        agency.setLocationHint(trimToNull(request.getLocationHint()));
                    }

                    if (request.getCountry() != null) {
                        String country = trimToNull(request.getCountry());
                        if (!StringUtils.hasText(country)) {
                            return Mono.error(new ResponseStatusException(
                                    HttpStatus.BAD_REQUEST,
                                    "Agency country cannot be empty."
                            ));
                        }
                        agency.setCountry(country);
                    }
                    if (request.getTown() != null) {
                        String town = trimToNull(request.getTown());
                        if (!StringUtils.hasText(town)) {
                            return Mono.error(new ResponseStatusException(
                                    HttpStatus.BAD_REQUEST,
                                    "Agency town cannot be empty."
                            ));
                        }
                        agency.setTown(town);
                    }
                    if (request.getIsActive() != null) {
                        agency.setIsActive(request.getIsActive());
                    }
                    if (request.getRequiresAdminAssignment() != null) {
                        agency.setRequiresAdminAssignment(request.getRequiresAdminAssignment());
                    }
                    if (request.getTelegramBotToken() != null) {
                        agency.setTelegramBotToken(trimToNull(request.getTelegramBotToken()));
                    }
                    return entityTemplate.update(agency)
                            .map(updated -> toResponse(updated, Boolean.FALSE, new ArrayList<>()));
                });
    }

    /**
     * Deletes an agency for the caller scope.
     *
     * @param agencyId agency identifier
     * @param organizationId organization identifier
     * @param agencyScopeId agency identifier when scoped
     * @param restrictToOrganization true when scoped to an organization
     * @param restrictToAgency true when scoped to an agency
     * @return completion signal
     */
    public Mono<Void> deleteAgency(
            String agencyId,
            String organizationId,
            String agencyScopeId,
            boolean restrictToOrganization,
            boolean restrictToAgency
    ) {
        return resolveScopedAgency(agencyId, organizationId, agencyScopeId, restrictToOrganization, restrictToAgency)
                .flatMap(agency ->
                        assertNoBlockingSession(
                                agencyId,
                                "Cannot delete this agency because one of its registers has an open or locked session."
                        ).thenReturn(agency)
                )
                .flatMap(agency -> agencyRepository.deleteById(agency.getId()));
    }

    /**
     * Fetches an agency for the caller scope.
     *
     * @param agencyId agency identifier
     * @param organizationId organization identifier
     * @param agencyScopeId agency identifier when scoped
     * @param restrictToOrganization true when scoped to an organization
     * @param restrictToAgency true when scoped to an agency
     * @return agency response
     */
    public Mono<AgencyResponse> getAgency(
            String agencyId,
            String organizationId,
            String agencyScopeId,
            boolean restrictToOrganization,
            boolean restrictToAgency
    ) {
        return resolveScopedAgency(agencyId, organizationId, agencyScopeId, restrictToOrganization, restrictToAgency)
                .flatMap(agency -> fetchAgencyWithRegisters(
                        agency.getId(),
                        organizationId,
                        restrictToOrganization,
                        agencyScopeId,
                        restrictToAgency
                ));
    }

    private Mono<Void> ensureOrganizationExists(String organizationId) {
        if (!StringUtils.hasText(organizationId)) {
            return Mono.empty();
        }
        return organizationRepository.findById(organizationId)
                .switchIfEmpty(Mono.error(new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Organization not found."
                )))
                .then();
    }

    private Mono<Agency> resolveScopedAgency(
            String agencyId,
            String organizationId,
            String agencyScopeId,
            boolean restrictToOrganization,
            boolean restrictToAgency
    ) {
        String trimmedOrganizationId = trimToNull(organizationId);
        return agencyRepository.findById(agencyId)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Agency not found.")))
                .flatMap(agency -> {
                    if (restrictToOrganization) {
                        if (!StringUtils.hasText(trimmedOrganizationId)
                                || !trimmedOrganizationId.equals(agency.getOrganizationId())) {
                            return Mono.error(new ResponseStatusException(
                                    HttpStatus.FORBIDDEN,
                                    "Unauthorized for this agency."
                            ));
                        }
                    }
                    if (restrictToAgency) {
                        String trimmedAgencyScope = trimToNull(agencyScopeId);
                        if (!StringUtils.hasText(trimmedAgencyScope) || !trimmedAgencyScope.equals(agency.getId())) {
                            return Mono.error(new ResponseStatusException(
                                    HttpStatus.FORBIDDEN,
                                    "Unauthorized for this agency."
                            ));
                        }
                    }
                    return Mono.just(agency);
                });
    }

    private Mono<AgencyResponse> fetchAgencyWithRegisters(
            String agencyId,
            String organizationId,
            boolean restrictToOrganization,
            String agencyScopeId,
            boolean restrictToAgency
    ) {
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT a.id, a.name, a.country, a.town, a.neighborhood, a.address, a.location_hint, ");
        sql.append("a.is_active, a.requires_admin_assignment, a.organization_id, a.telegram_bot_token, a.create_on, ");
        sql.append("EXISTS (SELECT 1 FROM cash_register_session s ");
        sql.append("JOIN cash_register r ON r.id = s.cash_register_id ");
        sql.append("WHERE r.agency_id = a.id AND (s.state = :openState OR s.is_locked = true)) ");
        sql.append("AS has_blocking_session, ");
        sql.append("r.id AS register_id, ");
        sql.append("s.state AS session_state, s.is_locked AS session_locked ");
        sql.append("FROM agency a ");
        sql.append("LEFT JOIN cash_register r ON r.agency_id = a.id ");
        sql.append("LEFT JOIN LATERAL (");
        sql.append("SELECT s1.state, s1.is_locked ");
        sql.append("FROM cash_register_session s1 ");
        sql.append("WHERE s1.cash_register_id = r.id ");
        sql.append("ORDER BY s1.open_on DESC NULLS LAST ");
        sql.append("LIMIT 1");
        sql.append(") s ON true ");
        sql.append("WHERE a.id = :agencyId ");
        if (restrictToOrganization) {
            sql.append("AND a.organization_id = :organizationId ");
        }
        if (restrictToAgency) {
            sql.append("AND a.id = :agencyScopeId ");
        }

        DatabaseClient.GenericExecuteSpec spec = entityTemplate.getDatabaseClient()
                .sql(sql.toString())
                .bind("openState", STATE_OPEN)
                .bind("agencyId", agencyId);
        if (restrictToOrganization) {
            spec = spec.bind("organizationId", trimToNull(organizationId));
        }
        if (restrictToAgency) {
            spec = spec.bind("agencyScopeId", trimToNull(agencyScopeId));
        }
        return spec.map(this::mapAgencyRow)
                .all()
                .collectList()
                .flatMap(list -> {
                    if (list.isEmpty()) {
                        return Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Agency not found."));
                    }
                    return Mono.just(mergeAgencyRows(list));
                });
    }

    private Mono<Void> assertNoBlockingSession(String agencyId, String message) {
        return hasBlockingSession(agencyId)
                .flatMap(blocked -> blocked
                        ? Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, message))
                        : Mono.empty());
    }

    private Mono<Boolean> hasBlockingSession(String agencyId) {
        String sql = "SELECT EXISTS ("
                + "SELECT 1 FROM cash_register_session s "
                + "JOIN cash_register r ON r.id = s.cash_register_id "
                + "WHERE r.agency_id = :agencyId AND (s.state = :openState OR s.is_locked = true)"
                + ") AS has_blocking_session";
        return entityTemplate.getDatabaseClient()
                .sql(sql)
                .bind("agencyId", agencyId)
                .bind("openState", STATE_OPEN)
                .map((row, metadata) -> row.get("has_blocking_session", Boolean.class))
                .one()
                .defaultIfEmpty(Boolean.FALSE);
    }

    private AgencyRow mapAgencyRow(Row row, RowMetadata metadata) {
        return new AgencyRow(
                row.get("id", String.class),
                row.get("name", String.class),
                row.get("country", String.class),
                row.get("town", String.class),
                row.get("neighborhood", String.class),
                row.get("address", String.class),
                row.get("location_hint", String.class),
                row.get("is_active", Boolean.class),
                row.get("requires_admin_assignment", Boolean.class),
                row.get("organization_id", String.class),
                row.get("telegram_bot_token", String.class),
                row.get("create_on", LocalDateTime.class),
                row.get("has_blocking_session", Boolean.class),
                row.get("register_id", String.class),
                row.get("session_state", String.class),
                row.get("session_locked", Boolean.class)
        );
    }

    private AgencyResponse mergeAgencyRows(List<AgencyRow> rows) {
        if (rows == null || rows.isEmpty()) {
            return null;
        }
        AgencyRow first = rows.get(0);
        List<AgencyCashRegisterResponse> registers = new ArrayList<>();
        for (AgencyRow row : rows) {
            if (!StringUtils.hasText(row.registerId())) {
                continue;
            }
            List<AgencyCashRegisterSessionResponse> sessions = new ArrayList<>();
            if (StringUtils.hasText(row.sessionState()) || row.sessionLocked() != null) {
                sessions.add(new AgencyCashRegisterSessionResponse(row.sessionState(), row.sessionLocked()));
            }
            registers.add(new AgencyCashRegisterResponse(row.registerId(), sessions));
        }
        return new AgencyResponse(
                first.id(),
                first.name(),
                first.country(),
                first.town(),
                first.neighborhood(),
                first.address(),
                first.locationHint(),
                first.isActive(),
                first.requiresAdminAssignment(),
                first.organizationId(),
                first.telegramBotToken(),
                first.createOn(),
                first.hasBlockingSession(),
                registers
        );
    }

    private AgencyResponse toResponse(
            Agency agency,
            Boolean hasBlockingSession,
            List<AgencyCashRegisterResponse> cashRegisters
    ) {
        return new AgencyResponse(
                agency.getId(),
                agency.getName(),
                agency.getCountry(),
                agency.getTown(),
                agency.getNeighborhood(),
                agency.getAddress(),
                agency.getLocationHint(),
                agency.getIsActive(),
                agency.getRequiresAdminAssignment(),
                agency.getOrganizationId(),
                agency.getTelegramBotToken(),
                agency.getCreateOn(),
                hasBlockingSession,
                cashRegisters
        );
    }

    private String trimToNull(String value) {
        String trimmed = StringUtils.trimWhitespace(value);
        return StringUtils.hasText(trimmed) ? trimmed : null;
    }

    private record AgencyRow(
            String id,
            String name,
            String country,
            String town,
            String neighborhood,
            String address,
            String locationHint,
            Boolean isActive,
            Boolean requiresAdminAssignment,
            String organizationId,
            String telegramBotToken,
            LocalDateTime createOn,
            Boolean hasBlockingSession,
            String registerId,
            String sessionState,
            Boolean sessionLocked
    ) {
    }
}
