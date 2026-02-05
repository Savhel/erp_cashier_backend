package com.erp.cashier.service;

import com.erp.cashier.dto.AdminProfileResponse;
import com.erp.cashier.dto.AdminUserResponse;
import com.erp.cashier.dto.AgencySummaryResponse;
import com.erp.cashier.dto.CreateOrganizationAdminRequest;
import com.erp.cashier.dto.CreateOrganizationRequest;
import com.erp.cashier.dto.OrganizationCreatorResponse;
import com.erp.cashier.dto.OrganizationResponse;
import com.erp.cashier.dto.OrganizationSummaryResponse;
import com.erp.cashier.dto.UpdateOrganizationAdminRequest;
import com.erp.cashier.dto.UpdateOrganizationRequest;
import com.erp.cashier.model.Agency;
import com.erp.cashier.model.AdminProfile;
import com.erp.cashier.model.Organization;
import com.erp.cashier.model.Person;
import com.erp.cashier.repository.AdminProfileRepository;
import com.erp.cashier.repository.AgencyRepository;
import com.erp.cashier.repository.OrganizationRepository;
import com.erp.cashier.repository.PersonRepository;
import io.r2dbc.spi.Row;
import io.r2dbc.spi.RowMetadata;
import java.time.LocalDateTime;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.ReactiveTransactionManager;
import org.springframework.transaction.reactive.TransactionalOperator;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Admin management service for organizations and admins.
 *
 * @author ERP Cashier Team
 * @since 2025-01-02
 */
@Service
public class SuperAdminService {
    private static final String ROLE_ORGANIZATION_ADMIN = "organization_admin";
    private static final String ROLE_AGENCY_ADMIN = "agency_admin";
    private static final String ROLE_SUPERADMIN = "superadmin";

    private final OrganizationRepository organizationRepository;
    private final PersonRepository personRepository;
    private final AdminProfileRepository adminProfileRepository;
    private final AgencyRepository agencyRepository;
    private final R2dbcEntityTemplate entityTemplate;
    private final TransactionalOperator transactionalOperator;

    /**
     * Creates the super admin service.
     *
     * @param organizationRepository organization repository
     * @param personRepository person repository
     * @param adminProfileRepository admin profile repository
     * @param agencyRepository agency repository
     * @param entityTemplate entity template
     * @param transactionManager reactive transaction manager
     */
    public SuperAdminService(
            OrganizationRepository organizationRepository,
            PersonRepository personRepository,
            AdminProfileRepository adminProfileRepository,
            AgencyRepository agencyRepository,
            R2dbcEntityTemplate entityTemplate,
            ReactiveTransactionManager transactionManager
    ) {
        this.organizationRepository = organizationRepository;
        this.personRepository = personRepository;
        this.adminProfileRepository = adminProfileRepository;
        this.agencyRepository = agencyRepository;
        this.entityTemplate = entityTemplate;
        this.transactionalOperator = TransactionalOperator.create(transactionManager);
    }

    /**
     * Lists all organizations.
     *
     * @return organization responses
     */
    public Flux<OrganizationResponse> listOrganizations() {
        String sql = "SELECT o.id AS organization_id, o.name, o.country, o.description, o.is_active, "
                + "o.create_on, o.create_by, o.telegram_bot_token, "
                + "p.id AS creator_id, p.user_name AS creator_user_name, "
                + "p.user_first_name AS creator_user_first_name "
                + "FROM organization o "
                + "LEFT JOIN person p ON p.id = o.create_by "
                + "ORDER BY o.create_on DESC";

        DatabaseClient client = entityTemplate.getDatabaseClient();
        return client.sql(sql)
                .map(this::mapOrganizationRow)
                .all();
    }

