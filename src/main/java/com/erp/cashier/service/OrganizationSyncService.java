package com.erp.cashier.service;

import com.erp.cashier.dto.OrganizationMembershipResponse;
import com.erp.cashier.dto.external.RtOrganizationMember;
import com.erp.cashier.model.AdminProfile;
import com.erp.cashier.model.Agency;
import com.erp.cashier.model.CashierProfile;
import com.erp.cashier.model.Organization;
import com.erp.cashier.model.Person;
import com.erp.cashier.repository.AdminProfileRepository;
import com.erp.cashier.repository.AgencyRepository;
import com.erp.cashier.repository.CashierProfileRepository;
import com.erp.cashier.repository.OrganizationRepository;
import com.erp.cashier.repository.PersonRepository;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

/**
 * Synchronizes organization data from RT_ComOps into the local database.
 *
 * @author ERP Cashier Team
 * @since 2026-01-31
 */
@Service
public class OrganizationSyncService {
    private static final String ROLE_ORGANIZATION_ADMIN = "organization_admin";
    private static final String ROLE_AGENCY_ADMIN = "agency_admin";

    private final RtComOpsClient rtComOpsClient;
    private final OrganizationRepository organizationRepository;
    private final AgencyRepository agencyRepository;
    private final PersonRepository personRepository;
    private final CashierProfileRepository cashierProfileRepository;
    private final AdminProfileRepository adminProfileRepository;
    private final PasswordService passwordService;
    private final R2dbcEntityTemplate entityTemplate;
    private final ObjectMapper objectMapper;

