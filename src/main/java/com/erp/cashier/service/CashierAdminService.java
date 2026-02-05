package com.erp.cashier.service;

import com.erp.cashier.dto.BaseAgencyResponse;
import com.erp.cashier.dto.CashierProfileDetailResponse;
import com.erp.cashier.dto.CashierProfileResponse;
import com.erp.cashier.dto.CashierResponse;
import com.erp.cashier.dto.CashierWithProfileResponse;
import com.erp.cashier.dto.CreateCashierRequest;
import com.erp.cashier.dto.UpdateCashierRequest;
import com.erp.cashier.model.Agency;
import com.erp.cashier.model.CashierProfile;
import com.erp.cashier.model.Person;
import com.erp.cashier.repository.AgencyRepository;
import com.erp.cashier.repository.CashierProfileRepository;
import com.erp.cashier.repository.PersonRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.r2dbc.spi.Row;
import io.r2dbc.spi.RowMetadata;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
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
 * Admin service for managing cashiers.
 *
 * @author ERP Cashier Team
 * @since 2025-01-02
 */
@Service
public class CashierAdminService {
    private static final String STATE_OPEN = "ouverte";

    private final PersonRepository personRepository;
    private final CashierProfileRepository cashierProfileRepository;
    private final AgencyRepository agencyRepository;
    private final R2dbcEntityTemplate entityTemplate;
    private final PasswordService passwordService;
    private final ObjectMapper objectMapper;
    private final TransactionalOperator transactionalOperator;

    /**
     * Creates the cashier admin service.
     *
     * @param personRepository person repository
     * @param cashierProfileRepository cashier profile repository
     * @param agencyRepository agency repository
     * @param entityTemplate entity template
     * @param passwordService password service
     * @param objectMapper object mapper
     * @param transactionManager reactive transaction manager
     */
    public CashierAdminService(
            PersonRepository personRepository,
            CashierProfileRepository cashierProfileRepository,
            AgencyRepository agencyRepository,
            R2dbcEntityTemplate entityTemplate,
            PasswordService passwordService,
            ObjectMapper objectMapper,
            ReactiveTransactionManager transactionManager
    ) {
        this.personRepository = personRepository;
        this.cashierProfileRepository = cashierProfileRepository;
        this.agencyRepository = agencyRepository;
        this.entityTemplate = entityTemplate;
        this.passwordService = passwordService;
        this.objectMapper = objectMapper;
        this.transactionalOperator = TransactionalOperator.create(transactionManager);
    }

    /**
     * Lists cashiers for admin users.
     *
     * @param includeBlocked include blocked sessions
     * @param organizationId organization identifier when scoped
     * @param agencyId agency identifier when scoped
     * @param restrictToAgency true when the caller is an agency admin
     * @param restrictToOrganization true when the caller is an organization admin
     * @return cashiers
     */
    public Flux<CashierResponse> listCashiers(
            boolean includeBlocked,
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
        sql.append("WITH active_assignment AS (");
        sql.append("SELECT DISTINCT ON (cashier_id) cashier_id, agency_id ");
        sql.append("FROM cashier_agency_assignment ");
        sql.append("WHERE (start_on IS NULL OR start_on <= CURRENT_TIMESTAMP) ");
        sql.append("AND (end_on IS NULL OR end_on >= CURRENT_TIMESTAMP) ");
        sql.append("AND assigned_by IS NOT NULL ");
        sql.append("ORDER BY cashier_id, assigned_on DESC");
        sql.append(") ");
        sql.append("SELECT p.id, p.user_first_name, p.user_name, p.phone, p.country, ");
        sql.append("cp.town_list_chosen, cp.work_town, cp.hire_date ");
        sql.append("FROM person p ");
        sql.append("JOIN cashier_profile cp ON cp.person_id = p.id ");
        sql.append("LEFT JOIN agency ba ON ba.id = cp.base_agency_id ");
        sql.append("LEFT JOIN active_assignment aa ON aa.cashier_id = p.id ");
        sql.append("LEFT JOIN agency aa_ag ON aa_ag.id = aa.agency_id ");
        sql.append("WHERE 1=1 ");
        if (!includeBlocked) {
            sql.append("AND NOT EXISTS (");
            sql.append("SELECT 1 FROM cash_register_session s ");
            sql.append("WHERE s.open_by = p.id ");
            sql.append("AND (s.state = :openState OR s.is_locked = true)");
            sql.append(") ");
        }
        if (restrictToAgency) {
            sql.append("AND (aa.agency_id = :agencyId ");
            sql.append("OR (aa.cashier_id IS NULL AND cp.base_agency_id = :agencyId)) ");
        }
        if (restrictToOrganization) {
            sql.append("AND (ba.organization_id = :organizationId ");
            sql.append("OR aa_ag.organization_id = :organizationId) ");
        }
        sql.append("ORDER BY p.user_first_name ASC");

        DatabaseClient.GenericExecuteSpec spec = entityTemplate.getDatabaseClient().sql(sql.toString());
        if (!includeBlocked) {
            spec = spec.bind("openState", STATE_OPEN);
        }
        if (restrictToAgency) {
            spec = spec.bind("agencyId", agencyId);
        }
        if (restrictToOrganization) {
            spec = spec.bind("organizationId", organizationId);
        }
        return spec.map(this::mapCashierRow).all();
    }

