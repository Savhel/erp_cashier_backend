package com.erp.cashier.service;

import com.erp.cashier.dto.AssignCashierRequest;
import com.erp.cashier.dto.CashRegisterAgencyResponse;
import com.erp.cashier.dto.CashRegisterAssignedCashierResponse;
import com.erp.cashier.dto.CashRegisterDetailResponse;
import com.erp.cashier.dto.CashRegisterMovementResponse;
import com.erp.cashier.dto.CashRegisterReconciliationResponse;
import com.erp.cashier.dto.CashRegisterResponse;
import com.erp.cashier.dto.CashRegisterSessionDetailResponse;
import com.erp.cashier.dto.CashRegisterSessionSummaryResponse;
import com.erp.cashier.dto.CashRegisterTicketingDenominationResponse;
import com.erp.cashier.dto.CashRegisterTicketingDetailResponse;
import com.erp.cashier.dto.CashRegisterUserResponse;
import com.erp.cashier.dto.CreateCashRegisterRequest;
import com.erp.cashier.dto.UpdateCashRegisterRequest;
import com.erp.cashier.model.Agency;
import com.erp.cashier.model.CashRegisterEvent;
import com.erp.cashier.model.CashRegisterSession;
import com.erp.cashier.model.CashierManageCashRegister;
import com.erp.cashier.model.EventTicketingDetail;
import com.erp.cashier.model.CashRegister;
import com.erp.cashier.repository.AgencyRepository;
import com.erp.cashier.repository.CashRegisterRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.r2dbc.spi.Row;
import io.r2dbc.spi.RowMetadata;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.ReactiveTransactionManager;
import org.springframework.transaction.reactive.TransactionalOperator;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Admin service for managing cash registers.
 *
 * @author ERP Cashier Team
 * @since 2025-01-02
 */
@Service
public class CashRegisterAdminService {
    private static final String STATE_OPEN = "ouverte";

    private final CashRegisterRepository cashRegisterRepository;
    private final AgencyRepository agencyRepository;
    private final R2dbcEntityTemplate entityTemplate;
    private final ObjectMapper objectMapper;
    private final TransactionalOperator transactionalOperator;

    /**
     * Creates the cash register admin service.
     *
     * @param cashRegisterRepository cash register repository
     * @param agencyRepository agency repository
     * @param entityTemplate entity template
     * @param objectMapper object mapper
     * @param transactionManager reactive transaction manager
     */
    public CashRegisterAdminService(
            CashRegisterRepository cashRegisterRepository,
            AgencyRepository agencyRepository,
            R2dbcEntityTemplate entityTemplate,
            ObjectMapper objectMapper,
            ReactiveTransactionManager transactionManager
    ) {
        this.cashRegisterRepository = cashRegisterRepository;
        this.agencyRepository = agencyRepository;
        this.entityTemplate = entityTemplate;
        this.objectMapper = objectMapper;
        this.transactionalOperator = TransactionalOperator.create(transactionManager);
    }

    /**
     * Lists cash registers for admin users.
     *
     * @param organizationId organization identifier when scoped
     * @param agencyId agency identifier when scoped
     * @param restrictToAgency true when the caller is an agency admin
     * @param restrictToOrganization true when the caller is an organization admin
     * @return cash registers
     */
    public Flux<CashRegisterResponse> listRegisters(
            String organizationId,
            String agencyId,
            boolean restrictToAgency,
            boolean restrictToOrganization
    ) {
        if (restrictToAgency && !StringUtils.hasText(agencyId)) {
            return Flux.error(new ResponseStatusException(HttpStatus.FORBIDDEN, "Agency scope is required."));
        }
        if (restrictToOrganization && !StringUtils.hasText(organizationId)) {
            return Flux.error(new ResponseStatusException(HttpStatus.FORBIDDEN, "Organization scope is required."));
        }

        StringBuilder sql = new StringBuilder();
        sql.append("SELECT r.id, r.town, r.country, r.neighborhood, r.adress, r.create_on, ");
        sql.append("r.ip_address, r.mac_address, r.min_open_time, r.max_close_time, r.is_active, ");
        sql.append("r.sale_agent_bank_account, r.sale_agent_accounting_account, ");
        sql.append("r.sale_agent_bank_account, r.sale_agent_accounting_account, ");
        sql.append("r.sale_agent_bank_account, r.sale_agent_accounting_account, ");
        sql.append("a.id AS agency_id, a.name AS agency_name, a.country AS agency_country, ");
        sql.append("a.town AS agency_town, a.neighborhood AS agency_neighborhood, ");
        sql.append("p.user_name AS cashier_user_name, p.user_first_name AS cashier_user_first_name, ");
        sql.append("s.id AS session_id, s.state AS session_state, s.open_on AS session_open_on, ");
        sql.append("s.theorical_initial_funds AS session_initial_funds, ");
        sql.append("s.theorical_close_funds AS session_close_funds ");
        sql.append("FROM cash_register r ");
        sql.append("LEFT JOIN agency a ON a.id = r.agency_id ");
        sql.append("LEFT JOIN person p ON p.id = r.user_id ");
        sql.append("LEFT JOIN LATERAL (");
        sql.append("SELECT s1.id, s1.state, s1.open_on, s1.theorical_initial_funds, s1.theorical_close_funds ");
        sql.append("FROM cash_register_session s1 ");
        sql.append("WHERE s1.cash_register_id = r.id ");
        sql.append("ORDER BY s1.open_on DESC NULLS LAST ");
        sql.append("LIMIT 1");
        sql.append(") s ON true ");
        sql.append("WHERE 1=1 ");
        if (restrictToAgency) {
            sql.append("AND r.agency_id = :agencyId ");
        }
        if (restrictToOrganization) {
            sql.append("AND a.organization_id = :organizationId ");
        }
        sql.append("ORDER BY r.create_on DESC");

        DatabaseClient.GenericExecuteSpec spec = entityTemplate.getDatabaseClient().sql(sql.toString());
        if (restrictToAgency) {
            spec = spec.bind("agencyId", agencyId);
        }
        if (restrictToOrganization) {
            spec = spec.bind("organizationId", organizationId);
        }
        return spec.map(this::mapRegisterRow)
                .all()
                .map(this::ensureSessions);
    }