    /**
     * Creates the sync service.
     *
     * @param rtComOpsClient RT_ComOps client
     * @param organizationRepository organization repository
     * @param agencyRepository agency repository
     * @param personRepository person repository
     * @param cashierProfileRepository cashier profile repository
     * @param adminProfileRepository admin profile repository
     * @param passwordService password service
     * @param entityTemplate entity template
     * @param objectMapper object mapper
     */
    public OrganizationSyncService(
            RtComOpsClient rtComOpsClient,
            OrganizationRepository organizationRepository,
            AgencyRepository agencyRepository,
            PersonRepository personRepository,
            CashierProfileRepository cashierProfileRepository,
            AdminProfileRepository adminProfileRepository,
            PasswordService passwordService,
            R2dbcEntityTemplate entityTemplate,
            ObjectMapper objectMapper
    ) {
        this.rtComOpsClient = rtComOpsClient;
        this.organizationRepository = organizationRepository;
        this.agencyRepository = agencyRepository;
        this.personRepository = personRepository;
        this.cashierProfileRepository = cashierProfileRepository;
        this.adminProfileRepository = adminProfileRepository;
        this.passwordService = passwordService;
        this.entityTemplate = entityTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * Synchronizes organization data for the given organization.
     *
     * @param organizationId organization identifier
     * @param token bearer token
     * @param actorId authenticated user identifier
     * @return completion signal
     */
    public Mono<Void> syncOrganization(String organizationId, String token, String actorId) {
        String orgId = trimToNull(organizationId);
        if (!StringUtils.hasText(orgId)) {
            return Mono.error(new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Organization identifier is required."
            ));
        }
        if (!StringUtils.hasText(token)) {
            return Mono.error(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Access token is required."));
        }

        Mono<OrganizationMembershipResponse> membership = StringUtils.hasText(actorId)
                ? rtComOpsClient.listUserOrganizations(actorId, token)
                        .filter(item -> orgId.equals(item.getOrganizationId()))
                        .next()
                : Mono.empty();

        Mono<java.util.List<RtOrganizationMember>> membersMono = rtComOpsClient.listEmployees(token, orgId)
                .collectList();

        return ensureActorPerson(actorId)
                .then(membership.defaultIfEmpty(new OrganizationMembershipResponse()))
                .flatMap(found -> upsertOrganization(
                        orgId,
                        trimToNull(found.getOrganizationName()),
                        found.getIsActive(),
                        actorId
                ))
                .then(membersMono)
                .flatMap(members -> {
                    Map<String, RtOrganizationMember> membersById = buildMemberMap(members);
                    return rtComOpsClient.listWarehouses(token, orgId)
                            .filter(map -> isWarehouse(map) && belongsToOrganization(map, orgId))
                            .concatMap(map -> upsertAgency(map, orgId)
                                    .then(syncAgencyManager(map, orgId, membersById)))
                            .thenMany(reactor.core.publisher.Flux.fromIterable(members)
                                    .concatMap(member -> syncMember(member, orgId)))
                            .then();
                });
    }

    /**
     * Synchronizes organization data on login by checking local existence first.
     *
     * @param membership organization membership
     * @param token bearer token
     * @param actorId authenticated user identifier
     * @return completion signal
     */
    public Mono<Void> syncOrganizationOnLogin(
            OrganizationMembershipResponse membership,
            String token,
            String actorId
    ) {
        if (membership == null) {
            return Mono.empty();
        }
        String orgId = trimToNull(membership.getOrganizationId());
        if (!StringUtils.hasText(orgId)) {
            return Mono.error(new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Organization identifier is required."
            ));
        }
        if (!StringUtils.hasText(token)) {
            return Mono.error(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Access token is required."));
        }
        return organizationRepository.existsById(orgId)
                .flatMap(exists -> exists
                        ? syncAgenciesAndMembers(orgId, token, membership, actorId)
                        : syncOrganizationFromMembership(orgId, token, membership, actorId));
    }

    /**
     * Synchronizes organization data incrementally using the last sync timestamp.
     *
     * @param organizationId organization identifier
     * @param token bearer token
     * @param actorId authenticated user identifier
     * @param membership organization membership
     * @param lastSyncedAt last sync timestamp (nullable)
     * @return completion signal
     */
    public Mono<Void> syncOrganizationIncremental(
            String organizationId,
            String token,
            String actorId,
            OrganizationMembershipResponse membership,
            LocalDateTime lastSyncedAt
    ) {
        String orgId = trimToNull(organizationId);
        if (!StringUtils.hasText(orgId)) {
            return Mono.error(new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Organization identifier is required."
            ));
        }
        if (!StringUtils.hasText(token)) {
            return Mono.error(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Access token is required."));
        }
        Mono<java.util.List<RtOrganizationMember>> membersMono = rtComOpsClient.listEmployees(token, orgId)
                .collectList();
        OrganizationMembershipResponse resolvedMembership = membership != null
                ? membership
                : new OrganizationMembershipResponse();
        return ensureActorPerson(actorId)
                .then(upsertOrganization(
                        orgId,
                        trimToNull(resolvedMembership.getOrganizationName()),
                        resolvedMembership.getIsActive(),
                        actorId
                ))
                .then(membersMono)
                .flatMap(members -> {
                    Map<String, RtOrganizationMember> membersById = buildMemberMap(members);
                    return rtComOpsClient.listWarehouses(token, orgId)
                            .filter(map -> isWarehouse(map) && belongsToOrganization(map, orgId))
                            .concatMap(map -> upsertAgencyIncremental(map, orgId, lastSyncedAt)
                                    .then(syncAgencyManager(map, orgId, membersById)))
                            .thenMany(reactor.core.publisher.Flux.fromIterable(members)
                                    .concatMap(member -> syncMember(member, orgId)))
                            .then();
                });
    }

    /**
     * Synchronizes organization data from overview payload.
     *
     * @param overview overview payload
     * @param lastSyncedAt last sync timestamp (nullable)
     * @param actorId authenticated user identifier
     * @return completion signal
     */
    public Mono<Void> syncOrganizationOverviewIncremental(
            Map<String, Object> overview,
            LocalDateTime lastSyncedAt,
            String actorId
    ) {
        if (overview == null || overview.isEmpty()) {
            return Mono.empty();
        }
        Map<String, Object> organizationPayload = readMap(overview, "organization");
        String orgId = trimToNull(readString(organizationPayload, "id"));
        if (!StringUtils.hasText(orgId)) {
            return Mono.empty();
        }
        String name = trimToNull(readString(organizationPayload, "name"));
        Boolean isActive = readBoolean(organizationPayload, "isActive");

        java.util.List<Map<String, Object>> warehouses = readMapList(overview.get("warehouses"));
        java.util.List<RtOrganizationMember> members = new ArrayList<>();
        members.addAll(mapMembers(overview.get("orgAdmins"), "ROLE_ORG_ADMIN", orgId));
        members.addAll(mapMembers(overview.get("cashiers"), "ROLE_SALESPERSON", orgId));
        members.addAll(mapMembers(overview.get("agencyAdmins"), "ROLE_MANAGER", orgId));
        members = distinctMembers(members);
        Map<String, RtOrganizationMember> membersById = buildMemberMap(members);

        return ensureActorPerson(actorId)
                .then(upsertOrganization(orgId, name, isActive, actorId))
                .thenMany(reactor.core.publisher.Flux.fromIterable(warehouses)
                        .filter(map -> isWarehouse(map) && belongsToOrganization(map, orgId))
                        .concatMap(map -> upsertAgencyIncremental(map, orgId, lastSyncedAt)
                                .then(syncAgencyManager(map, orgId, membersById))))
                .thenMany(reactor.core.publisher.Flux.fromIterable(members)
                        .concatMap(member -> syncMember(member, orgId)))
                .then();
    }

    private Mono<Void> syncMember(RtOrganizationMember member, String organizationId) {
        if (member == null || !StringUtils.hasText(member.getUserId())) {
            return Mono.empty();
        }
        if (isCashier(member)) {
            String agencyId = trimToNull(member.getAgencyId());
            if (!StringUtils.hasText(agencyId)) {
                return Mono.empty();
            }
            return agencyRepository.existsById(agencyId)
                    .flatMap(exists -> exists ? upsertCashier(member, organizationId) : Mono.empty());
        }
        if (isAgencyAdmin(member)) {
            String agencyId = trimToNull(member.getAgencyId());
            if (!StringUtils.hasText(agencyId)) {
                return Mono.empty();
            }
            return agencyRepository.existsById(agencyId)
                    .flatMap(exists -> exists ? upsertAdmin(member, organizationId, ROLE_AGENCY_ADMIN) : Mono.empty());
        }
        if (isOrganizationAdmin(member)) {
            return upsertAdmin(member, organizationId, ROLE_ORGANIZATION_ADMIN);
        }
        return Mono.empty();
    }

    private Mono<Void> syncAgenciesAndMembers(
            String organizationId,
            String token,
            OrganizationMembershipResponse membership,
            String actorId
    ) {
        Mono<java.util.List<RtOrganizationMember>> membersMono = rtComOpsClient.listEmployees(token, organizationId)
                .collectList();
        return ensureActorPerson(actorId)
                .then(upsertOrganization(
                        organizationId,
                        trimToNull(membership.getOrganizationName()),
                        membership.getIsActive(),
                        actorId
                ))
                .then(membersMono)
                .flatMap(members -> {
                    Map<String, RtOrganizationMember> membersById = buildMemberMap(members);
                    return rtComOpsClient.listWarehouses(token, organizationId)
                            .filter(map -> isWarehouse(map) && belongsToOrganization(map, organizationId))
                            .concatMap(map -> upsertAgency(map, organizationId)
                                    .then(syncAgencyManager(map, organizationId, membersById)))
                            .thenMany(reactor.core.publisher.Flux.fromIterable(members)
                                    .concatMap(member -> syncMember(member, organizationId)))
                            .then();
                });
    }

    private Mono<Void> syncOrganizationFromMembership(
            String organizationId,
            String token,
            OrganizationMembershipResponse membership,
            String actorId
    ) {
        Mono<java.util.List<RtOrganizationMember>> membersMono = rtComOpsClient.listEmployees(token, organizationId)
                .collectList();
        return ensureActorPerson(actorId)
                .then(upsertOrganization(
                        organizationId,
                        trimToNull(membership.getOrganizationName()),
                        membership.getIsActive(),
                        actorId
                ))
                .then(membersMono)
                .flatMap(members -> {
                    Map<String, RtOrganizationMember> membersById = buildMemberMap(members);
                    return rtComOpsClient.listWarehouses(token, organizationId)
                            .filter(map -> isWarehouse(map) && belongsToOrganization(map, organizationId))
                            .concatMap(map -> upsertAgency(map, organizationId)
                                    .then(syncAgencyManager(map, organizationId, membersById)))
                            .thenMany(reactor.core.publisher.Flux.fromIterable(members)
                                    .concatMap(member -> syncMember(member, organizationId)))
                            .then();
                });
    }

    private Mono<Void> upsertCashier(RtOrganizationMember member, String organizationId) {
        return ensurePersonExists(member)
                .flatMap(person -> ensureCashierProfile(person.getId())
                        .then(updateCashierProfileFromAgency(person.getId(), member.getAgencyId()))
                )
                .then();
    }

    private Mono<Void> upsertAdmin(RtOrganizationMember member, String organizationId, String roleType) {
        return ensurePersonExists(member)
                .flatMap(person -> ensureAdminProfile(
                        person.getId(),
                        organizationId,
                        trimToNull(member.getAgencyId()),
                        roleType
                ))
                .then();
    }

    private Mono<Person> ensurePersonExists(RtOrganizationMember member) {
        String userId = trimToNull(member.getUserId());
        if (!StringUtils.hasText(userId)) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "User identifier is required."));
        }
        return personRepository.findById(userId)
                .flatMap(existing -> {
                    boolean updated = false;
                    String email = trimToNull(member.getUserEmail());
                    if (StringUtils.hasText(email) && !email.equals(existing.getUserName())) {
                        existing.setUserName(email);
                        existing.setMail(email);
                        updated = true;
                    }
                    String firstName = trimToNull(member.getUserFirstName());
                    if (StringUtils.hasText(firstName) && !firstName.equals(existing.getUserFirstName())) {
                        existing.setUserFirstName(firstName);
                        updated = true;
                    }
                    Boolean active = member.getIsActive();
                    if (active != null && !active.equals(existing.getActif())) {
                        existing.setActif(active);
                        updated = true;
                    }
                    return updated ? personRepository.save(existing) : Mono.just(existing);
                })
                .switchIfEmpty(Mono.defer(() -> entityTemplate.insert(Person.class).using(createPerson(member))));
    }

    private Mono<Person> ensurePersonExistsById(String userId) {
        String resolvedId = trimToNull(userId);
        if (!StringUtils.hasText(resolvedId)) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "User identifier is required."));
        }
        return personRepository.findById(resolvedId)
                .switchIfEmpty(Mono.defer(() -> {
                    Person person = new Person();
                    person.setId(resolvedId);
                    person.setUserName(resolvedId);
                    person.setUserFirstName(resolvedId);
                    person.setMail(null);
                    person.setActif(Boolean.TRUE);
                    person.setPassword(passwordService.hashPassword(UUID.randomUUID().toString()));
                    return entityTemplate.insert(Person.class).using(person);
                }));
    }

    private Person createPerson(RtOrganizationMember member) {
        Person person = new Person();
        String userId = trimToNull(member.getUserId());
        String email = trimToNull(member.getUserEmail());
        person.setId(userId);
        person.setUserName(StringUtils.hasText(email) ? email : userId);
        String firstName = trimToNull(member.getUserFirstName());
        person.setUserFirstName(StringUtils.hasText(firstName)
                ? firstName
                : (StringUtils.hasText(email) ? email : userId));
        person.setMail(email);
        person.setActif(member.getIsActive() != null ? member.getIsActive() : Boolean.TRUE);
        person.setPassword(passwordService.hashPassword(UUID.randomUUID().toString()));
        return person;
    }

    private Mono<Void> ensureActorPerson(String actorId) {
        String resolvedActorId = trimToNull(actorId);
        if (!StringUtils.hasText(resolvedActorId)) {
            return Mono.empty();
        }
        return personRepository.existsById(resolvedActorId)
                .flatMap(exists -> {
                    if (exists) {
                        return Mono.empty();
                    }
                    Person person = new Person();
                    person.setId(resolvedActorId);
                    person.setUserName(resolvedActorId);
                    person.setUserFirstName(resolvedActorId);
                    person.setMail(null);
                    person.setActif(Boolean.TRUE);
                    person.setPassword(passwordService.hashPassword(UUID.randomUUID().toString()));
                    return entityTemplate.insert(Person.class).using(person).then();
                });
    }

    private Mono<Void> ensureCashierProfile(String personId) {
        return cashierProfileRepository.findByPersonId(personId)
                .hasElement()
                .flatMap(exists -> {
                    if (exists) {
                        return Mono.empty();
                    }
                    CashierProfile profile = new CashierProfile();
                    profile.setId(UUID.randomUUID().toString());
                    profile.setPersonId(personId);
                    profile.setTownListChosen(null);
                    profile.setWorkTown(null);
                    profile.setHireDate(null);
                    profile.setIsActive(true);
                    return entityTemplate.insert(CashierProfile.class).using(profile).then();
                });
    }

    private Mono<Void> updateCashierProfileFromAgency(String personId, String agencyId) {
        String resolvedAgencyId = trimToNull(agencyId);
        if (!StringUtils.hasText(resolvedAgencyId)) {
            return Mono.empty();
        }
        return agencyRepository.findById(resolvedAgencyId)
                .flatMap(agency -> {
                    String town = trimToNull(agency.getTown());
                    return cashierProfileRepository.findByPersonId(personId)
                            .flatMap(profile -> {
                                boolean updated = false;
                                String currentBase = trimToNull(profile.getBaseAgencyId());
                                if (!resolvedAgencyId.equals(currentBase)) {
                                    profile.setBaseAgencyId(resolvedAgencyId);
                                    updated = true;
                                }
                                if (StringUtils.hasText(town)) {
                                    String currentTown = trimToNull(profile.getWorkTown());
                                    if (!StringUtils.hasText(currentTown) || !town.equals(currentTown)) {
                                        profile.setWorkTown(town);
                                        updated = true;
                                    }
                                }
                                return updated ? entityTemplate.update(profile).then() : Mono.empty();
                            })
                            .then();
                })
                .then();
    }

    private Mono<Void> ensureAdminProfile(
            String personId,
            String organizationId,
            String agencyId,
            String roleType
    ) {
        return adminProfileRepository.findByPersonId(personId)
                .flatMap(existing -> updateAdminProfileIfNeeded(existing, organizationId, agencyId, roleType))
                .switchIfEmpty(Mono.defer(() -> insertAdminProfile(personId, organizationId, agencyId, roleType)
                        .onErrorResume(
                                DuplicateKeyException.class,
                                ex -> adminProfileRepository.findByPersonId(personId)
                                        .flatMap(existing -> updateAdminProfileIfNeeded(
                                                existing,
                                                organizationId,
                                                agencyId,
                                                roleType
                                        ))
                                        .then()
                        )
                ));
    }

    private Mono<Void> insertAdminProfile(
            String personId,
            String organizationId,
            String agencyId,
            String roleType
    ) {
        AdminProfile profile = new AdminProfile();
        profile.setId(UUID.randomUUID().toString());
        profile.setPersonId(personId);
        profile.setRoleType(AdminRoleResolver.normalizeRoleType(roleType, agencyId));
        profile.setOrganizationId(organizationId);
        profile.setAgencyId(agencyId);
        return entityTemplate.insert(AdminProfile.class).using(profile).then();
    }

    private Mono<Void> updateAdminProfileIfNeeded(
            AdminProfile existing,
            String organizationId,
            String agencyId,
            String roleType
    ) {
        boolean updated = false;
        String resolvedAgencyId = StringUtils.hasText(agencyId) ? agencyId : existing.getAgencyId();
        String resolvedRoleType = AdminRoleResolver.normalizeRoleType(
                StringUtils.hasText(roleType) ? roleType : existing.getRoleType(),
                resolvedAgencyId
        );
        if (StringUtils.hasText(resolvedRoleType)
                && (existing.getRoleType() == null
                || !"superadmin".equalsIgnoreCase(existing.getRoleType()))
                && !resolvedRoleType.equalsIgnoreCase(existing.getRoleType())) {
            existing.setRoleType(resolvedRoleType);
            updated = true;
        }
        if (StringUtils.hasText(organizationId) && !organizationId.equals(existing.getOrganizationId())) {
            existing.setOrganizationId(organizationId);
            updated = true;
        }
        if (StringUtils.hasText(agencyId) && !agencyId.equals(existing.getAgencyId())) {
            existing.setAgencyId(agencyId);
            updated = true;
        }
        return updated ? adminProfileRepository.save(existing).then() : Mono.empty();
    }

    private Mono<Void> syncAgencyManager(
            Map<String, Object> agencyPayload,
            String organizationId,
            Map<String, RtOrganizationMember> membersById
    ) {
        String managerId = trimToNull(readString(agencyPayload, "managerId"));
        if (!StringUtils.hasText(managerId)) {
            managerId = trimToNull(readString(agencyPayload, "manager_id"));
        }
        if (!StringUtils.hasText(managerId)) {
            return Mono.empty();
        }
        String agencyId = trimToNull(readString(agencyPayload, "id"));
        if (!StringUtils.hasText(agencyId)) {
            return Mono.empty();
        }
        RtOrganizationMember member = membersById != null ? membersById.get(managerId) : null;
        Mono<Person> personMono = member != null ? ensurePersonExists(member) : ensurePersonExistsById(managerId);
        return personMono.flatMap(person -> ensureAdminProfile(
                person.getId(),
                organizationId,
                agencyId,
                ROLE_AGENCY_ADMIN
        ));
    }

    private Mono<Organization> upsertOrganization(
            String organizationId,
            String name,
            Boolean isActive,
            String actorId
    ) {
        String id = trimToNull(organizationId);
        if (!StringUtils.hasText(id)) {
            return Mono.empty();
        }
        return organizationRepository.findById(id)
                .defaultIfEmpty(new Organization())
                .map(existing -> mergeOrganization(existing, id, name, isActive, actorId))
                .flatMap(organization -> saveOrInsertOrganization(organization, id));
    }

    private Organization mergeOrganization(
            Organization organization,
            String id,
            String name,
            Boolean isActive,
            String actorId
    ) {
        organization.setId(id);
        if (StringUtils.hasText(name)) {
            organization.setName(name);
        }
        if (isActive != null) {
            organization.setIsActive(isActive);
        }
        if (StringUtils.hasText(actorId)
                && !StringUtils.hasText(organization.getCreateBy())) {
            organization.setCreateBy(actorId);
        }
        return organization;
    }

    private Mono<Agency> upsertAgency(Map<String, Object> payload, String organizationId) {
        String id = trimToNull(readString(payload, "id"));
        if (!StringUtils.hasText(id)) {
            return Mono.empty();
        }
        return agencyRepository.findById(id)
                .defaultIfEmpty(new Agency())
                .map(existing -> mergeAgency(existing, payload, organizationId))
                .flatMap(agency -> saveOrInsertAgency(agency, id));
    }

    private Mono<Void> upsertAgencyIncremental(
            Map<String, Object> payload,
            String organizationId,
            LocalDateTime lastSyncedAt
    ) {
        if (lastSyncedAt == null) {
            return upsertAgency(payload, organizationId).then();
        }
        String id = trimToNull(readString(payload, "id"));
        if (!StringUtils.hasText(id)) {
            return Mono.empty();
        }
        LocalDateTime updatedAt = readUpdatedAt(payload);
        if (updatedAt == null) {
            return upsertAgency(payload, organizationId).then();
        }
        if (updatedAt.isAfter(lastSyncedAt)) {
            return upsertAgency(payload, organizationId).then();
        }
        return agencyRepository.existsById(id)
                .flatMap(exists -> exists ? Mono.empty() : upsertAgency(payload, organizationId).then());
    }

    private Agency mergeAgency(Agency agency, Map<String, Object> payload, String organizationId) {
        agency.setId(trimToNull(readString(payload, "id")));
        String name = trimToNull(readString(payload, "name"));
        if (StringUtils.hasText(name)) {
            agency.setName(name);
        }
        String country = trimToNull(readString(payload, "country"));
        if (StringUtils.hasText(country)) {
            agency.setCountry(country);
        }
        String town = trimToNull(readString(payload, "city"));
        if (StringUtils.hasText(town)) {
            agency.setTown(town);
        }
        String neighborhood = trimToNull(readString(payload, "neighborhood"));
        if (StringUtils.hasText(neighborhood)) {
            agency.setNeighborhood(neighborhood);
        }
        String address = trimToNull(readString(payload, "address"));
        if (StringUtils.hasText(address)) {
            agency.setAddress(address);
        }
        String location = trimToNull(readString(payload, "location"));
        if (StringUtils.hasText(location)) {
            agency.setLocationHint(location);
        }
        Boolean isActive = readBoolean(payload, "isActive");
        if (isActive == null) {
            isActive = readBoolean(payload, "is_active");
        }
        if (isActive != null) {
            agency.setIsActive(isActive);
        } else if (agency.getIsActive() == null) {
            agency.setIsActive(Boolean.TRUE);
        }
        agency.setRequiresAdminAssignment(Boolean.FALSE);
        String externalOrgId = trimToNull(readString(payload, "organizationId"));
        agency.setOrganizationId(StringUtils.hasText(externalOrgId) ? externalOrgId : organizationId);
        LocalDateTime createdAt = parseInstant(payload.get("createdAt"));
        if (createdAt != null) {
            agency.setCreateOn(createdAt);
        }
        return agency;
    }


    private Mono<Organization> saveOrInsertOrganization(Organization organization, String id) {
        return organizationRepository.findById(id)
                .flatMap(existing -> organizationRepository.save(organization))
                .switchIfEmpty(entityTemplate.insert(Organization.class).using(organization));
    }

    private Mono<Agency> saveOrInsertAgency(Agency agency, String id) {
        return agencyRepository.findById(id)
                .flatMap(existing -> agencyRepository.save(agency))
                .switchIfEmpty(entityTemplate.insert(Agency.class).using(agency));
    }

    private boolean isWarehouse(Map<String, Object> payload) {
        String type = trimToNull(readString(payload, "type"));
        return !StringUtils.hasText(type) || "WAREHOUSE".equalsIgnoreCase(type);
    }

    private boolean belongsToOrganization(Map<String, Object> payload, String organizationId) {
        String orgId = trimToNull(readString(payload, "organizationId"));
        return !StringUtils.hasText(orgId) || orgId.equals(organizationId);
    }

    private boolean isCashier(RtOrganizationMember member) {
        String role = normalizeRole(member.getRoleName());
        return role.contains("SALESPERSON");
    }

    private boolean isAgencyAdmin(RtOrganizationMember member) {
        String role = normalizeRole(member.getRoleName());
        return "ROLE_MANAGER".equals(role) && StringUtils.hasText(trimToNull(member.getAgencyId()));
    }

    private boolean isOrganizationAdmin(RtOrganizationMember member) {
        String role = normalizeRole(member.getRoleName());
        return "ROLE_ORG_ADMIN".equals(role) || "ROLE_ADMIN".equals(role);
    }

    private String normalizeRole(String role) {
        if (!StringUtils.hasText(role)) {
            return "";
        }
        return role.trim().toUpperCase(Locale.ROOT);
    }

    private Map<String, RtOrganizationMember> buildMemberMap(java.util.List<RtOrganizationMember> members) {
        Map<String, RtOrganizationMember> map = new java.util.HashMap<>();
        if (members == null) {
            return map;
        }
        for (RtOrganizationMember member : members) {
            if (member == null) {
                continue;
            }
            String userId = trimToNull(member.getUserId());
            if (StringUtils.hasText(userId)) {
                map.put(userId, member);
            }
        }
        return map;
    }

    private java.util.List<RtOrganizationMember> mapMembers(
            Object value,
            String defaultRole,
            String organizationId
    ) {
        java.util.List<RtOrganizationMember> members = new ArrayList<>();
        if (!(value instanceof Iterable<?> iterable)) {
            return members;
        }
        for (Object item : iterable) {
            if (item == null) {
                continue;
            }
            RtOrganizationMember member = objectMapper.convertValue(item, RtOrganizationMember.class);
            if (member == null) {
                continue;
            }
            if (!StringUtils.hasText(trimToNull(member.getRoleName())) && StringUtils.hasText(defaultRole)) {
                member.setRoleName(defaultRole);
            }
            if (!StringUtils.hasText(trimToNull(member.getOrganizationId()))
                    && StringUtils.hasText(organizationId)) {
                member.setOrganizationId(organizationId);
            }
            members.add(member);
        }
        return members;
    }

    private java.util.List<RtOrganizationMember> distinctMembers(java.util.List<RtOrganizationMember> members) {
        if (members == null || members.isEmpty()) {
            return java.util.List.of();
        }
        java.util.Map<String, RtOrganizationMember> map = new java.util.LinkedHashMap<>();
        for (RtOrganizationMember member : members) {
            if (member == null) {
                continue;
            }
            String userId = trimToNull(member.getUserId());
            String role = normalizeRole(member.getRoleName());
            if (!StringUtils.hasText(userId)) {
                continue;
            }
            String key = userId + ":" + role;
            map.putIfAbsent(key, member);
        }
        return new ArrayList<>(map.values());
    }

    private Map<String, Object> readMap(Map<String, Object> payload, String key) {
        if (payload == null || key == null) {
            return null;
        }
        Object value = payload.get(key);
        if (value instanceof Map<?, ?> map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> casted = (Map<String, Object>) map;
            return casted;
        }
        return null;
    }

    private java.util.List<Map<String, Object>> readMapList(Object value) {
        java.util.List<Map<String, Object>> result = new ArrayList<>();
        if (!(value instanceof Iterable<?> iterable)) {
            return result;
        }
        for (Object item : iterable) {
            if (item instanceof Map<?, ?> map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> casted = (Map<String, Object>) map;
                result.add(casted);
            }
        }
        return result;
    }

    private String readString(Map<String, Object> payload, String key) {
        if (payload == null || key == null) {
            return null;
        }
        Object value = payload.get(key);
        return value != null ? String.valueOf(value) : null;
    }

    private LocalDateTime readUpdatedAt(Map<String, Object> payload) {
        if (payload == null) {
            return null;
        }
        LocalDateTime updatedAt = parseInstant(payload.get("updatedAt"));
        if (updatedAt != null) {
            return updatedAt;
        }
        return parseInstant(payload.get("updated_at"));
    }

    private Boolean readBoolean(Map<String, Object> payload, String key) {
        if (payload == null || key == null) {
            return null;
        }
        Object value = payload.get(key);
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value != null) {
            return Boolean.parseBoolean(String.valueOf(value));
        }
        return null;
    }

    private LocalDateTime parseInstant(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof LocalDateTime dateTime) {
            return dateTime;
        }
        try {
            Instant instant = Instant.parse(String.valueOf(value));
            return LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }
}