    /**
     * Creates a new organization.
     *
     * @param request create organization request
     * @param createdBy creator identifier
     * @return created organization response
     */
    public Mono<OrganizationResponse> createOrganization(CreateOrganizationRequest request, String createdBy) {
        if (request == null) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Organization payload is required."));
        }
        String name = trimToNull(request.getName());
        if (!StringUtils.hasText(name)) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Organization name is required."));
        }
        Organization organization = new Organization();
        organization.setId(UUID.randomUUID().toString());
        organization.setName(name);
        organization.setCountry(trimToNull(request.getCountry()));
        organization.setDescription(trimToNull(request.getDescription()));
        organization.setTelegramBotToken(trimToNull(request.getTelegramBotToken()));
        organization.setIsActive(Boolean.TRUE.equals(request.getIsActive()) || request.getIsActive() == null);
        organization.setCreateOn(LocalDateTime.now());
        organization.setCreateBy(trimToNull(createdBy));

        return entityTemplate.insert(Organization.class)
                .using(organization)
                .map(this::toResponse)
                .onErrorMap(DuplicateKeyException.class,
                        ex -> new ResponseStatusException(HttpStatus.CONFLICT, "Organization already exists.", ex));
    }

    /**
     * Updates an organization.
     *
     * @param organizationId organization identifier
     * @param request update request
     * @return updated organization response
     */
    public Mono<OrganizationResponse> updateOrganization(String organizationId, UpdateOrganizationRequest request) {
        if (request == null) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Organization payload is required."));
        }
        String name = trimToNull(request.getName());
        if (request.getName() != null && !StringUtils.hasText(name)) {
            return Mono.error(new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Organization name cannot be empty."
            ));
        }

        return organizationRepository.findById(organizationId)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Organization not found.")))
                .flatMap(existing -> {
                    if (StringUtils.hasText(name)) {
                        existing.setName(name);
                    }
                    if (request.getCountry() != null) {
                        existing.setCountry(trimToNull(request.getCountry()));
                    }
                    if (request.getDescription() != null) {
                        existing.setDescription(trimToNull(request.getDescription()));
                    }
                    if (request.getTelegramBotToken() != null) {
                        existing.setTelegramBotToken(trimToNull(request.getTelegramBotToken()));
                    }
                    if (request.getIsActive() != null) {
                        existing.setIsActive(request.getIsActive());
                    }
                    return entityTemplate.update(existing)
                            .map(this::toResponse);
                });
    }

    /**
     * Deletes an organization.
     *
     * @param organizationId organization identifier
     * @return completion signal
     */
    public Mono<Void> deleteOrganization(String organizationId) {
        return organizationRepository.findById(organizationId)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Organization not found.")))
                .flatMap(existing -> organizationRepository.deleteById(existing.getId()));
    }

    /**
     * Lists organization admins.
     *
     * @return organization admin responses
     */
    public Flux<AdminUserResponse> listOrganizationAdmins() {
        String sql = "SELECT p.id AS person_id, p.user_name, p.user_first_name, p.mail, "
                + "p.telegram_chat_id, p.country, p.phone, p.actif, "
                + "a.role_type, a.organization_id, a.agency_id, "
                + "o.id AS org_id, o.name AS org_name, o.country AS org_country, "
                + "o.telegram_bot_token AS org_telegram_bot_token "
                + "FROM person p "
                + "JOIN admin_profile a ON a.person_id = p.id "
                + "LEFT JOIN organization o ON o.id = a.organization_id "
                + "WHERE a.role_type = $1 "
                + "ORDER BY p.user_first_name ASC";

        DatabaseClient client = entityTemplate.getDatabaseClient();
        return client.sql(sql)
                .bind(0, ROLE_ORGANIZATION_ADMIN)
                .map(this::mapOrganizationAdminRow)
                .all();
    }

    /**
     * Lists all admins (organization and agency).
     *
     * @return admin responses
     */
    public Flux<AdminUserResponse> listAllAdmins() {
        String sql = "SELECT p.id AS person_id, p.user_name, p.user_first_name, p.mail, "
                + "p.telegram_chat_id, p.country, p.phone, p.actif, "
                + "a.role_type, a.organization_id AS admin_organization_id, a.agency_id AS admin_agency_id, "
                + "ag.id AS agency_id, ag.name AS agency_name, ag.country AS agency_country, "
                + "ag.town AS agency_town, ag.neighborhood AS agency_neighborhood, "
                + "ag.organization_id AS agency_organization_id, "
                + "o.id AS org_id, o.name AS org_name, o.country AS org_country, "
                + "o.telegram_bot_token AS org_telegram_bot_token "
                + "FROM person p "
                + "JOIN admin_profile a ON a.person_id = p.id "
                + "LEFT JOIN agency ag ON ag.id = a.agency_id "
                + "LEFT JOIN organization o ON o.id = COALESCE(a.organization_id, ag.organization_id) "
                + "WHERE a.role_type <> $1 "
                + "ORDER BY p.user_first_name ASC";

        DatabaseClient client = entityTemplate.getDatabaseClient();
        return client.sql(sql)
                .bind(0, ROLE_SUPERADMIN)
                .map(this::mapAgencyAdminRow)
                .all();
    }

    /**
     * Gets a single organization admin.
     *
     * @param personId person identifier
     * @return organization admin response
     */
    public Mono<AdminUserResponse> getOrganizationAdmin(String personId) {
        String sql = "SELECT p.id AS person_id, p.user_name, p.user_first_name, p.mail, "
                + "p.telegram_chat_id, p.country, p.phone, p.actif, "
                + "a.role_type, a.organization_id, a.agency_id, "
                + "o.id AS org_id, o.name AS org_name, o.country AS org_country, "
                + "o.telegram_bot_token AS org_telegram_bot_token "
                + "FROM person p "
                + "JOIN admin_profile a ON a.person_id = p.id "
                + "LEFT JOIN organization o ON o.id = a.organization_id "
                + "WHERE a.role_type = $1 AND p.id = $2";

        DatabaseClient client = entityTemplate.getDatabaseClient();
        return client.sql(sql)
                .bind(0, ROLE_ORGANIZATION_ADMIN)
                .bind(1, personId)
                .map(this::mapOrganizationAdminRow)
                .one()
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Admin not found.")));
    }

    /**
     * Creates an organization admin user.
     *
     * @param request create admin request
     * @return created admin response
     */
    public Mono<AdminUserResponse> createOrganizationAdmin(CreateOrganizationAdminRequest request) {
        if (request == null) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Admin payload is required."));
        }
        String organizationId = trimToNull(request.getOrganizationId());
        String personId = trimToNull(request.getPersonId());
        String phone = trimToNull(request.getPhone());

        if (!StringUtils.hasText(organizationId)) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Organization ID is required."));
        }
        if (!StringUtils.hasText(personId) && !StringUtils.hasText(phone)) {
            return Mono.error(new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Person ID or phone is required."
            ));
        }

        return organizationRepository.findById(organizationId)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Organization not found.")))
                .flatMap(organization -> resolvePerson(personId, phone))
                .flatMap(this::ensureNotSuperAdmin)
                .flatMap(person -> assignAdminProfile(person, ROLE_ORGANIZATION_ADMIN, organizationId, null))
                .flatMap(this::getOrganizationAdmin);
    }

    /**
     * Updates an organization admin user.
     *
     * @param personId person identifier
     * @param request update admin request
     * @return updated admin response
     */
    public Mono<AdminUserResponse> updateOrganizationAdmin(String personId, UpdateOrganizationAdminRequest request) {
        if (request == null) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Admin payload is required."));
        }
        String userName = trimToNull(request.getUserName());
        String userFirstName = trimToNull(request.getUserFirstName());
        String organizationId = trimToNull(request.getOrganizationId());
        String roleType = trimToNull(request.getRoleType());

        if (request.getUserName() != null && !StringUtils.hasText(userName)) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Username cannot be empty."));
        }
        if (request.getUserFirstName() != null && !StringUtils.hasText(userFirstName)) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "First name cannot be empty."));
        }
        if (request.getRoleType() != null && !StringUtils.hasText(roleType)) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Role type cannot be empty."));
        }
        if (roleType != null && !ROLE_ORGANIZATION_ADMIN.equals(roleType)) {
            return Mono.error(new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Only organization admins can be updated here."
            ));
        }
        if (request.getOrganizationId() != null && !StringUtils.hasText(organizationId)) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Organization ID cannot be empty."));
        }

        Mono<Person> personMono = personRepository.findById(personId)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Admin not found.")));

        Mono<AdminProfile> adminProfileMono = adminProfileRepository.findByPersonId(personId)
                .switchIfEmpty(Mono.error(new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Admin profile not found."
                )))
                .flatMap(profile -> {
                    if (!ROLE_ORGANIZATION_ADMIN.equals(profile.getRoleType())) {
                        return Mono.error(new ResponseStatusException(
                                HttpStatus.CONFLICT,
                                "Admin role type mismatch."
                        ));
                    }
                    return Mono.just(profile);
                });

        return personMono.flatMap(person -> adminProfileMono.flatMap(profile -> {
            boolean willBeActive = request.getActif() != null
                    ? request.getActif()
                    : Boolean.TRUE.equals(person.getActif());
            boolean wasActive = Boolean.TRUE.equals(person.getActif());
            boolean isDisabling = wasActive && !willBeActive;
            String currentOrganizationId = profile.getOrganizationId();

            Mono<Person> validatedPerson = validateUsernameChange(person, userName);
            Mono<Void> organizationValidation = validateOrganizationChange(organizationId).then();

            return validatedPerson.flatMap(candidate -> organizationValidation.thenReturn(candidate))
                    .flatMap(updatedPerson -> {
                        updatedPerson = applyPersonUpdates(updatedPerson, request, userName, userFirstName);
                        updatedPerson.setActif(willBeActive);

                        String effectiveRole = roleType != null ? roleType : profile.getRoleType();
                        profile.setRoleType(effectiveRole);
                        if (willBeActive) {
                            if (StringUtils.hasText(organizationId)) {
                                profile.setOrganizationId(organizationId);
                            }
                        } else {
                            profile.setOrganizationId(null);
                            profile.setAgencyId(null);
                        }

                        Mono<Void> updateFlow = entityTemplate.update(updatedPerson)
                                .then(entityTemplate.update(profile))
                                .then(updateOrganizationBotToken(
                                        StringUtils.hasText(organizationId) ? organizationId : currentOrganizationId,
                                        request.getOrganizationBotToken()
                                ))
                                .then(handleOrganizationAdminDisable(isDisabling, currentOrganizationId));

                        return transactionalOperator.transactional(updateFlow)
                                .then(getOrganizationAdmin(personId));
                    });
        }));
    }

    /**
     * Deletes an organization admin user.
     *
     * @param personId person identifier
     * @return completion signal
     */
    public Mono<Void> deleteOrganizationAdmin(String personId) {
        Mono<Person> personMono = personRepository.findById(personId)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Admin not found.")));

        Mono<AdminProfile> adminProfileMono = adminProfileRepository.findByPersonId(personId)
                .switchIfEmpty(Mono.error(new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Admin profile not found."
                )))
                .flatMap(profile -> {
                    if (!ROLE_ORGANIZATION_ADMIN.equals(profile.getRoleType())) {
                        return Mono.error(new ResponseStatusException(
                                HttpStatus.CONFLICT,
                                "Admin role type mismatch."
                        ));
                    }
                    return Mono.just(profile);
                });

        Mono<Void> deleteFlow = personMono.flatMap(person -> adminProfileMono.flatMap(profile -> {
            person.setActif(false);
            return entityTemplate.update(person)
                    .then(adminProfileRepository.deleteById(profile.getId()));
        }));

        return transactionalOperator.transactional(deleteFlow);
    }

    /**
     * Lists agency admins for an organization.
     *
     * @param organizationId organization identifier
     * @param agencyId agency identifier when scoped
     * @return agency admin responses
     */
    public Flux<AdminUserResponse> listAgencyAdmins(String organizationId, String agencyId) {
        if (!StringUtils.hasText(organizationId)) {
            return Flux.error(new ResponseStatusException(HttpStatus.FORBIDDEN, "Missing organization scope."));
        }
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT p.id AS person_id, p.user_name, p.user_first_name, p.mail, ");
        sql.append("p.telegram_chat_id, p.country, p.phone, p.actif, ");
        sql.append("a.role_type, a.organization_id AS admin_organization_id, a.agency_id AS admin_agency_id, ");
        sql.append("ag.id AS agency_id, ag.name AS agency_name, ag.country AS agency_country, ");
        sql.append("ag.town AS agency_town, ag.neighborhood AS agency_neighborhood, ");
        sql.append("ag.organization_id AS agency_organization_id, ");
        sql.append("o.id AS org_id, o.name AS org_name, o.country AS org_country, ");
        sql.append("o.telegram_bot_token AS org_telegram_bot_token ");
        sql.append("FROM person p ");
        sql.append("JOIN admin_profile a ON a.person_id = p.id ");
        sql.append("LEFT JOIN agency ag ON ag.id = a.agency_id ");
        sql.append("LEFT JOIN organization o ON o.id = COALESCE(a.organization_id, ag.organization_id) ");
        sql.append("WHERE a.role_type = $1 AND ag.organization_id = $2 ");
        if (StringUtils.hasText(agencyId)) {
            sql.append("AND ag.id = $3 ");
        }
        sql.append("ORDER BY p.user_first_name ASC");

        DatabaseClient client = entityTemplate.getDatabaseClient();
        DatabaseClient.GenericExecuteSpec spec = client.sql(sql.toString())
                .bind(0, ROLE_AGENCY_ADMIN)
                .bind(1, organizationId);
        if (StringUtils.hasText(agencyId)) {
            spec = spec.bind(2, agencyId);
        }
        return spec.map(this::mapAgencyAdminRow).all();
    }

    /**
     * Gets an agency admin.
     *
     * @param personId person identifier
     * @param organizationId organization identifier
     * @return agency admin response
     */
    public Mono<AdminUserResponse> getAgencyAdmin(String personId, String organizationId) {
        if (!StringUtils.hasText(organizationId)) {
            return Mono.error(new ResponseStatusException(HttpStatus.FORBIDDEN, "Missing organization scope."));
        }
        String sql = "SELECT p.id AS person_id, p.user_name, p.user_first_name, p.mail, "
                + "p.telegram_chat_id, p.country, p.phone, p.actif, "
                + "a.role_type, a.organization_id AS admin_organization_id, a.agency_id AS admin_agency_id, "
                + "ag.id AS agency_id, ag.name AS agency_name, ag.country AS agency_country, "
                + "ag.town AS agency_town, ag.neighborhood AS agency_neighborhood, "
                + "ag.organization_id AS agency_organization_id, "
                + "o.id AS org_id, o.name AS org_name, o.country AS org_country, "
                + "o.telegram_bot_token AS org_telegram_bot_token "
                + "FROM person p "
                + "JOIN admin_profile a ON a.person_id = p.id "
                + "LEFT JOIN agency ag ON ag.id = a.agency_id "
                + "LEFT JOIN organization o ON o.id = COALESCE(a.organization_id, ag.organization_id) "
                + "WHERE a.role_type = $1 AND p.id = $2 AND ag.organization_id = $3";

        DatabaseClient client = entityTemplate.getDatabaseClient();
        return client.sql(sql)
                .bind(0, ROLE_AGENCY_ADMIN)
                .bind(1, personId)
                .bind(2, organizationId)
                .map(this::mapAgencyAdminRow)
                .one()
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Admin not found.")));
    }

    /**
     * Creates a new agency admin user.
     *
     * @param request create admin request
     * @param organizationId organization identifier
     * @return created admin response
     */
    public Mono<AdminUserResponse> createAgencyAdmin(CreateOrganizationAdminRequest request, String organizationId) {
        if (request == null) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Admin payload is required."));
        }
        String agencyId = trimToNull(request.getAgencyId());
        String personId = trimToNull(request.getPersonId());
        String phone = trimToNull(request.getPhone());

        if (!StringUtils.hasText(organizationId)) {
            return Mono.error(new ResponseStatusException(HttpStatus.FORBIDDEN, "Missing organization scope."));
        }
        if (!StringUtils.hasText(agencyId)) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Agency ID is required."));
        }
        if (!StringUtils.hasText(personId) && !StringUtils.hasText(phone)) {
            return Mono.error(new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Person ID or phone is required."
            ));
        }

        return validateAgencyAssignment(agencyId, organizationId)
                .flatMap(agency -> resolvePerson(personId, phone))
                .flatMap(this::ensureNotSuperAdmin)
                .flatMap(person -> assignAdminProfile(person, ROLE_AGENCY_ADMIN, null, agencyId)
                        .flatMap(savedId -> setAgencyRequiresAdminAssignment(agencyId, false).thenReturn(savedId)))
                .flatMap(savedId -> getAgencyAdmin(savedId, organizationId));
    }

    /**
     * Updates an agency admin user.
     *
     * @param personId person identifier
     * @param request update admin request
     * @param organizationId organization identifier
     * @param agencyScopeId agency identifier when scoped
     * @return updated admin response
     */
    public Mono<AdminUserResponse> updateAgencyAdmin(
            String personId,
            UpdateOrganizationAdminRequest request,
            String organizationId,
            String agencyScopeId
    ) {
        if (request == null) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Admin payload is required."));
        }
        if (!StringUtils.hasText(organizationId)) {
            return Mono.error(new ResponseStatusException(HttpStatus.FORBIDDEN, "Missing organization scope."));
        }
        String userName = trimToNull(request.getUserName());
        String userFirstName = trimToNull(request.getUserFirstName());
        String agencyId = trimToNull(request.getAgencyId());
        String roleType = trimToNull(request.getRoleType());

        if (request.getUserName() != null && !StringUtils.hasText(userName)) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Username cannot be empty."));
        }
        if (request.getUserFirstName() != null && !StringUtils.hasText(userFirstName)) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "First name cannot be empty."));
        }
        if (request.getRoleType() != null && !StringUtils.hasText(roleType)) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Role type cannot be empty."));
        }
        if (roleType != null && !ROLE_AGENCY_ADMIN.equals(roleType)) {
            return Mono.error(new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Only agency admins can be updated here."
            ));
        }
        if (request.getAgencyId() != null && !StringUtils.hasText(agencyId)) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Agency ID cannot be empty."));
        }

        Mono<Person> personMono = personRepository.findById(personId)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Admin not found.")));

        Mono<AdminProfile> adminProfileMono = adminProfileRepository.findByPersonId(personId)
                .switchIfEmpty(Mono.error(new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Admin profile not found."
                )))
                .flatMap(profile -> {
                    if (!ROLE_AGENCY_ADMIN.equals(profile.getRoleType())) {
                        return Mono.error(new ResponseStatusException(
                                HttpStatus.CONFLICT,
                                "Admin role type mismatch."
                        ));
                    }
                    if (StringUtils.hasText(agencyScopeId)
                            && !agencyScopeId.equals(profile.getAgencyId())) {
                        return Mono.error(new ResponseStatusException(
                                HttpStatus.FORBIDDEN,
                                "Unauthorized for this agency."
                        ));
                    }
                    return ensureAgencyScope(profile, organizationId).thenReturn(profile);
                });

        return personMono.flatMap(person -> adminProfileMono.flatMap(profile -> {
            boolean willBeActive = request.getActif() != null
                    ? request.getActif()
                    : Boolean.TRUE.equals(person.getActif());
            boolean wasActive = Boolean.TRUE.equals(person.getActif());
            boolean isDisabling = wasActive && !willBeActive;
            String currentAgencyId = profile.getAgencyId();

            Mono<Person> validatedPerson = validateUsernameChange(person, userName);
            Mono<Void> agencyValidation = StringUtils.hasText(agencyId)
                    ? validateAgencyAssignment(agencyId, organizationId).then()
                    : Mono.empty();

            return validatedPerson.flatMap(candidate -> agencyValidation.thenReturn(candidate))
                    .flatMap(updatedPerson -> {
                        updatedPerson = applyPersonUpdates(updatedPerson, request, userName, userFirstName);
                        updatedPerson.setActif(willBeActive);

                        String effectiveRole = roleType != null ? roleType : profile.getRoleType();
                        profile.setRoleType(effectiveRole);
                        if (willBeActive) {
                            if (StringUtils.hasText(agencyId)) {
                                if (StringUtils.hasText(agencyScopeId)
                                        && !agencyScopeId.equals(agencyId)) {
                                    return Mono.error(new ResponseStatusException(
                                            HttpStatus.FORBIDDEN,
                                            "Unauthorized for this agency."
                                    ));
                                }
                                profile.setAgencyId(agencyId);
                            }
                        } else {
                            profile.setAgencyId(null);
                        }
                        profile.setOrganizationId(null);

                        Mono<Void> updateFlow = entityTemplate.update(updatedPerson)
                                .then(entityTemplate.update(profile))
                                .then(handleAgencyAdminDisable(isDisabling, currentAgencyId));

                        String targetAgencyId = StringUtils.hasText(agencyId) ? agencyId : currentAgencyId;
                        if (willBeActive && StringUtils.hasText(targetAgencyId)) {
                            updateFlow = updateFlow.then(setAgencyRequiresAdminAssignment(targetAgencyId, false));
                        }

                        return transactionalOperator.transactional(updateFlow)
                                .then(getAgencyAdmin(personId, organizationId));
                    });
        }));
    }

    /**
     * Deletes an agency admin user.
     *
     * @param personId person identifier
     * @param organizationId organization identifier
     * @param agencyScopeId agency identifier when scoped
     * @return completion signal
     */
    public Mono<Void> deleteAgencyAdmin(String personId, String organizationId, String agencyScopeId) {
        if (!StringUtils.hasText(organizationId)) {
            return Mono.error(new ResponseStatusException(HttpStatus.FORBIDDEN, "Missing organization scope."));
        }
        Mono<Person> personMono = personRepository.findById(personId)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Admin not found.")));

        Mono<AdminProfile> adminProfileMono = adminProfileRepository.findByPersonId(personId)
                .switchIfEmpty(Mono.error(new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Admin profile not found."
                )))
                .flatMap(profile -> {
                    if (!ROLE_AGENCY_ADMIN.equals(profile.getRoleType())) {
                        return Mono.error(new ResponseStatusException(
                                HttpStatus.CONFLICT,
                                "Admin role type mismatch."
                        ));
                    }
                    if (StringUtils.hasText(agencyScopeId)
                            && !agencyScopeId.equals(profile.getAgencyId())) {
                        return Mono.error(new ResponseStatusException(
                                HttpStatus.FORBIDDEN,
                                "Unauthorized for this agency."
                        ));
                    }
                    return ensureAgencyScope(profile, organizationId).thenReturn(profile);
                });

        Mono<Void> deleteFlow = personMono.flatMap(person -> adminProfileMono.flatMap(profile -> {
            String currentAgencyId = profile.getAgencyId();
            person.setActif(false);
            profile.setAgencyId(null);
            profile.setOrganizationId(null);

            Mono<Void> updateFlow = entityTemplate.update(person)
                    .then(entityTemplate.update(profile))
                    .then(handleAgencyAdminDisable(true, currentAgencyId));

            return transactionalOperator.transactional(updateFlow);
        }));

        return deleteFlow;
    }

    /**
     * Finds an admin by phone number.
     *
     * @param phone phone number
     * @return admin response
     */
    public Mono<AdminUserResponse> lookupAdminByPhone(String phone) {
        String normalized = normalizePhone(phone);
        if (!StringUtils.hasText(normalized)) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Phone is required."));
        }

        String sql = "SELECT p.id AS person_id, p.user_name, p.user_first_name, p.mail, "
                + "p.telegram_chat_id, p.country, p.phone, p.actif, "
                + "a.role_type, a.organization_id, a.agency_id "
                + "FROM person p "
                + "LEFT JOIN admin_profile a ON a.person_id = p.id "
                + "WHERE p.phone IS NOT NULL";

        DatabaseClient client = entityTemplate.getDatabaseClient();
        return client.sql(sql)
                .map(this::mapLookupRow)
                .all()
                .collectList()
                .flatMap(list -> {
                    AdminUserResponse exact = null;
                    AdminUserResponse partial = null;
                    for (AdminUserResponse candidate : list) {
                        String candidatePhone = normalizePhone(candidate.getPhone());
                        if (!StringUtils.hasText(candidatePhone)) {
                            continue;
                        }
                        if (candidatePhone.equals(normalized)) {
                            exact = candidate;
                            break;
                        }
                        if (partial == null && candidatePhone.startsWith(normalized)) {
                            partial = candidate;
                        }
                    }
                    AdminUserResponse selected = exact != null ? exact : partial;
                    if (selected == null) {
                        return Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Admin not found."));
                    }
                    AdminProfileResponse profile = selected.getAdminProfile();
                    if (profile != null && ROLE_SUPERADMIN.equals(profile.getRoleType())) {
                        return Mono.error(new ResponseStatusException(
                                HttpStatus.FORBIDDEN,
                                "ERP admins cannot be assigned."
                        ));
                    }
                    return Mono.just(selected);
                });
    }

    private Mono<Person> validateUsernameChange(Person person, String username) {
        if (!StringUtils.hasText(username) || username.equals(person.getUserName())) {
            return Mono.just(person);
        }
        return personRepository.findByUserName(username)
                .filter(existing -> !existing.getId().equals(person.getId()))
                .flatMap(existing -> Mono.<Person>error(new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Username already exists."
                )))
                .switchIfEmpty(Mono.just(person));
    }

    private Mono<String> validateOrganizationChange(String organizationId) {
        if (!StringUtils.hasText(organizationId)) {
            return Mono.empty();
        }
        return organizationRepository.findById(organizationId)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Organization not found.")))
                .map(Organization::getId);
    }

    private Mono<Person> resolvePerson(String personId, String phone) {
        if (StringUtils.hasText(personId)) {
            return personRepository.findById(personId)
                    .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Admin not found.")));
        }
        return findPersonByPhone(phone)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Admin not found.")));
    }

    private Mono<Person> ensureNotSuperAdmin(Person person) {
        return adminProfileRepository.findByPersonId(person.getId())
                .flatMap(profile -> {
                    if (ROLE_SUPERADMIN.equals(profile.getRoleType())) {
                        return Mono.error(new ResponseStatusException(
                                HttpStatus.FORBIDDEN,
                                "ERP admins cannot be assigned."
                        ));
                    }
                    return Mono.just(person);
                })
                .defaultIfEmpty(person);
    }

    private Mono<String> assignAdminProfile(
            Person person,
            String roleType,
            String organizationId,
            String agencyId
    ) {
        person.setActif(true);
        Mono<String> saveFlow = adminProfileRepository.findByPersonId(person.getId())
                .flatMap(profile -> saveAdminProfile(person, profile, roleType, organizationId, agencyId, false))
                .switchIfEmpty(Mono.defer(() -> {
                    AdminProfile profile = new AdminProfile();
                    profile.setId(UUID.randomUUID().toString());
                    profile.setPersonId(person.getId());
                    return saveAdminProfile(person, profile, roleType, organizationId, agencyId, true);
                }));

        return transactionalOperator.transactional(saveFlow);
    }

    private Mono<String> saveAdminProfile(
            Person person,
            AdminProfile profile,
            String roleType,
            String organizationId,
            String agencyId,
            boolean isNew
    ) {
        profile.setRoleType(AdminRoleResolver.normalizeRoleType(roleType, agencyId));
        profile.setOrganizationId(organizationId);
        profile.setAgencyId(agencyId);

        Mono<Person> personWrite = entityTemplate.update(person);
        Mono<AdminProfile> profileWrite = isNew
                ? entityTemplate.insert(AdminProfile.class).using(profile)
                : entityTemplate.update(profile);
        return personWrite.then(profileWrite).thenReturn(person.getId());
    }

    private Person applyPersonUpdates(
            Person person,
            UpdateOrganizationAdminRequest request,
            String userName,
            String userFirstName
    ) {
        if (StringUtils.hasText(userName)) {
            person.setUserName(userName);
        }
        if (StringUtils.hasText(userFirstName)) {
            person.setUserFirstName(userFirstName);
        }
        if (request.getMail() != null) {
            person.setMail(trimToNull(request.getMail()));
        }
        if (request.getTelegramChatId() != null) {
            person.setTelegramChatId(trimToNull(request.getTelegramChatId()));
        }
        if (request.getCountry() != null) {
            person.setCountry(trimToNull(request.getCountry()));
        }
        if (request.getPhone() != null) {
            person.setPhone(trimToNull(request.getPhone()));
        }
        return person;
    }

    private Mono<Void> updateOrganizationBotToken(String organizationId, String token) {
        if (!StringUtils.hasText(organizationId) || token == null) {
            return Mono.empty();
        }
        String sql = "UPDATE organization SET telegram_bot_token = $1 WHERE id = $2";
        return entityTemplate.getDatabaseClient()
                .sql(sql)
                .bind(0, trimToNull(token))
                .bind(1, organizationId)
                .fetch()
                .rowsUpdated()
                .then();
    }

    private Mono<Void> handleOrganizationAdminDisable(boolean isDisabling, String organizationId) {
        if (!isDisabling || !StringUtils.hasText(organizationId)) {
            return Mono.empty();
        }
        DatabaseClient client = entityTemplate.getDatabaseClient();
        Mono<Void> disableOrganization = client.sql("UPDATE organization SET is_active = false WHERE id = $1")
                .bind(0, organizationId)
                .fetch()
                .rowsUpdated()
                .then();
        Mono<Void> disableAgencies = client.sql(
                        "UPDATE agency SET is_active = false, requires_admin_assignment = true "
                                + "WHERE organization_id = $1")
                .bind(0, organizationId)
                .fetch()
                .rowsUpdated()
                .then();
        Mono<Void> lockSessions = client.sql(
                        "UPDATE cash_register_session SET is_locked = true "
                                + "WHERE state = $1 AND cash_register_id IN ("
                                + "SELECT id FROM cash_register WHERE agency_id IN ("
                                + "SELECT id FROM agency WHERE organization_id = $2))")
                .bind(0, "ouverte")
                .bind(1, organizationId)
                .fetch()
                .rowsUpdated()
                .then();
        Mono<Void> disableAgencyAdmins = client.sql(
                        "UPDATE person SET actif = false "
                                + "WHERE id IN ("
                                + "SELECT ap.person_id FROM admin_profile ap "
                                + "JOIN agency ag ON ap.agency_id = ag.id "
                                + "WHERE ap.role_type = $1 AND ag.organization_id = $2)")
                .bind(0, ROLE_AGENCY_ADMIN)
                .bind(1, organizationId)
                .fetch()
                .rowsUpdated()
                .then();

        return disableOrganization.then(disableAgencies).then(lockSessions).then(disableAgencyAdmins);
    }

    private Mono<Void> handleAgencyAdminDisable(boolean isDisabling, String agencyId) {
        if (!isDisabling || !StringUtils.hasText(agencyId)) {
            return Mono.empty();
        }
        String sql = "SELECT COUNT(1) AS admin_count "
                + "FROM admin_profile ap "
                + "JOIN person p ON p.id = ap.person_id "
                + "WHERE ap.role_type = $1 AND ap.agency_id = $2 AND p.actif = true";
        DatabaseClient client = entityTemplate.getDatabaseClient();
        return client.sql(sql)
                .bind(0, ROLE_AGENCY_ADMIN)
                .bind(1, agencyId)
                .map((row, meta) -> row.get("admin_count", Long.class))
                .one()
                .defaultIfEmpty(0L)
                .flatMap(count -> {
                    if (count != null && count > 0) {
                        return Mono.empty();
                    }
                    return client.sql(
                                    "UPDATE agency SET is_active = false, requires_admin_assignment = true "
                                            + "WHERE id = $1")
                            .bind(0, agencyId)
                            .fetch()
                            .rowsUpdated()
                            .then();
                });
    }

    private Mono<Void> setAgencyRequiresAdminAssignment(String agencyId, boolean requiresAdminAssignment) {
        if (!StringUtils.hasText(agencyId)) {
            return Mono.empty();
        }
        String sql = "UPDATE agency SET requires_admin_assignment = $1 WHERE id = $2";
        return entityTemplate.getDatabaseClient()
                .sql(sql)
                .bind(0, requiresAdminAssignment)
                .bind(1, agencyId)
                .fetch()
                .rowsUpdated()
                .then();
    }

    private Mono<Agency> validateAgencyAssignment(String agencyId, String organizationId) {
        return agencyRepository.findById(agencyId)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Agency not found.")))
                .flatMap(agency -> {
                    if (!StringUtils.hasText(agency.getOrganizationId())
                            || !agency.getOrganizationId().equals(organizationId)) {
                        return Mono.error(new ResponseStatusException(
                                HttpStatus.FORBIDDEN,
                                "Agency does not belong to your organization."
                        ));
                    }
                    if (!Boolean.TRUE.equals(agency.getIsActive())) {
                        return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Agency is inactive."));
                    }
                    return Mono.just(agency);
                });
    }

    private Mono<Void> ensureAgencyScope(AdminProfile profile, String organizationId) {
        if (!StringUtils.hasText(profile.getAgencyId())) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Admin agency is missing."));
        }
        return agencyRepository.findById(profile.getAgencyId())
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Agency not found.")))
                .flatMap(agency -> {
                    if (!organizationId.equals(agency.getOrganizationId())) {
                        return Mono.error(new ResponseStatusException(
                                HttpStatus.FORBIDDEN,
                                "Agency does not belong to your organization."
                        ));
                    }
                    return Mono.empty();
                });
    }

    private Mono<Person> findPersonByPhone(String phone) {
        String normalized = normalizePhone(phone);
        if (!StringUtils.hasText(normalized)) {
            return Mono.empty();
        }
        String sql = "SELECT id, phone FROM person WHERE phone IS NOT NULL";
        return entityTemplate.getDatabaseClient()
                .sql(sql)
                .map((row, metadata) -> {
                    String id = row.get("id", String.class);
                    String phoneValue = row.get("phone", String.class);
                    return new PhoneCandidate(id, phoneValue);
                })
                .all()
                .collectList()
                .flatMap(list -> {
                    PhoneCandidate exact = null;
                    PhoneCandidate partial = null;
                    for (PhoneCandidate candidate : list) {
                        String candidatePhone = normalizePhone(candidate.phone);
                        if (!StringUtils.hasText(candidatePhone)) {
                            continue;
                        }
                        if (candidatePhone.equals(normalized)) {
                            exact = candidate;
                            break;
                        }
                        if (partial == null && candidatePhone.startsWith(normalized)) {
                            partial = candidate;
                        }
                    }
                    PhoneCandidate selected = exact != null ? exact : partial;
                    if (selected == null || !StringUtils.hasText(selected.id)) {
                        return Mono.empty();
                    }
                    return personRepository.findById(selected.id);
                });
    }

    private String normalizePhone(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.replaceAll("\\D+", "");
    }

    private AdminUserResponse mapOrganizationAdminRow(Row row, RowMetadata metadata) {
        AdminProfileResponse profile = new AdminProfileResponse();
        profile.setRoleType(row.get("role_type", String.class));
        profile.setOrganizationId(row.get("organization_id", String.class));
        profile.setAgencyId(row.get("agency_id", String.class));

        String orgId = row.get("org_id", String.class);
        if (StringUtils.hasText(orgId)) {
            profile.setOrganization(new OrganizationSummaryResponse(
                    orgId,
                    row.get("org_name", String.class),
                    row.get("org_country", String.class),
                    row.get("org_telegram_bot_token", String.class)
            ));
        }

        return buildAdminResponse(row, profile);
    }

    private AdminUserResponse mapAgencyAdminRow(Row row, RowMetadata metadata) {
        AdminProfileResponse profile = new AdminProfileResponse();
        profile.setRoleType(row.get("role_type", String.class));
        profile.setOrganizationId(resolveOrganizationId(row));
        profile.setAgencyId(row.get("admin_agency_id", String.class));

        String agencyId = row.get("agency_id", String.class);
        if (StringUtils.hasText(agencyId)) {
            profile.setAgency(new AgencySummaryResponse(
                    agencyId,
                    row.get("agency_name", String.class),
                    row.get("agency_country", String.class),
                    row.get("agency_town", String.class),
                    row.get("agency_neighborhood", String.class),
                    row.get("agency_organization_id", String.class)
            ));
        }

        String orgId = row.get("org_id", String.class);
        if (StringUtils.hasText(orgId)) {
            profile.setOrganization(new OrganizationSummaryResponse(
                    orgId,
                    row.get("org_name", String.class),
                    row.get("org_country", String.class),
                    row.get("org_telegram_bot_token", String.class)
            ));
        }

        return buildAdminResponse(row, profile);
    }

    private AdminUserResponse mapLookupRow(Row row, RowMetadata metadata) {
        String roleType = row.get("role_type", String.class);
        String organizationId = row.get("organization_id", String.class);
        String agencyId = row.get("agency_id", String.class);
        AdminProfileResponse profile = null;
        if (roleType != null || organizationId != null || agencyId != null) {
            profile = new AdminProfileResponse(roleType, organizationId, agencyId, null, null);
        }
        return buildAdminResponse(row, profile);
    }

    private AdminUserResponse buildAdminResponse(Row row, AdminProfileResponse profile) {
        return new AdminUserResponse(
                row.get("person_id", String.class),
                row.get("user_name", String.class),
                row.get("user_first_name", String.class),
                row.get("mail", String.class),
                null,
                row.get("telegram_chat_id", String.class),
                row.get("country", String.class),
                row.get("phone", String.class),
                row.get("actif", Boolean.class),
                profile
        );
    }

    private String resolveOrganizationId(Row row) {
        String orgId = row.get("admin_organization_id", String.class);
        if (StringUtils.hasText(orgId)) {
            return orgId;
        }
        return row.get("agency_organization_id", String.class);
    }

    private OrganizationResponse mapOrganizationRow(Row row, RowMetadata metadata) {
        OrganizationCreatorResponse creator = null;
        String creatorId = row.get("creator_id", String.class);
        if (StringUtils.hasText(creatorId)) {
            creator = new OrganizationCreatorResponse(
                    creatorId,
                    row.get("creator_user_name", String.class),
                    row.get("creator_user_first_name", String.class)
            );
        }
        return new OrganizationResponse(
                row.get("organization_id", String.class),
                row.get("name", String.class),
                row.get("country", String.class),
                row.get("description", String.class),
                row.get("is_active", Boolean.class),
                row.get("create_on", LocalDateTime.class),
                row.get("create_by", String.class),
                row.get("telegram_bot_token", String.class),
                creator
        );
    }

    private OrganizationResponse toResponse(Organization organization) {
        return new OrganizationResponse(
                organization.getId(),
                organization.getName(),
                organization.getCountry(),
                organization.getDescription(),
                organization.getIsActive(),
                organization.getCreateOn(),
                organization.getCreateBy(),
                organization.getTelegramBotToken(),
                null
        );
    }

    private String trimToNull(String value) {
        String trimmed = StringUtils.trimWhitespace(value);
        return StringUtils.hasText(trimmed) ? trimmed : null;
    }

    private static final class PhoneCandidate {
        private final String id;
        private final String phone;

        private PhoneCandidate(String id, String phone) {
            this.id = id;
            this.phone = phone;
        }
    }
}