    /**
     * Lists cashiers available for assignment within a date range.
     *
     * @param startOn start date (YYYY-MM-DD)
     * @param endOn end date (YYYY-MM-DD)
     * @param organizationId organization identifier when scoped
     * @param agencyId agency identifier when scoped
     * @param restrictToOrganization true when the caller is scoped to an organization
     * @param restrictToAgency true when the caller is scoped to an agency
     * @return available cashiers
     */
    public Flux<CashierResponse> listAvailableCashiers(
            String startOn,
            String endOn,
            String organizationId,
            String agencyId,
            boolean restrictToOrganization,
            boolean restrictToAgency
    ) {
        LocalDateTime startDate = parseDateBoundary(startOn, "start_on");
        LocalDateTime endDate = parseDateBoundary(endOn, "end_on");
        if (endDate.isBefore(startDate)) {
            return Flux.error(new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "end_on must be after or equal to start_on."
            ));
        }
        if (restrictToOrganization && !StringUtils.hasText(organizationId)) {
            return Flux.error(new ResponseStatusException(HttpStatus.FORBIDDEN, "Organization scope is required."));
        }
        if (restrictToAgency && !StringUtils.hasText(agencyId)) {
            return Flux.error(new ResponseStatusException(HttpStatus.FORBIDDEN, "Agency scope is required."));
        }

        StringBuilder sql = new StringBuilder();
        sql.append("SELECT p.id, p.user_first_name, p.user_name, p.phone, p.country, ");
        sql.append("cp.town_list_chosen, cp.work_town, cp.hire_date ");
        sql.append("FROM person p ");
        sql.append("JOIN cashier_profile cp ON cp.person_id = p.id ");
        sql.append("LEFT JOIN agency ba ON ba.id = cp.base_agency_id ");
        sql.append("WHERE p.actif = true AND cp.is_active = true ");
        sql.append("AND NOT EXISTS (");
        sql.append("SELECT 1 FROM cash_register_session s ");
        sql.append("WHERE s.open_by = p.id ");
        sql.append("AND (s.state = :openState OR s.is_locked = true)");
        sql.append(") ");
        sql.append("AND NOT EXISTS (");
        sql.append("SELECT 1 FROM cashier_agency_assignment ca ");
        sql.append("WHERE ca.cashier_id = p.id ");
        sql.append("AND ca.assigned_by IS NOT NULL ");
        sql.append("AND (ca.start_on IS NULL OR ca.start_on <= :endOn) ");
        sql.append("AND (ca.end_on IS NULL OR ca.end_on >= :startOn) ");
        sql.append(") ");
        if (restrictToAgency) {
            sql.append("AND cp.base_agency_id = :agencyId ");
        }
        if (restrictToOrganization) {
            sql.append("AND ba.organization_id = :organizationId ");
        }
        sql.append("ORDER BY p.user_first_name ASC");

