package com.erp.cashier.controller;

import com.erp.cashier.model.AdminProfile;
import com.erp.cashier.model.CashierAgencyAssignment;
import com.erp.cashier.model.CashierProfile;
import com.erp.cashier.model.Person;
import com.erp.cashier.repository.AdminProfileRepository;
import com.erp.cashier.repository.AgencyRepository;
import com.erp.cashier.repository.CashierAgencyAssignmentRepository;
import com.erp.cashier.repository.CashierProfileRepository;
import com.erp.cashier.repository.PersonRepository;
import com.erp.cashier.security.JwtPayload;
import com.erp.cashier.service.PasswordService;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Employee endpoints backed by the local database.
 *
 * @author ERP Cashier Team
 * @since 2026-01-30
 */
@RestController
@RequestMapping("/api/employees")
public class EmployeeController {
    private static final String ROLE_ORG_ADMIN = "ROLE_ORG_ADMIN";
    private static final String ROLE_AGENCY_ADMIN = "ROLE_MANAGER";
    private static final String ROLE_CASHIER = "ROLE_SALESPERSON";
    private static final String ROLE_SUPERADMIN = "ROLE_SUPERADMIN";

    private final PersonRepository personRepository;
    private final AdminProfileRepository adminProfileRepository;
    private final CashierProfileRepository cashierProfileRepository;
    private final CashierAgencyAssignmentRepository assignmentRepository;
    private final AgencyRepository agencyRepository;
    private final PasswordService passwordService;
    private final R2dbcEntityTemplate entityTemplate;

    /**
     * Creates the employee controller.
     *
     * @param personRepository person repository
     * @param adminProfileRepository admin profile repository
     * @param cashierProfileRepository cashier profile repository
     * @param assignmentRepository assignment repository
     * @param agencyRepository agency repository
     * @param passwordService password service
     * @param entityTemplate entity template
     */
    public EmployeeController(
            PersonRepository personRepository,
            AdminProfileRepository adminProfileRepository,
            CashierProfileRepository cashierProfileRepository,
            CashierAgencyAssignmentRepository assignmentRepository,
            AgencyRepository agencyRepository,
            PasswordService passwordService,
            R2dbcEntityTemplate entityTemplate
    ) {
        this.personRepository = personRepository;
        this.adminProfileRepository = adminProfileRepository;
        this.cashierProfileRepository = cashierProfileRepository;
        this.assignmentRepository = assignmentRepository;
        this.agencyRepository = agencyRepository;
        this.passwordService = passwordService;
        this.entityTemplate = entityTemplate;
    }