    /**
     * Creates a new cash register.
     *
     * @param request create request
     * @param creatorId creator identifier
     * @param organizationScopeId organization identifier when scoped
     * @param agencyScopeId agency identifier from the token
     * @param restrictToOrganization true when the caller is an organization admin
     * @param restrictToAgency true when the caller is an agency admin
     * @return created cash register
     */
    public Mono<CashRegisterResponse> createRegister(
            CreateCashRegisterRequest request,
            String creatorId,
            String organizationScopeId,
            String agencyScopeId,
            boolean restrictToOrganization,
            boolean restrictToAgency
    ) {
        if (request == null) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Register payload is required."));
        }
        String resolvedCreatorId = trimToNull(creatorId);
        if (!StringUtils.hasText(resolvedCreatorId)) {
            resolvedCreatorId = trimToNull(request.getCreateBy());
        }
        if (!StringUtils.hasText(resolvedCreatorId)) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "create_by is required"));
        }
        final String creatorIdValue = resolvedCreatorId;

        String resolvedAgencyId = restrictToAgency ? trimToNull(agencyScopeId) : trimToNull(request.getAgencyId());
        if (!StringUtils.hasText(resolvedAgencyId)) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "agency_id is required"));
        }

        Mono<Agency> agencyMono = agencyRepository.findById(resolvedAgencyId)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Agency not found.")));

        return agencyMono.flatMap(agency -> {
            if (restrictToOrganization) {
                String resolvedOrgId = trimToNull(organizationScopeId);
                if (!StringUtils.hasText(resolvedOrgId)) {
                    return Mono.error(new ResponseStatusException(
                            HttpStatus.FORBIDDEN,
                            "Organization scope is required."
                    ));
                }
                if (!resolvedOrgId.equals(agency.getOrganizationId())) {
                    return Mono.error(new ResponseStatusException(
                            HttpStatus.FORBIDDEN,
                            "You can only create registers for your organization."
                    ));
                }
            }
            String country = trimToNull(request.getCountry());
            String town = trimToNull(request.getTown());
            String neighborhood = trimToNull(request.getNeighborhood());

            if (StringUtils.hasText(country) && !country.equals(agency.getCountry())) {
                return Mono.error(new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Register country must match the agency country."
                ));
            }
            if (StringUtils.hasText(town) && !town.equals(agency.getTown())) {
                return Mono.error(new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Register town must match the agency town."
                ));
            }

            if (!StringUtils.hasText(country)) {
                country = agency.getCountry();
            }
            if (!StringUtils.hasText(town)) {
                town = agency.getTown();
            }

            String ipAddress = trimToNull(request.getIpAddress());
            if (!StringUtils.hasText(ipAddress)) {
                return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "ip_address is required"));
            }
            String macAddress = trimToNull(request.getMacAddress());
            if (!StringUtils.hasText(macAddress)) {
                return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "mac_address is required"));
            }

            CashRegister register = new CashRegister();
            register.setId(UUID.randomUUID().toString());
            register.setCreateBy(creatorIdValue);
            register.setCreateOn(LocalDateTime.now());
            register.setAgencyId(resolvedAgencyId);
            register.setCountry(country);
            register.setTown(town);
            register.setNeighborhood(neighborhood);
            register.setAdress(trimToNull(request.getAdress()));
            register.setIpAddress(ipAddress);
            register.setMacAddress(macAddress);
            register.setImageUrl(trimToNull(request.getImageUrl()));
            register.setMinOpenTime(trimToNull(request.getMinOpenTime()));
            register.setMaxCloseTime(trimToNull(request.getMaxCloseTime()));
            register.setSaleAgentBankAccount(trimToNull(request.getSaleAgentBankAccount()));
            register.setSaleAgentAccountingAccount(trimToNull(request.getSaleAgentAccountingAccount()));
            register.setIsActive(request.getIsActive() != null ? request.getIsActive() : Boolean.TRUE);
            register.setDoubleClosingCount(Boolean.FALSE);

            return entityTemplate.insert(CashRegister.class)
                    .using(register)
                    .flatMap(saved -> fetchRegisterSummary(saved.getId()));
        });
    }

    /**
     * Returns detailed cash register information.
     *
     * @param registerId register identifier
     * @param agencyScopeId agency identifier when scoped
     * @param restrictToAgency true when the caller is an agency admin
     * @return detailed cash register response
     */
    public Mono<CashRegisterDetailResponse> getRegisterDetails(
            String registerId,
            String agencyScopeId,
            boolean restrictToAgency
    ) {
        return getRegisterDetails(registerId, null, agencyScopeId, restrictToAgency, false);
    }

    /**
     * Returns detailed cash register information with organization scope.
     *
     * @param registerId register identifier
     * @param organizationScopeId organization identifier when scoped
     * @param agencyScopeId agency identifier when scoped
     * @param restrictToAgency true when the caller is an agency admin
     * @param restrictToOrganization true when the caller is an organization admin
     * @return detailed cash register response
     */
    public Mono<CashRegisterDetailResponse> getRegisterDetails(
            String registerId,
            String organizationScopeId,
            String agencyScopeId,
            boolean restrictToAgency,
            boolean restrictToOrganization
    ) {
        return fetchRegisterDetail(registerId)
                .flatMap(register -> {
                    if (restrictToAgency && !StringUtils.hasText(agencyScopeId)) {
                        return Mono.error(new ResponseStatusException(
                                HttpStatus.FORBIDDEN,
                                "Agency scope is required."
                        ));
                    }
                    if (restrictToAgency
                            && (register.getAgency() == null
                            || !agencyScopeId.equals(register.getAgency().getId()))) {
                        return Mono.error(new ResponseStatusException(
                                HttpStatus.FORBIDDEN,
                                "You can only access registers from your agency."
                        ));
                    }
                    if (restrictToOrganization) {
                        if (!StringUtils.hasText(organizationScopeId)) {
                            return Mono.error(new ResponseStatusException(
                                    HttpStatus.FORBIDDEN,
                                    "Organization scope is required."
                            ));
                        }
                        if (register.getAgency() == null
                                || !StringUtils.hasText(register.getAgency().getId())) {
                            return Mono.error(new ResponseStatusException(
                                    HttpStatus.FORBIDDEN,
                                    "You can only access registers from your organization."
                            ));
                        }
                        return agencyRepository.findById(register.getAgency().getId())
                                .flatMap(agency -> {
                                    if (!organizationScopeId.equals(agency.getOrganizationId())) {
                                        return Mono.error(new ResponseStatusException(
                                                HttpStatus.FORBIDDEN,
                                                "You can only access registers from your organization."
                                        ));
                                    }
                                    return fetchSessionDetails(registerId)
                                            .collectList()
                                            .map(sessions -> {
                                                register.setSessions(sessions);
                                                return register;
                                            });
                                });
                    }
                    return fetchSessionDetails(registerId)
                            .collectList()
                            .map(sessions -> {
                                register.setSessions(sessions);
                                return register;
                            });
                });
    }

    /**
     * Updates a cash register.
     *
     * @param registerId register identifier
     * @param request update request
     * @param organizationScopeId organization identifier when scoped
     * @param agencyScopeId agency identifier when scoped
     * @param restrictToOrganization true when the caller is an organization admin
     * @param restrictToAgency true when the caller is an agency admin
     * @return updated cash register
     */
    public Mono<CashRegisterResponse> updateRegister(
            String registerId,
            UpdateCashRegisterRequest request,
            String organizationScopeId,
            String agencyScopeId,
            boolean restrictToOrganization,
            boolean restrictToAgency
    ) {
        if (request == null) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Register payload is required."));
        }
        String ipAddress = trimToNull(request.getIpAddress());
        if (!StringUtils.hasText(ipAddress)) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "ip_address is required"));
        }
        String macAddress = trimToNull(request.getMacAddress());
        if (!StringUtils.hasText(macAddress)) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "mac_address is required"));
        }

        return cashRegisterRepository.findById(registerId)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Register not found")))
                .flatMap(register -> {
                    if (restrictToAgency && !StringUtils.hasText(agencyScopeId)) {
                        return Mono.error(new ResponseStatusException(
                                HttpStatus.FORBIDDEN,
                                "Agency scope is required."
                        ));
                    }
                    if (restrictToAgency && StringUtils.hasText(agencyScopeId)
                            && !agencyScopeId.equals(register.getAgencyId())) {
                        return Mono.error(new ResponseStatusException(
                                HttpStatus.FORBIDDEN,
                                "You can only manage registers from your agency."
                        ));
                    }

                    Mono<Agency> agencyMono = restrictToOrganization
                            ? agencyRepository.findById(register.getAgencyId())
                            : Mono.empty();

                    return agencyMono.defaultIfEmpty(null).flatMap(agency -> {
                        if (restrictToOrganization) {
                            String resolvedOrgId = trimToNull(organizationScopeId);
                            if (!StringUtils.hasText(resolvedOrgId)) {
                                return Mono.error(new ResponseStatusException(
                                        HttpStatus.FORBIDDEN,
                                        "Organization scope is required."
                                ));
                            }
                            if (agency == null || !resolvedOrgId.equals(agency.getOrganizationId())) {
                                return Mono.error(new ResponseStatusException(
                                        HttpStatus.FORBIDDEN,
                                        "You can only manage registers from your organization."
                                ));
                            }
                        }

                        register.setIpAddress(ipAddress);
                        register.setMacAddress(macAddress);

                        if (request.getNeighborhood() != null) {
                            String neighborhood = trimToNull(request.getNeighborhood());
                            if (!StringUtils.hasText(neighborhood)) {
                                return Mono.error(new ResponseStatusException(
                                        HttpStatus.BAD_REQUEST,
                                        "neighborhood cannot be empty"
                                ));
                            }
                            register.setNeighborhood(neighborhood);
                        }
                        if (request.getTown() != null) {
                            String town = trimToNull(request.getTown());
                            if (!StringUtils.hasText(town)) {
                                return Mono.error(new ResponseStatusException(
                                        HttpStatus.BAD_REQUEST,
                                        "town cannot be empty"
                                ));
                            }
                            register.setTown(town);
                        }
                        if (request.getCountry() != null) {
                            String country = trimToNull(request.getCountry());
                            if (!StringUtils.hasText(country)) {
                                return Mono.error(new ResponseStatusException(
                                        HttpStatus.BAD_REQUEST,
                                        "country cannot be empty"
                                ));
                            }
                            register.setCountry(country);
                        }
                        if (request.getIsActive() != null) {
                            register.setIsActive(request.getIsActive());
                        }

                        register.setMinOpenTime(trimToNull(request.getMinOpenTime()));
                        register.setMaxCloseTime(trimToNull(request.getMaxCloseTime()));
                        register.setSaleAgentBankAccount(trimToNull(request.getSaleAgentBankAccount()));
                        register.setSaleAgentAccountingAccount(trimToNull(request.getSaleAgentAccountingAccount()));

                        return entityTemplate.update(register)
                                .flatMap(saved -> fetchRegisterSummary(saved.getId()));
                    });
                });
    }

    /**
     * Deletes a cash register.
     *
     * @param registerId register identifier
     * @param organizationScopeId organization identifier when scoped
     * @param agencyScopeId agency identifier when scoped
     * @param restrictToOrganization true when the caller is an organization admin
     * @param restrictToAgency true when the caller is an agency admin
     * @return completion signal
     */
    public Mono<Void> deleteRegister(
            String registerId,
            String organizationScopeId,
            String agencyScopeId,
            boolean restrictToOrganization,
            boolean restrictToAgency
    ) {
        return cashRegisterRepository.findById(registerId)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Register not found")))
                .flatMap(register -> {
                    if (restrictToAgency && !StringUtils.hasText(agencyScopeId)) {
                        return Mono.error(new ResponseStatusException(
                                HttpStatus.FORBIDDEN,
                                "Agency scope is required."
                        ));
                    }
                    if (restrictToAgency && StringUtils.hasText(agencyScopeId)
                            && !agencyScopeId.equals(register.getAgencyId())) {
                        return Mono.error(new ResponseStatusException(
                                HttpStatus.FORBIDDEN,
                                "You can only manage registers from your agency."
                        ));
                    }
                    Mono<Agency> agencyMono = restrictToOrganization
                            ? agencyRepository.findById(register.getAgencyId())
                            : Mono.empty();
                    return agencyMono.defaultIfEmpty(null).flatMap(agency -> {
                        if (restrictToOrganization) {
                            String resolvedOrgId = trimToNull(organizationScopeId);
                            if (!StringUtils.hasText(resolvedOrgId)) {
                                return Mono.error(new ResponseStatusException(
                                        HttpStatus.FORBIDDEN,
                                        "Organization scope is required."
                                ));
                            }
                            if (agency == null || !resolvedOrgId.equals(agency.getOrganizationId())) {
                                return Mono.error(new ResponseStatusException(
                                        HttpStatus.FORBIDDEN,
                                        "You can only manage registers from your organization."
                                ));
                            }
                        }
                        if (StringUtils.hasText(register.getUserId())) {
                            return Mono.error(new ResponseStatusException(
                                    HttpStatus.BAD_REQUEST,
                                    "Cannot delete an assigned register."
                            ));
                        }
                        return assertNoOpenSession(registerId)
                                .then(cashRegisterRepository.delete(register));
                    });
                });
    }

    /**
     * Assigns a cashier to a cash register and opens the session.
     *
     * @param registerId register identifier
     * @param request assign request
     * @param adminId admin identifier
     * @param organizationScopeId organization identifier when scoped
     * @param agencyScopeId agency identifier when scoped
     * @param restrictToOrganization true when the caller is an organization admin
     * @param restrictToAgency true when the caller is an agency admin
     * @return assignment payload
     */
    public Mono<CashierManageCashRegister> assignCashier(
            String registerId,
            AssignCashierRequest request,
            String adminId,
            String organizationScopeId,
            String agencyScopeId,
            boolean restrictToOrganization,
            boolean restrictToAgency
    ) {
        if (request == null) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Assignment payload is required."));
        }
        String cashierId = trimToNull(request.getCashierId());
        if (!StringUtils.hasText(cashierId)) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "cashier_id is required"));
        }
        AssignCashierRequest.InitialFunds initialFunds = request.getInitialFunds();
        if (initialFunds == null) {
            return Mono.error(new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Initial funds are required."
            ));
        }
        BigDecimal total = initialFunds.getTotal();
        if (total == null) {
            return Mono.error(new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Initial funds total is required."
            ));
        }
        if (total.signum() < 0) {
            return Mono.error(new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Initial funds cannot be negative."
            ));
        }
        String resolvedAdminId = trimToNull(adminId);
        if (!StringUtils.hasText(resolvedAdminId)) {
            return Mono.error(new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin scope is required."));
        }
        String resolvedAgencyId = restrictToAgency ? trimToNull(agencyScopeId) : null;
        if (restrictToAgency && !StringUtils.hasText(resolvedAgencyId)) {
            return Mono.error(new ResponseStatusException(HttpStatus.FORBIDDEN, "Agency scope is required."));
        }
        Map<String, Integer> denominations = initialFunds.getDenominations();
        boolean hasLines = hasTicketingLines(denominations);

        return fetchRegisterInfo(registerId)
                .flatMap(register -> {
                    if (restrictToAgency && !resolvedAgencyId.equals(register.agencyId())) {
                        return Mono.error(new ResponseStatusException(
                                HttpStatus.FORBIDDEN,
                                "Unauthorized for this agency."
                        ));
                    }
                    Mono<Agency> agencyMono = restrictToOrganization
                            ? agencyRepository.findById(register.agencyId())
                            : Mono.empty();
                    return agencyMono.defaultIfEmpty(null).flatMap(agency -> {
                        if (restrictToOrganization) {
                            String resolvedOrgId = trimToNull(organizationScopeId);
                            if (!StringUtils.hasText(resolvedOrgId)) {
                                return Mono.error(new ResponseStatusException(
                                        HttpStatus.FORBIDDEN,
                                        "Organization scope is required."
                                ));
                            }
                            if (agency == null || !resolvedOrgId.equals(agency.getOrganizationId())) {
                                return Mono.error(new ResponseStatusException(
                                        HttpStatus.FORBIDDEN,
                                        "Unauthorized for this organization."
                                ));
                            }
                        }

                        Mono<Void> agencyAssignmentCheck = restrictToAgency
                                ? assertCashierAssignedToAgency(cashierId, resolvedAgencyId)
                                : Mono.empty();

                        return Mono.when(
                                        assertRegisterNotLocked(registerId),
                                        assertRegisterNoActiveSession(registerId),
                                        assertCashierNoOpenSession(cashierId),
                                        assertCashierNoLockedSession(cashierId),
                                        agencyAssignmentCheck
                                )
                                .then(fetchCashierProfile(cashierId)
                                        .flatMap(profile -> assertTownAuthorized(register, profile)))
                                .thenReturn(register);
                    });
                })
                .flatMap(register ->
                        serializeInitialFunds(initialFunds)
                                .flatMap(payload -> executeAssignment(
                                        register,
                                        cashierId,
                                        resolvedAdminId,
                                        denominations,
                                        total,
                                        hasLines,
                                        payload
                                ))
                );
    }

    private Mono<CashRegisterResponse> fetchRegisterSummary(String registerId) {
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT r.id, r.town, r.country, r.neighborhood, r.adress, r.create_on, ");
        sql.append("r.ip_address, r.mac_address, r.min_open_time, r.max_close_time, r.is_active, ");
        sql.append("a.id AS agency_id, a.name AS agency_name, a.country AS agency_country, ");
        sql.append("a.town AS agency_town, a.neighborhood AS agency_neighborhood, ");
        sql.append("p.user_name AS cashier_user_name, p.user_first_name AS cashier_user_first_name, ");
        sql.append("s.id AS session_id, s.state AS session_state, s.open_on AS session_open_on, ");
        sql.append("s.theorical_initial_funds AS session_initial_funds, ");
        sql.append("s.theorical_close_funds AS session_close_funds ");
        sql.append("FROM cash_register r ");
        sql.append("LEFT JOIN agency a ON a.id = r.agency_id ");
        sql.append("LEFT JOIN person p ON p.id = r.user_id ");
        sql.append("LEFT JOIN LATERAL (");
        sql.append("SELECT s1.id, s1.state, s1.open_on, s1.theorical_initial_funds, s1.theorical_close_funds ");
        sql.append("FROM cash_register_session s1 ");
        sql.append("WHERE s1.cash_register_id = r.id ");
        sql.append("ORDER BY s1.open_on DESC NULLS LAST ");
        sql.append("LIMIT 1");
        sql.append(") s ON true ");
        sql.append("WHERE r.id = :registerId");

        return entityTemplate.getDatabaseClient()
                .sql(sql.toString())
                .bind("registerId", registerId)
                .map(this::mapRegisterRow)
                .one()
                .map(this::ensureSessions)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Register not found")));
    }

    private Mono<CashRegisterDetailResponse> fetchRegisterDetail(String registerId) {
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT r.id, r.town, r.country, r.neighborhood, r.adress, r.create_on, ");
        sql.append("r.ip_address, r.mac_address, r.min_open_time, r.max_close_time, r.is_active, ");
        sql.append("r.agency_id, a.name AS agency_name, a.country AS agency_country, ");
        sql.append("a.town AS agency_town, a.neighborhood AS agency_neighborhood, ");
        sql.append("p.user_name AS cashier_user_name, p.user_first_name AS cashier_user_first_name ");
        sql.append("FROM cash_register r ");
        sql.append("LEFT JOIN agency a ON a.id = r.agency_id ");
        sql.append("LEFT JOIN person p ON p.id = r.user_id ");
        sql.append("WHERE r.id = :registerId");

        return entityTemplate.getDatabaseClient()
                .sql(sql.toString())
                .bind("registerId", registerId)
                .map(this::mapRegisterDetailRow)
                .one()
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Register not found")));
    }

    private Mono<RegisterInfo> fetchRegisterInfo(String registerId) {
        String sql = "SELECT id, town, agency_id FROM cash_register WHERE id = :registerId";
        return entityTemplate.getDatabaseClient()
                .sql(sql)
                .bind("registerId", registerId)
                .map((row, meta) -> new RegisterInfo(
                        row.get("id", String.class),
                        row.get("town", String.class),
                        row.get("agency_id", String.class)
                ))
                .one()
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Register not found")));
    }

    private Flux<CashRegisterSessionDetailResponse> fetchSessionDetails(String registerId) {
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT s.id, s.state, s.open_on, s.close_on, s.theorical_initial_funds, ");
        sql.append("s.theorical_close_funds, s.is_locked, ");
        sql.append("op.user_name AS opener_user_name, op.user_first_name AS opener_user_first_name, ");
        sql.append("cl.user_name AS closer_user_name, cl.user_first_name AS closer_user_first_name ");
        sql.append("FROM cash_register_session s ");
        sql.append("LEFT JOIN person op ON op.id = s.open_by ");
        sql.append("LEFT JOIN person cl ON cl.id = s.close_by ");
        sql.append("WHERE s.cash_register_id = :registerId ");
        sql.append("ORDER BY s.open_on DESC");

        return entityTemplate.getDatabaseClient()
                .sql(sql.toString())
                .bind("registerId", registerId)
                .map(this::mapSessionRow)
                .all()
                .flatMap(this::enrichSession);
    }

    private Mono<Void> assertRegisterNotLocked(String registerId) {
        String sql = "SELECT is_locked FROM cash_register_session "
                + "WHERE cash_register_id = :registerId "
                + "ORDER BY open_on DESC NULLS LAST LIMIT 1";
        return entityTemplate.getDatabaseClient()
                .sql(sql)
                .bind("registerId", registerId)
                .map((row, meta) -> row.get("is_locked", Boolean.class))
                .one()
                .filter(Boolean.TRUE::equals)
                .flatMap(ignore -> Mono.error(new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "This cash register is locked."
                )))
                .then();
    }

    private Mono<Void> assertRegisterNoActiveSession(String registerId) {
        String sql = "SELECT 1 FROM cash_register_session "
                + "WHERE cash_register_id = :registerId "
                + "AND state IN (:openState, :closingState) "
                + "LIMIT 1";
        return entityTemplate.getDatabaseClient()
                .sql(sql)
                .bind("registerId", registerId)
                .bind("openState", STATE_OPEN)
                .bind("closingState", "en_cloture")
                .map((row, meta) -> 1)
                .first()
                .flatMap(ignore -> Mono.error(new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "There is already an active session for this cash register."
                )))
                .then();
    }

    private Mono<Void> assertCashierNoOpenSession(String cashierId) {
        String sql = "SELECT 1 FROM cash_register_session "
                + "WHERE open_by = :cashierId AND state = :openState LIMIT 1";
        return entityTemplate.getDatabaseClient()
                .sql(sql)
                .bind("cashierId", cashierId)
                .bind("openState", STATE_OPEN)
                .map((row, meta) -> 1)
                .first()
                .flatMap(ignore -> Mono.error(new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Cashier already has an active session."
                )))
                .then();
    }

    private Mono<Void> assertCashierNoLockedSession(String cashierId) {
        String sql = "SELECT 1 FROM cash_register_session "
                + "WHERE open_by = :cashierId AND is_locked = true LIMIT 1";
        return entityTemplate.getDatabaseClient()
                .sql(sql)
                .bind("cashierId", cashierId)
                .map((row, meta) -> 1)
                .first()
                .flatMap(ignore -> Mono.error(new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "This cashier has a locked session. Please unlock it before assigning a new one."
                )))
                .then();
    }

    private Mono<Void> assertCashierAssignedToAgency(String cashierId, String agencyId) {
        String sql = "WITH active_assignment AS ("
                + "SELECT DISTINCT ON (cashier_id) cashier_id, agency_id "
                + "FROM cashier_agency_assignment "
                + "WHERE (start_on IS NULL OR start_on <= CURRENT_TIMESTAMP) "
                + "AND (end_on IS NULL OR end_on >= CURRENT_TIMESTAMP) "
                + "AND assigned_by IS NOT NULL "
                + "ORDER BY cashier_id, assigned_on DESC"
                + ") "
                + "SELECT 1 FROM cashier_profile cp "
                + "LEFT JOIN active_assignment aa ON aa.cashier_id = cp.person_id "
                + "WHERE cp.person_id = :cashierId "
                + "AND (aa.agency_id = :agencyId "
                + "OR (aa.cashier_id IS NULL AND cp.base_agency_id = :agencyId)) "
                + "LIMIT 1";
        return entityTemplate.getDatabaseClient()
                .sql(sql)
                .bind("cashierId", cashierId)
                .bind("agencyId", agencyId)
                .map((row, meta) -> 1)
                .first()
                .switchIfEmpty(Mono.error(new ResponseStatusException(
                        HttpStatus.FORBIDDEN,
                        "Cashier is not assigned to your agency."
                )))
                .then();
    }

    private Mono<CashierTownProfile> fetchCashierProfile(String cashierId) {
        String sql = "SELECT town_list_chosen, work_town FROM cashier_profile WHERE person_id = :cashierId";
        return entityTemplate.getDatabaseClient()
                .sql(sql)
                .bind("cashierId", cashierId)
                .map((row, meta) -> new CashierTownProfile(
                        row.get("town_list_chosen", String.class),
                        row.get("work_town", String.class)
                ))
                .one()
                .switchIfEmpty(Mono.error(new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Invalid cashier profile."
                )));
    }

    private Mono<Void> assertTownAuthorized(RegisterInfo register, CashierTownProfile profile) {
        String registerTown = trimToNull(register.town());
        if (!StringUtils.hasText(registerTown)) {
            return Mono.empty();
        }
        List<String> allowedTowns = parseTownList(profile.townListChosen());
        String workTown = trimToNull(profile.workTown());
        if (StringUtils.hasText(workTown) && !allowedTowns.contains(workTown)) {
            allowedTowns.add(workTown);
        }
        if (!allowedTowns.contains(registerTown)) {
            return Mono.error(new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Cashier is not authorized to work in " + registerTown + "."
            ));
        }
        return Mono.empty();
    }

    private Mono<String> serializeInitialFunds(AssignCashierRequest.InitialFunds initialFunds) {
        try {
            return Mono.just(objectMapper.writeValueAsString(initialFunds));
        } catch (JsonProcessingException ex) {
            return Mono.error(new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to serialize initial funds.",
                    ex
            ));
        }
    }

    private Mono<CashierManageCashRegister> executeAssignment(
            RegisterInfo register,
            String cashierId,
            String adminId,
            Map<String, Integer> denominations,
            BigDecimal total,
            boolean hasLines,
            String payload
    ) {
        CashierManageCashRegister assignment = new CashierManageCashRegister();
        assignment.setId(UUID.randomUUID().toString());
        assignment.setCashRegisterId(register.id());
        assignment.setUserId(cashierId);
        assignment.setDay(LocalDateTime.now());

        CashRegisterSession session = new CashRegisterSession();
        session.setId(UUID.randomUUID().toString());
        session.setCashRegisterId(register.id());
        session.setOpenBy(cashierId);
        session.setState(STATE_OPEN);
        session.setOpenOn(LocalDateTime.now());
        session.setTheoricalInitialFunds(total);
        session.setIsLocked(false);

        Mono<CashierManageCashRegister> insertFlow = entityTemplate.insert(CashierManageCashRegister.class)
                .using(assignment)
                .then(updateRegisterAssignment(register.id(), cashierId))
                .then(entityTemplate.insert(CashRegisterSession.class).using(session))
                .flatMap(savedSession ->
                        insertTicketingDetails(savedSession.getId(), denominations)
                                .flatMap(calculatedTotal -> {
                                    if (hasLines && calculatedTotal.compareTo(total) != 0) {
                                        return Mono.error(new ResponseStatusException(
                                                HttpStatus.BAD_REQUEST,
                                                "Billetage mismatch: declared total is "
                                                        + total + " XAF but calculated total is "
                                                        + calculatedTotal + " XAF"
                                        ));
                                    }
                                    return insertOpeningEvent(savedSession.getId(), adminId, payload)
                                            .thenReturn(assignment);
                                })
                );

        return transactionalOperator.transactional(insertFlow);
    }

    private Mono<Void> updateRegisterAssignment(String registerId, String cashierId) {
        String sql = "UPDATE cash_register SET user_id = :cashierId WHERE id = :registerId";
        return entityTemplate.getDatabaseClient()
                .sql(sql)
                .bind("cashierId", cashierId)
                .bind("registerId", registerId)
                .fetch()
                .rowsUpdated()
                .flatMap(count -> count > 0
                        ? Mono.empty()
                        : Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Register not found"))
                );
    }

    private Mono<BigDecimal> insertTicketingDetails(
            String sessionId,
            Map<String, Integer> denominations
    ) {
        if (!hasTicketingLines(denominations)) {
            return Mono.just(BigDecimal.ZERO);
        }
        return Flux.fromIterable(denominations.entrySet())
                .filter(entry -> entry.getValue() != null && entry.getValue() > 0)
                .flatMap(entry -> fetchDenomination(entry.getKey())
                        .flatMap(denomination -> {
                            int quantity = entry.getValue();
                            BigDecimal lineTotal = denomination.value().multiply(BigDecimal.valueOf(quantity));
                            EventTicketingDetail detail = new EventTicketingDetail();
                            detail.setId(UUID.randomUUID().toString());
                            detail.setSessionId(sessionId);
                            detail.setConnectionType("session_ouverture");
                            detail.setQuantity(quantity);
                            detail.setValue(denomination.value());
                            detail.setTotal(lineTotal);
                            detail.setDenominationId(denomination.id());
                            return entityTemplate.insert(EventTicketingDetail.class)
                                    .using(detail)
                                    .thenReturn(lineTotal);
                        })
                )
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private Mono<DenominationInfo> fetchDenomination(String denominationId) {
        String sql = "SELECT id, value FROM currency_denomination WHERE id = :denominationId";
        return entityTemplate.getDatabaseClient()
                .sql(sql)
                .bind("denominationId", denominationId)
                .map((row, meta) -> new DenominationInfo(
                        row.get("id", String.class),
                        row.get("value", BigDecimal.class)
                ))
                .one()
                .switchIfEmpty(Mono.error(new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Denomination not found."
                )));
    }

    private Mono<Void> insertOpeningEvent(String sessionId, String adminId, String payload) {
        CashRegisterEvent event = new CashRegisterEvent();
        event.setId(UUID.randomUUID().toString());
        event.setSessionId(sessionId);
        event.setType("ouverture");
        event.setAuthorId(adminId);
        event.setPayload(payload);
        event.setDateTime(LocalDateTime.now());
        return entityTemplate.insert(CashRegisterEvent.class)
                .using(event)
                .then();
    }

    private Mono<CashRegisterSessionDetailResponse> enrichSession(CashRegisterSessionDetailResponse session) {
        Mono<List<CashRegisterMovementResponse>> movements = fetchMovements(session.getId()).collectList();
        Mono<List<CashRegisterTicketingDetailResponse>> ticketing = fetchTicketingDetails(session.getId())
                .collectList();
        Mono<CashRegisterReconciliationResponse> reconciliation = fetchReconciliation(session.getId())
                .defaultIfEmpty(null);

        return Mono.zip(movements, ticketing, reconciliation)
                .map(tuple -> {
                    session.setMovements(tuple.getT1());
                    session.setTicketingDetails(tuple.getT2());
                    session.setReconciliation(tuple.getT3());
                    return session;
                });
    }

    private Flux<CashRegisterMovementResponse> fetchMovements(String sessionId) {
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT m.id, m.sense, m.amount, m.reason, m.create_on, ");
        sql.append("p.user_name AS creator_user_name, p.user_first_name AS creator_user_first_name ");
        sql.append("FROM cash_register_movement m ");
        sql.append("LEFT JOIN person p ON p.id = m.create_by ");
        sql.append("WHERE m.session_id = :sessionId ");
        sql.append("ORDER BY m.create_on DESC");

        return entityTemplate.getDatabaseClient()
                .sql(sql.toString())
                .bind("sessionId", sessionId)
                .map(this::mapMovementRow)
                .all();
    }

    private Flux<CashRegisterTicketingDetailResponse> fetchTicketingDetails(String sessionId) {
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT d.id, d.connection_type, d.quantity, d.value, d.total, ");
        sql.append("denom.value AS denomination_value, denom.label AS denomination_label ");
        sql.append("FROM event_ticketing_detail d ");
        sql.append("LEFT JOIN currency_denomination denom ON denom.id = d.denomination_id ");
        sql.append("WHERE d.session_id = :sessionId ");
        sql.append("ORDER BY d.id");

        return entityTemplate.getDatabaseClient()
                .sql(sql.toString())
                .bind("sessionId", sessionId)
                .map(this::mapTicketingRow)
                .all();
    }

    private Mono<CashRegisterReconciliationResponse> fetchReconciliation(String sessionId) {
        String sql = "SELECT theorical_total, physical_total, difference, justification "
                + "FROM cash_reconciliation WHERE session_id = :sessionId LIMIT 1";

        return entityTemplate.getDatabaseClient()
                .sql(sql)
                .bind("sessionId", sessionId)
                .map(this::mapReconciliationRow)
                .one();
    }

    private Mono<Void> assertNoOpenSession(String registerId) {
        String sql = "SELECT 1 FROM cash_register_session "
                + "WHERE cash_register_id = :registerId AND state = :state LIMIT 1";

        return entityTemplate.getDatabaseClient()
                .sql(sql)
                .bind("registerId", registerId)
                .bind("state", STATE_OPEN)
                .map((row, meta) -> 1)
                .first()
                .flatMap(ignore -> Mono.error(new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Cannot delete register with an open session."
                )))
                .then();
    }

    private CashRegisterResponse mapRegisterRow(Row row, RowMetadata metadata) {
        CashRegisterResponse response = new CashRegisterResponse();
        response.setId(row.get("id", String.class));
        response.setTown(row.get("town", String.class));
        response.setCountry(row.get("country", String.class));
        response.setNeighborhood(row.get("neighborhood", String.class));
        response.setAdress(row.get("adress", String.class));
        response.setCreateOn(row.get("create_on", LocalDateTime.class));
        response.setIpAddress(row.get("ip_address", String.class));
        response.setMacAddress(row.get("mac_address", String.class));
        response.setMinOpenTime(row.get("min_open_time", String.class));
        response.setMaxCloseTime(row.get("max_close_time", String.class));
        response.setSaleAgentBankAccount(row.get("sale_agent_bank_account", String.class));
        response.setSaleAgentAccountingAccount(row.get("sale_agent_accounting_account", String.class));
        response.setIsActive(row.get("is_active", Boolean.class));

        CashRegisterAgencyResponse agency = mapAgency(row);
        response.setAgency(agency);

        CashRegisterAssignedCashierResponse cashier = mapAssignedCashier(row);
        response.setAssignedCashier(cashier);

        CashRegisterSessionSummaryResponse session = mapSessionSummary(row);
        if (session != null) {
            List<CashRegisterSessionSummaryResponse> sessions = new ArrayList<>();
            sessions.add(session);
            response.setSessions(sessions);
        }
        return response;
    }

    private CashRegisterDetailResponse mapRegisterDetailRow(Row row, RowMetadata metadata) {
        CashRegisterDetailResponse response = new CashRegisterDetailResponse();
        response.setId(row.get("id", String.class));
        response.setTown(row.get("town", String.class));
        response.setCountry(row.get("country", String.class));
        response.setNeighborhood(row.get("neighborhood", String.class));
        response.setAdress(row.get("adress", String.class));
        response.setCreateOn(row.get("create_on", LocalDateTime.class));
        response.setIpAddress(row.get("ip_address", String.class));
        response.setMacAddress(row.get("mac_address", String.class));
        response.setMinOpenTime(row.get("min_open_time", String.class));
        response.setMaxCloseTime(row.get("max_close_time", String.class));
        response.setSaleAgentBankAccount(row.get("sale_agent_bank_account", String.class));
        response.setSaleAgentAccountingAccount(row.get("sale_agent_accounting_account", String.class));
        response.setIsActive(row.get("is_active", Boolean.class));

        CashRegisterAgencyResponse agency = mapAgency(row);
        response.setAgency(agency);

        CashRegisterAssignedCashierResponse cashier = mapAssignedCashier(row);
        response.setAssignedCashier(cashier);

        response.setSessions(new ArrayList<>());
        return response;
    }

    private CashRegisterAgencyResponse mapAgency(Row row) {
        String agencyId = row.get("agency_id", String.class);
        String agencyName = row.get("agency_name", String.class);
        String agencyCountry = row.get("agency_country", String.class);
        String agencyTown = row.get("agency_town", String.class);
        String agencyNeighborhood = row.get("agency_neighborhood", String.class);
        if (!StringUtils.hasText(agencyId) && !StringUtils.hasText(agencyName)) {
            return null;
        }
        return new CashRegisterAgencyResponse(
                agencyId,
                agencyName,
                agencyCountry,
                agencyTown,
                agencyNeighborhood
        );
    }

    private CashRegisterAssignedCashierResponse mapAssignedCashier(Row row) {
        String cashierUserName = row.get("cashier_user_name", String.class);
        String cashierUserFirstName = row.get("cashier_user_first_name", String.class);
        if (!StringUtils.hasText(cashierUserName) && !StringUtils.hasText(cashierUserFirstName)) {
            return null;
        }
        return new CashRegisterAssignedCashierResponse(cashierUserName, cashierUserFirstName);
    }

    private CashRegisterSessionSummaryResponse mapSessionSummary(Row row) {
        String state = row.get("session_state", String.class);
        LocalDateTime openOn = row.get("session_open_on", LocalDateTime.class);
        BigDecimal initialFunds = row.get("session_initial_funds", BigDecimal.class);
        BigDecimal closeFunds = row.get("session_close_funds", BigDecimal.class);

        if (!StringUtils.hasText(state) && openOn == null && initialFunds == null && closeFunds == null) {
            return null;
        }
        return new CashRegisterSessionSummaryResponse(
                row.get("session_id", String.class),
                state,
                openOn,
                initialFunds,
                closeFunds
        );
    }

    private CashRegisterSessionDetailResponse mapSessionRow(Row row, RowMetadata metadata) {
        CashRegisterSessionDetailResponse session = new CashRegisterSessionDetailResponse();
        session.setId(row.get("id", String.class));
        session.setState(row.get("state", String.class));
        session.setOpenOn(row.get("open_on", LocalDateTime.class));
        session.setCloseOn(row.get("close_on", LocalDateTime.class));
        session.setTheoricalInitialFunds(row.get("theorical_initial_funds", BigDecimal.class));
        session.setTheoricalCloseFunds(row.get("theorical_close_funds", BigDecimal.class));
        session.setIsLocked(Boolean.TRUE.equals(row.get("is_locked", Boolean.class)));

        CashRegisterUserResponse opener = mapUser(
                row.get("opener_user_name", String.class),
                row.get("opener_user_first_name", String.class)
        );
        CashRegisterUserResponse closer = mapUser(
                row.get("closer_user_name", String.class),
                row.get("closer_user_first_name", String.class)
        );
        session.setOpener(opener);
        session.setCloser(closer);
        session.setMovements(new ArrayList<>());
        session.setTicketingDetails(new ArrayList<>());
        return session;
    }

    private CashRegisterMovementResponse mapMovementRow(Row row, RowMetadata metadata) {
        CashRegisterUserResponse creator = mapUser(
                row.get("creator_user_name", String.class),
                row.get("creator_user_first_name", String.class)
        );
        return new CashRegisterMovementResponse(
                row.get("id", String.class),
                row.get("sense", String.class),
                row.get("amount", BigDecimal.class),
                row.get("reason", String.class),
                row.get("create_on", LocalDateTime.class),
                creator
        );
    }

    private CashRegisterTicketingDetailResponse mapTicketingRow(Row row, RowMetadata metadata) {
        CashRegisterTicketingDenominationResponse denomination = null;
        BigDecimal denominationValue = row.get("denomination_value", BigDecimal.class);
        String denominationLabel = row.get("denomination_label", String.class);
        if (denominationValue != null || StringUtils.hasText(denominationLabel)) {
            denomination = new CashRegisterTicketingDenominationResponse(denominationValue, denominationLabel);
        }

        return new CashRegisterTicketingDetailResponse(
                row.get("id", String.class),
                row.get("connection_type", String.class),
                row.get("quantity", Integer.class),
                row.get("value", BigDecimal.class),
                row.get("total", BigDecimal.class),
                denomination
        );
    }

    private CashRegisterReconciliationResponse mapReconciliationRow(Row row, RowMetadata metadata) {
        return new CashRegisterReconciliationResponse(
                row.get("theorical_total", BigDecimal.class),
                row.get("physical_total", BigDecimal.class),
                row.get("difference", BigDecimal.class),
                row.get("justification", String.class)
        );
    }

    private CashRegisterUserResponse mapUser(String userName, String userFirstName) {
        if (!StringUtils.hasText(userName) && !StringUtils.hasText(userFirstName)) {
            return null;
        }
        return new CashRegisterUserResponse(userName, userFirstName);
    }

    private CashRegisterResponse ensureSessions(CashRegisterResponse response) {
        if (response.getSessions() == null) {
            response.setSessions(new ArrayList<>());
        }
        return response;
    }

    private boolean hasTicketingLines(Map<String, Integer> denominations) {
        if (denominations == null || denominations.isEmpty()) {
            return false;
        }
        return denominations.values().stream()
                .anyMatch(quantity -> quantity != null && quantity > 0);
    }

    private List<String> parseTownList(String townListChosen) {
        if (!StringUtils.hasText(townListChosen)) {
            return new ArrayList<>();
        }
        try {
            return objectMapper.readValue(townListChosen, new TypeReference<List<String>>() {});
        } catch (JsonProcessingException ex) {
            return new ArrayList<>();
        }
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private record RegisterInfo(String id, String town, String agencyId) {
    }

    private record CashierTownProfile(String townListChosen, String workTown) {
    }

    private record DenominationInfo(String id, BigDecimal value) {
    }
}