        DatabaseClient.GenericExecuteSpec spec = entityTemplate.getDatabaseClient()
                .sql(sql.toString())
                .bind("openState", STATE_OPEN)
                .bind("startOn", startDate)
                .bind("endOn", endDate);
        if (restrictToAgency) {
            spec = spec.bind("agencyId", agencyId);
        }
        if (restrictToOrganization) {
            spec = spec.bind("organizationId", organizationId);
        }
        return spec.map(this::mapCashierRow).all();
    }

    /**
     * Lists cashiers with profile details.
     *
     * @param organizationId organization identifier when scoped
     * @param agencyId agency identifier when scoped
     * @param restrictToAgency true when the caller is an agency admin
     * @param restrictToOrganization true when the caller is an organization admin
     * @return cashiers with profile details
     */
    public Flux<CashierWithProfileResponse> listCashiersWithProfile(
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
        sql.append("WITH active_assignment AS (");
        sql.append("SELECT DISTINCT ON (cashier_id) cashier_id, agency_id ");
        sql.append("FROM cashier_agency_assignment ");
        sql.append("WHERE (start_on IS NULL OR start_on <= CURRENT_TIMESTAMP) ");
        sql.append("AND (end_on IS NULL OR end_on >= CURRENT_TIMESTAMP) ");
        sql.append("AND assigned_by IS NOT NULL ");
        sql.append("ORDER BY cashier_id, assigned_on DESC");
        sql.append(") ");
        sql.append("SELECT p.id, p.user_first_name, p.user_name, p.phone, p.country, ");
        sql.append("cp.town_list_chosen, cp.work_town, cp.hire_date, ");
        sql.append("cp.base_agency_id, ba.name AS base_agency_name, ba.town AS base_agency_town, ");
        sql.append("ba.organization_id AS base_organization_id ");
        sql.append("FROM person p ");
        sql.append("JOIN cashier_profile cp ON cp.person_id = p.id ");
        sql.append("LEFT JOIN agency ba ON ba.id = cp.base_agency_id ");
        sql.append("LEFT JOIN active_assignment aa ON aa.cashier_id = p.id ");
        sql.append("LEFT JOIN agency aa_ag ON aa_ag.id = aa.agency_id ");
        sql.append("WHERE 1=1 ");
        if (restrictToAgency) {
            sql.append("AND (aa.agency_id = :agencyId ");
            sql.append("OR (aa.cashier_id IS NULL AND cp.base_agency_id = :agencyId)) ");
        }
        if (restrictToOrganization) {
            sql.append("AND (ba.organization_id = :organizationId ");
            sql.append("OR aa_ag.organization_id = :organizationId) ");
        }
        sql.append("ORDER BY p.user_first_name ASC");

        DatabaseClient.GenericExecuteSpec spec = entityTemplate.getDatabaseClient().sql(sql.toString());
        if (restrictToAgency) {
            spec = spec.bind("agencyId", agencyId);
        }
        if (restrictToOrganization) {
            spec = spec.bind("organizationId", organizationId);
        }
        return spec.map(this::mapCashierProfileRow).all();
    }

    /**
     * Creates a new cashier.
     *
     * @param request create request
     * @return created cashier
     */
    public Mono<CashierResponse> createCashier(CreateCashierRequest request) {
        if (request == null) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cashier payload is required."));
        }
        String userName = trimToNull(request.getUserName());
        if (!StringUtils.hasText(userName)) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "user_name is required"));
        }
        String userFirstName = trimToNull(request.getUserFirstName());
        if (!StringUtils.hasText(userFirstName)) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "user_first_name is required"));
        }
        String rawPassword = trimToNull(request.getPassword());
        if (!StringUtils.hasText(rawPassword)) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "password is required"));
        }
        String workTown = trimToNull(request.getWorkTown());
        if (!StringUtils.hasText(workTown)) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "work_town is required"));
        }
        String townListChosen = trimToNull(request.getTownListChosen());
        if (!StringUtils.hasText(townListChosen)) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "town_list_chosen is required"));
        }
        List<String> towns = parseTownList(townListChosen);
        if (!towns.contains(workTown)) {
            return Mono.error(new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "work_town must be included in town_list_chosen"
            ));
        }

        Person person = new Person();
        person.setId(UUID.randomUUID().toString());
        person.setUserName(userName);
        person.setUserFirstName(userFirstName);
        person.setPassword(passwordService.hashPassword(rawPassword));
        person.setMail(trimToNull(request.getMail()));
        person.setCountry(trimToNull(request.getCountry()));
        person.setPhone(trimToNull(request.getPhone()));
        person.setActif(true);

        CashierProfile profile = new CashierProfile();
        profile.setId(UUID.randomUUID().toString());
        profile.setPersonId(person.getId());
        profile.setTownListChosen(townListChosen);
        profile.setWorkTown(workTown);
        profile.setHireDate(parseHireDate(request.getHireDate()));
        profile.setIsActive(true);

        Mono<CashierResponse> insertFlow = entityTemplate.insert(Person.class)
                .using(person)
                .flatMap(saved ->
                        entityTemplate.insert(CashierProfile.class)
                                .using(profile)
                                .map(savedProfile -> toResponse(saved, savedProfile))
                )
                .onErrorMap(DuplicateKeyException.class,
                        ex -> new ResponseStatusException(HttpStatus.CONFLICT, "Cashier already exists.", ex));

        return transactionalOperator.transactional(insertFlow);
    }

    /**
     * Updates a cashier.
     *
     * @param cashierId cashier identifier
     * @param request update request
     * @return updated cashier
     */
    public Mono<CashierResponse> updateCashier(String cashierId, UpdateCashierRequest request) {
        return updateCashierInternal(cashierId, request, null, false)
                .map(updated -> toResponse(updated.person(), updated.profile()));
    }

    /**
     * Updates a cashier with profile details.
     *
     * @param cashierId cashier identifier
     * @param request update request
     * @param organizationScope organization identifier when scoped
     * @param restrictToOrganization true when the caller is an organization admin
     * @return updated cashier with profile details
     */
    public Mono<CashierWithProfileResponse> updateCashierWithProfile(
            String cashierId,
            UpdateCashierRequest request,
            String organizationScope,
            boolean restrictToOrganization
    ) {
        return updateCashierInternal(cashierId, request, organizationScope, restrictToOrganization)
                .map(updated -> toWithProfileResponse(updated.person(), updated.profile(), updated.baseAgency()));
    }

    /**
     * Deletes a cashier by disabling the cashier profile.
     *
     * @param cashierId cashier identifier
     * @return completion signal
     */
    public Mono<Void> deleteCashier(String cashierId) {
        return deleteCashier(cashierId, null, false);
    }

    /**
     * Deletes a cashier by disabling the cashier profile.
     *
     * @param cashierId cashier identifier
     * @param organizationScope organization identifier when scoped
     * @param restrictToOrganization true when the caller is an organization admin
     * @return completion signal
     */
    public Mono<Void> deleteCashier(
            String cashierId,
            String organizationScope,
            boolean restrictToOrganization
    ) {
        Mono<Void> deleteFlow = personRepository.findById(cashierId)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Cashier not found")))
                .flatMap(person -> cashierProfileRepository.findByPersonId(cashierId)
                        .switchIfEmpty(Mono.error(new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Cashier not found"
                        )))
                        .flatMap(profile -> resolveBaseAgency(profile)
                                .defaultIfEmpty(new Agency())
                                .flatMap(baseAgency -> {
                                    if (restrictToOrganization) {
                                        if (!StringUtils.hasText(organizationScope)) {
                                            return Mono.error(new ResponseStatusException(
                                                    HttpStatus.FORBIDDEN,
                                                    "Forbidden"
                                            ));
                                        }
                                        if (!StringUtils.hasText(baseAgency.getOrganizationId())) {
                                            return Mono.error(new ResponseStatusException(
                                                    HttpStatus.FORBIDDEN,
                                                    "Forbidden"
                                            ));
                                        }
                                        if (!organizationScope.equals(baseAgency.getOrganizationId())) {
                                            return Mono.error(new ResponseStatusException(
                                                    HttpStatus.FORBIDDEN,
                                                    "Forbidden"
                                            ));
                                        }
                                    }
                                    profile.setIsActive(false);
                                    return entityTemplate.update(profile).then();
                                })
                        )
                );

        return transactionalOperator.transactional(deleteFlow);
    }

    private CashierResponse mapCashierRow(Row row, RowMetadata metadata) {
        CashierProfileResponse profile = new CashierProfileResponse(
                row.get("town_list_chosen", String.class),
                row.get("work_town", String.class),
                row.get("hire_date", LocalDateTime.class)
        );
        return new CashierResponse(
                row.get("id", String.class),
                row.get("user_name", String.class),
                row.get("user_first_name", String.class),
                row.get("phone", String.class),
                null,
                row.get("country", String.class),
                profile
        );
    }

    private CashierWithProfileResponse mapCashierProfileRow(Row row, RowMetadata metadata) {
        String baseAgencyId = row.get("base_agency_id", String.class);
        BaseAgencyResponse baseAgency = null;
        if (StringUtils.hasText(baseAgencyId)) {
            baseAgency = new BaseAgencyResponse(
                    baseAgencyId,
                    row.get("base_agency_name", String.class),
                    row.get("base_agency_town", String.class)
            );
        }
        CashierProfileDetailResponse profile = new CashierProfileDetailResponse(
                row.get("town_list_chosen", String.class),
                row.get("work_town", String.class),
                row.get("hire_date", LocalDateTime.class),
                baseAgencyId,
                row.get("base_organization_id", String.class),
                baseAgency
        );
        return new CashierWithProfileResponse(
                row.get("id", String.class),
                row.get("user_name", String.class),
                row.get("user_first_name", String.class),
                row.get("phone", String.class),
                null,
                row.get("country", String.class),
                profile
        );
    }

    private CashierResponse toResponse(Person person, CashierProfile profile) {
        CashierProfileResponse profileResponse = new CashierProfileResponse(
                profile.getTownListChosen(),
                profile.getWorkTown(),
                profile.getHireDate()
        );
        return new CashierResponse(
                person.getId(),
                person.getUserName(),
                person.getUserFirstName(),
                person.getPhone(),
                null,
                person.getCountry(),
                profileResponse
        );
    }

    private Mono<UpdatedCashier> updateCashierInternal(
            String cashierId,
            UpdateCashierRequest request,
            String organizationScope,
            boolean restrictToOrganization
    ) {
        if (request == null) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cashier payload is required."));
        }
        Mono<Person> personMono = personRepository.findById(cashierId)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Cashier not found")));
        Mono<CashierProfile> profileMono = cashierProfileRepository.findByPersonId(cashierId)
                .switchIfEmpty(Mono.error(new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Cashier not found"
                )));

        return personMono.zipWith(profileMono)
                .flatMap(tuple -> {
                    Person person = tuple.getT1();
                    CashierProfile profile = tuple.getT2();

                    String baseAgencyId = resolveBaseAgencyId(request, profile);
                    if (!StringUtils.hasText(baseAgencyId)) {
                        return Mono.error(new ResponseStatusException(
                                HttpStatus.BAD_REQUEST,
                                "base_agency_id is required"
                        ));
                    }

                    return agencyRepository.findById(baseAgencyId)
                            .switchIfEmpty(Mono.error(new ResponseStatusException(
                                    HttpStatus.BAD_REQUEST,
                                    "base_agency_id is required"
                            )))
                            .flatMap(baseAgency -> {
                                if (restrictToOrganization) {
                                    if (!StringUtils.hasText(organizationScope)) {
                                        return Mono.error(new ResponseStatusException(
                                                HttpStatus.FORBIDDEN,
                                                "Forbidden"
                                        ));
                                    }
                                    if (!organizationScope.equals(baseAgency.getOrganizationId())) {
                                        return Mono.error(new ResponseStatusException(
                                                HttpStatus.FORBIDDEN,
                                                "Forbidden"
                                        ));
                                    }
                                }

                                String effectiveWorkTown = resolveWorkTown(request, profile, baseAgency);
                                String baseTown = trimToNull(baseAgency.getTown());
                                if (StringUtils.hasText(baseTown)
                                        && StringUtils.hasText(effectiveWorkTown)
                                        && !baseTown.equals(effectiveWorkTown)) {
                                    return Mono.error(new ResponseStatusException(
                                            HttpStatus.BAD_REQUEST,
                                            "Base agency must be in the work town."
                                    ));
                                }

                                String townListChosen = trimToNull(request.getTownListChosen());
                                if (StringUtils.hasText(townListChosen)) {
                                    parseTownList(townListChosen);
                                    profile.setTownListChosen(townListChosen);
                                }

                                if (StringUtils.hasText(effectiveWorkTown)) {
                                    profile.setWorkTown(effectiveWorkTown);
                                }

                                profile.setBaseAgencyId(baseAgencyId);

                                String hireDate = trimToNull(request.getHireDate());
                                if (StringUtils.hasText(hireDate)) {
                                    profile.setHireDate(parseHireDate(hireDate));
                                }

                                String userName = trimToNull(request.getUserName());
                                if (StringUtils.hasText(userName)) {
                                    person.setUserName(userName);
                                }
                                String userFirstName = trimToNull(request.getUserFirstName());
                                if (StringUtils.hasText(userFirstName)) {
                                    person.setUserFirstName(userFirstName);
                                }
                                if (request.getCountry() != null) {
                                    String country = trimToNull(request.getCountry());
                                    if (StringUtils.hasText(country)) {
                                        person.setCountry(country);
                                    }
                                }

                                Mono<Person> updatePerson = entityTemplate.update(person);
                                Mono<CashierProfile> updateProfile = entityTemplate.update(profile);
                                return updatePerson.zipWith(updateProfile,
                                        (updatedPerson, updatedProfile) ->
                                                new UpdatedCashier(updatedPerson, updatedProfile, baseAgency));
                            });
                })
                .as(transactionalOperator::transactional);
    }

    private String resolveBaseAgencyId(UpdateCashierRequest request, CashierProfile profile) {
        String requested = trimToNull(request.getBaseAgencyId());
        if (StringUtils.hasText(requested)) {
            return requested;
        }
        return trimToNull(profile.getBaseAgencyId());
    }

    private String resolveWorkTown(UpdateCashierRequest request, CashierProfile profile, Agency baseAgency) {
        String requested = trimToNull(request.getWorkTown());
        if (StringUtils.hasText(requested)) {
            return requested;
        }
        String existing = trimToNull(profile.getWorkTown());
        if (StringUtils.hasText(existing)) {
            return existing;
        }
        return baseAgency != null ? trimToNull(baseAgency.getTown()) : null;
    }

    private Mono<Agency> resolveBaseAgency(CashierProfile profile) {
        String baseAgencyId = trimToNull(profile.getBaseAgencyId());
        if (!StringUtils.hasText(baseAgencyId)) {
            return Mono.empty();
        }
        return agencyRepository.findById(baseAgencyId);
    }

    private CashierWithProfileResponse toWithProfileResponse(
            Person person,
            CashierProfile profile,
            Agency baseAgency
    ) {
        String baseAgencyId = trimToNull(profile.getBaseAgencyId());
        BaseAgencyResponse baseAgencyResponse = null;
        String organizationId = null;
        if (baseAgency != null) {
            baseAgencyResponse = new BaseAgencyResponse(
                    baseAgency.getId(),
                    baseAgency.getName(),
                    baseAgency.getTown()
            );
            organizationId = baseAgency.getOrganizationId();
        }
        CashierProfileDetailResponse profileResponse = new CashierProfileDetailResponse(
                profile.getTownListChosen(),
                profile.getWorkTown(),
                profile.getHireDate(),
                baseAgencyId,
                organizationId,
                baseAgencyResponse
        );
        return new CashierWithProfileResponse(
                person.getId(),
                person.getUserName(),
                person.getUserFirstName(),
                person.getPhone(),
                null,
                person.getCountry(),
                profileResponse
        );
    }

    private record UpdatedCashier(Person person, CashierProfile profile, Agency baseAgency) {
    }

    private LocalDateTime parseDateBoundary(String value, String fieldName) {
        if (!StringUtils.hasText(value)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, fieldName + " is required.");
        }
        try {
            return LocalDate.parse(value).atStartOfDay();
        } catch (DateTimeParseException ex) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    fieldName + " must be in YYYY-MM-DD format.",
                    ex
            );
        }
    }

    private List<String> parseTownList(String townListChosen) {
        try {
            List<String> towns = objectMapper.readValue(townListChosen, new TypeReference<List<String>>() {});
            if (towns == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "town_list_chosen must be valid JSON");
            }
            return towns;
        } catch (JsonProcessingException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "town_list_chosen must be valid JSON", ex);
        }
    }

    private LocalDateTime parseHireDate(String hireDate) {
        if (!StringUtils.hasText(hireDate)) {
            return null;
        }
        String trimmed = hireDate.trim();
        try {
            return LocalDateTime.parse(trimmed);
        } catch (DateTimeParseException ex) {
            try {
                return LocalDate.parse(trimmed).atStartOfDay();
            } catch (DateTimeParseException inner) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "hire_date must be a valid date",
                        inner
                );
            }
        }
    }

    private String trimToNull(String value) {
        String trimmed = StringUtils.trimWhitespace(value);
        return StringUtils.hasText(trimmed) ? trimmed : null;
    }
}