    /**
     * Finds an employee by email.
     *
     * @param email employee email
     * @param tenantId optional organization identifier
     * @param authentication authentication payload
     * @return employee info
     */
    @GetMapping("/by-email")
    @PreAuthorize("hasAuthority('ROLE_SUPERADMIN') or hasAuthority('ROLE_ORGANIZATION_ADMIN')")
    public Mono<Map<String, Object>> findByEmail(
            @RequestParam("email") String email,
            @RequestHeader(name = "X-Tenant-ID", required = false) String tenantId,
            Authentication authentication
    ) {
        String resolvedEmail = trimToNull(email);
        if (!StringUtils.hasText(resolvedEmail)) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email is required."));
        }
        String organizationId = resolveOrganizationId(authentication, tenantId);
        return resolvePersonByLogin(resolvedEmail)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Employee not found.")))
                .flatMap(person -> buildMemberResponse(person, organizationId));
    }

    /**
     * Lists available roles.
     *
     * @return roles
     */
    @GetMapping("/roles")
    @PreAuthorize("hasAuthority('ROLE_SUPERADMIN') or hasAuthority('ROLE_ORGANIZATION_ADMIN')")
    public Flux<Map<String, Object>> listRoles() {
        return Flux.fromIterable(List.of(
                role("ROLE_ORG_ADMIN", "Organization admin"),
                role("ROLE_MANAGER", "Agency admin"),
                role("ROLE_SALESPERSON", "Cashier")
        ));
    }

    /**
     * Invites an employee (local database).
     *
     * @param request invite request
     * @param tenantId optional organization identifier
     * @param authentication authentication payload
     * @return invited employee
     */
    @PostMapping("/invite")
    @PreAuthorize("hasAuthority('ROLE_SUPERADMIN') or hasAuthority('ROLE_ORGANIZATION_ADMIN')")
    public Mono<Map<String, Object>> inviteEmployee(
            @RequestBody Map<String, Object> request,
            @RequestHeader(name = "X-Tenant-ID", required = false) String tenantId,
            Authentication authentication
    ) {
        if (request == null || request.isEmpty()) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invite payload is required."));
        }
        String email = trimToNull(readString(request, "email"));
        if (!StringUtils.hasText(email)) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email is required."));
        }
        String roleId = trimToNull(readString(request, "roleId"));
        RoleType roleType = resolveRoleType(roleId);
        if (roleType == null) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Role is required."));
        }
        String organizationId = resolveOrganizationId(authentication, tenantId);
        String agencyId = trimToNull(readString(request, "agencyId"));

        Mono<Person> personMono = resolvePersonByLogin(email)
                .switchIfEmpty(createPerson(email));

        return personMono.flatMap(person -> {
            if (roleType == RoleType.ORGANIZATION_ADMIN) {
                if (!StringUtils.hasText(organizationId)) {
                    return Mono.error(new ResponseStatusException(
                            HttpStatus.BAD_REQUEST,
                            "Organization context is required."
                    ));
                }
                return ensureAdminProfile(person.getId(), organizationId, null, "organization_admin")
                        .then(buildMemberResponse(person, organizationId));
            }
            if (!StringUtils.hasText(agencyId)) {
                return Mono.error(new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "agencyId is required for this role."
                ));
            }
            return agencyRepository.findById(agencyId)
                    .switchIfEmpty(Mono.error(new ResponseStatusException(
                            HttpStatus.NOT_FOUND,
                            "Agency not found."
                    )))
                    .flatMap(agency -> {
                        String resolvedOrgId = StringUtils.hasText(organizationId)
                                ? organizationId
                                : agency.getOrganizationId();
                        if (roleType == RoleType.AGENCY_ADMIN) {
                            return ensureAdminProfile(
                                    person.getId(),
                                    resolvedOrgId,
                                    agency.getId(),
                                    "agency_admin"
                            ).then(buildMemberResponse(person, resolvedOrgId));
                        }
                        return ensureCashierProfile(person.getId())
                                .then(ensureAssignment(person.getId(), agency.getId()))
                                .then(buildMemberResponse(person, resolvedOrgId));
                    });
        });
    }

    private Mono<Map<String, Object>> buildMemberResponse(Person person, String organizationId) {
        Mono<AdminProfile> adminMono = adminProfileRepository.findByPersonId(person.getId());
        Mono<CashierProfile> cashierMono = cashierProfileRepository.findByPersonId(person.getId());

        return adminMono.flatMap(admin -> toAdminMember(person, admin, organizationId))
                .switchIfEmpty(cashierMono.flatMap(cashier -> toCashierMember(person, organizationId)))
                .switchIfEmpty(Mono.just(toMember(person, organizationId, null, null, null)));
    }

    private Mono<Map<String, Object>> toAdminMember(Person person, AdminProfile admin, String organizationId) {
        String roleName = mapAdminRole(admin.getRoleType());
        String adminOrgId = trimToNull(admin.getOrganizationId());
        String adminAgencyId = trimToNull(admin.getAgencyId());
        if (StringUtils.hasText(adminAgencyId)) {
            return agencyRepository.findById(adminAgencyId)
                    .map(agency -> toMember(
                            person,
                            StringUtils.hasText(adminOrgId) ? adminOrgId : agency.getOrganizationId(),
                            roleName,
                            agency.getId(),
                            agency.getName()
                    ))
                    .defaultIfEmpty(toMember(person, adminOrgId, roleName, adminAgencyId, null));
        }
        return Mono.just(toMember(person, adminOrgId, roleName, null, null));
    }

    private Mono<Map<String, Object>> toCashierMember(Person person, String organizationId) {
        String activeSql = "SELECT ca.agency_id, ag.name AS agency_name, ag.organization_id "
                + "FROM cashier_agency_assignment ca "
                + "JOIN agency ag ON ag.id = ca.agency_id "
                + "WHERE ca.cashier_id = $1 "
                + "AND ca.assigned_by IS NOT NULL "
                + "AND (ca.start_on IS NULL OR ca.start_on <= NOW()) "
                + "AND (ca.end_on IS NULL OR ca.end_on >= NOW()) "
                + (StringUtils.hasText(organizationId)
                ? "AND ag.organization_id = $2 "
                : "")
                + "ORDER BY ca.assigned_on DESC LIMIT 1";
        var activeSpec = entityTemplate.getDatabaseClient().sql(activeSql).bind(0, person.getId());
        if (StringUtils.hasText(organizationId)) {
            activeSpec = activeSpec.bind(1, organizationId);
        }
        Mono<Map<String, Object>> activeMember = activeSpec.map((row, meta) -> toMember(
                        person,
                        row.get("organization_id", String.class),
                        ROLE_CASHIER,
                        row.get("agency_id", String.class),
                        row.get("agency_name", String.class)
                ))
                .one();

        String baseSql = "SELECT cp.base_agency_id, ag.name AS agency_name, ag.organization_id "
                + "FROM cashier_profile cp "
                + "LEFT JOIN agency ag ON ag.id = cp.base_agency_id "
                + "WHERE cp.person_id = $1 "
                + (StringUtils.hasText(organizationId)
                ? "AND ag.organization_id = $2 "
                : "");
        var baseSpec = entityTemplate.getDatabaseClient().sql(baseSql).bind(0, person.getId());
        if (StringUtils.hasText(organizationId)) {
            baseSpec = baseSpec.bind(1, organizationId);
        }
        Mono<Map<String, Object>> baseMember = baseSpec.map((row, meta) -> toMember(
                        person,
                        row.get("organization_id", String.class),
                        ROLE_CASHIER,
                        row.get("base_agency_id", String.class),
                        row.get("agency_name", String.class)
                ))
                .one()
                .defaultIfEmpty(toMember(person, organizationId, ROLE_CASHIER, null, null));

        return activeMember.switchIfEmpty(baseMember);
    }

    private Map<String, Object> toMember(
            Person person,
            String organizationId,
            String roleName,
            String agencyId,
            String agencyName
    ) {
        Map<String, Object> response = new HashMap<>();
        response.put("id", person.getId());
        response.put("organizationId", organizationId);
        response.put("userId", person.getId());
        response.put("userEmail", person.getMail());
        response.put("userFirstName", person.getUserFirstName());
        response.put("userLastName", null);
        response.put("agencyId", agencyId);
        response.put("agencyName", agencyName);
        response.put("roleId", roleName);
        response.put("roleName", roleName);
        response.put("isActive", person.getActif());
        response.put("joinedAt", null);
        return response;
    }

    private Map<String, Object> role(String name, String description) {
        Map<String, Object> response = new HashMap<>();
        response.put("id", name);
        response.put("name", name);
        response.put("description", description);
        return response;
    }

    private Mono<Person> resolvePersonByLogin(String login) {
        String trimmed = trimToNull(login);
        if (!StringUtils.hasText(trimmed)) {
            return Mono.empty();
        }
        return personRepository.findByUserName(trimmed)
                .switchIfEmpty(personRepository.findByMail(trimmed));
    }

    private Mono<Person> createPerson(String email) {
        Person person = new Person();
        person.setId(UUID.randomUUID().toString());
        person.setUserName(email);
        person.setUserFirstName(email);
        person.setMail(email);
        person.setActif(true);
        person.setPassword(passwordService.hashPassword(UUID.randomUUID().toString()));
        return entityTemplate.insert(Person.class).using(person);
    }

    private Mono<Void> ensureAdminProfile(
            String personId,
            String organizationId,
            String agencyId,
            String roleType
    ) {
        return adminProfileRepository.findByPersonId(personId)
                .flatMap(existing -> {
                    boolean updated = false;
                    if (StringUtils.hasText(roleType)
                            && (existing.getRoleType() == null
                            || !"superadmin".equalsIgnoreCase(existing.getRoleType()))
                            && !roleType.equalsIgnoreCase(existing.getRoleType())) {
                        existing.setRoleType(roleType);
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
                })
                .switchIfEmpty(Mono.defer(() -> {
                    AdminProfile profile = new AdminProfile();
                    profile.setId(UUID.randomUUID().toString());
                    profile.setPersonId(personId);
                    profile.setRoleType(roleType);
                    profile.setOrganizationId(organizationId);
                    profile.setAgencyId(agencyId);
                    return entityTemplate.insert(AdminProfile.class).using(profile).then();
                }));
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
                    return entityTemplate.insert(CashierProfile.class).using(profile).then();
                });
    }

    private Mono<Void> ensureAssignment(String cashierId, String agencyId) {
        return assignmentRepository.findByCashierIdAndAgencyId(cashierId, agencyId)
                .switchIfEmpty(Mono.defer(() -> {
                    CashierAgencyAssignment assignment = new CashierAgencyAssignment();
                    assignment.setId(UUID.randomUUID().toString());
                    assignment.setCashierId(cashierId);
                    assignment.setAgencyId(agencyId);
                    assignment.setStartOn(LocalDateTime.now());
                    assignment.setEndOn(null);
                    assignment.setAssignedOn(LocalDateTime.now());
                    assignment.setAssignedBy(null);
                    return entityTemplate.insert(CashierAgencyAssignment.class).using(assignment);
                }))
                .then();
    }

    private RoleType resolveRoleType(String roleId) {
        String normalized = normalizeRole(roleId);
        if (normalized == null) {
            return null;
        }
        return switch (normalized) {
            case "ROLE_ORG_ADMIN", "ROLE_ADMIN" -> RoleType.ORGANIZATION_ADMIN;
            case "ROLE_MANAGER", "ROLE_AGENCY_ADMIN" -> RoleType.AGENCY_ADMIN;
            case "ROLE_SALESPERSON", "ROLE_CASHIER" -> RoleType.CASHIER;
            default -> null;
        };
    }

    private String mapAdminRole(String roleType) {
        if (!StringUtils.hasText(roleType)) {
            return null;
        }
        return switch (roleType.trim().toLowerCase()) {
            case "superadmin" -> ROLE_SUPERADMIN;
            case "organization_admin" -> ROLE_ORG_ADMIN;
            case "agency_admin" -> ROLE_AGENCY_ADMIN;
            default -> null;
        };
    }

    private String resolveOrganizationId(Authentication authentication, String tenantId) {
        String headerValue = trimToNull(tenantId);
        String tokenOrgId = null;
        if (StringUtils.hasText(headerValue)) {
            if (authentication != null && authentication.getDetails() instanceof JwtPayload payload) {
                tokenOrgId = trimToNull(payload.getOrganizationId());
            }
            if (StringUtils.hasText(tokenOrgId) && !tokenOrgId.equals(headerValue)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Organization mismatch.");
            }
            return headerValue;
        }
        if (authentication != null && authentication.getDetails() instanceof JwtPayload payload) {
            return payload.getOrganizationId();
        }
        return null;
    }

    private String readString(Map<String, Object> payload, String key) {
        if (payload == null || key == null) {
            return null;
        }
        Object value = payload.get(key);
        return value != null ? String.valueOf(value) : null;
    }

    private String normalizeRole(String role) {
        if (!StringUtils.hasText(role)) {
            return null;
        }
        String normalized = role.trim().toUpperCase();
        return normalized.startsWith("ROLE_") ? normalized : "ROLE_" + normalized;
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private enum RoleType {
        ORGANIZATION_ADMIN,
        AGENCY_ADMIN,
        CASHIER
    }
}
