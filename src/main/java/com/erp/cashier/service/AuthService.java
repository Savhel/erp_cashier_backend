package com.erp.cashier.service;

import com.erp.cashier.dto.AuthSessionResponse;
import com.erp.cashier.dto.LoginRequest;
import com.erp.cashier.dto.LoginResponse;
import com.erp.cashier.dto.LoginUserResponse;
import com.erp.cashier.dto.NamedEntityResponse;
import com.erp.cashier.dto.OrganizationMembershipResponse;
import com.erp.cashier.dto.external.RtAuthResponse;
import com.erp.cashier.dto.external.RtUserResponse;
import com.erp.cashier.model.AdminProfile;
import com.erp.cashier.model.Agency;
import com.erp.cashier.model.CashRegisterSession;
import com.erp.cashier.model.Organization;
import com.erp.cashier.model.Person;
import com.erp.cashier.repository.AdminProfileRepository;
import com.erp.cashier.repository.AgencyRepository;
import com.erp.cashier.repository.CashRegisterSessionRepository;
import com.erp.cashier.repository.CashierProfileRepository;
import com.erp.cashier.repository.OrganizationRepository;
import com.erp.cashier.repository.PersonRepository;
import com.erp.cashier.security.JwtPayload;
import com.erp.cashier.security.JwtService;
import com.erp.cashier.security.KernelTokenRelayStore;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

/**
 * Authentication service.
 *
 * @author ERP Cashier Team
 * @since 2025-01-02
 */
@Service
public class AuthService {
    private static final String ROLE_USER = "user";
    private static final String ROLE_ADMIN = "admin";
    private static final String ROLE_CASHIER = "cashier";
    private static final String ROLE_TYPE_SALESPERSON = "salesperson";
    private static final String TOKEN_TYPE_BEARER = "Bearer";
    private static final String SESSION_STATE_OPEN = "ouverte";
    private static final Logger LOGGER = LoggerFactory.getLogger(AuthService.class);

    private final OrganizationRepository organizationRepository;
    private final AgencyRepository agencyRepository;
    private final PersonRepository personRepository;
    private final AdminProfileRepository adminProfileRepository;
    private final CashierProfileRepository cashierProfileRepository;
    private final CashRegisterSessionRepository sessionRepository;
    private final JwtService jwtService;
    private final KernelTokenRelayStore kernelTokenRelayStore;
    private final R2dbcEntityTemplate entityTemplate;
    private final RtComOpsClient rtComOpsClient;
    private final AuditService auditService;
    private final com.erp.cashier.config.CashierCoreProperties cashierCoreProperties;

    /**
     * Creates the authentication service.
     *
     * @param organizationRepository organization repository
     * @param agencyRepository agency repository
     * @param personRepository person repository
     * @param adminProfileRepository admin profile repository
     * @param cashierProfileRepository cashier profile repository
     * @param jwtService jwt service
     * @param entityTemplate entity template
     * @param rtComOpsClient RT_ComOps client
     */
    public AuthService(
            OrganizationRepository organizationRepository,
            AgencyRepository agencyRepository,
            PersonRepository personRepository,
            AdminProfileRepository adminProfileRepository,
            CashierProfileRepository cashierProfileRepository,
            CashRegisterSessionRepository sessionRepository,
            JwtService jwtService,
            KernelTokenRelayStore kernelTokenRelayStore,
            R2dbcEntityTemplate entityTemplate,
            RtComOpsClient rtComOpsClient,
            AuditService auditService,
            com.erp.cashier.config.CashierCoreProperties cashierCoreProperties
    ) {
        this.organizationRepository = organizationRepository;
        this.agencyRepository = agencyRepository;
        this.personRepository = personRepository;
        this.adminProfileRepository = adminProfileRepository;
        this.cashierProfileRepository = cashierProfileRepository;
        this.sessionRepository = sessionRepository;
        this.jwtService = jwtService;
        this.kernelTokenRelayStore = kernelTokenRelayStore;
        this.entityTemplate = entityTemplate;
        this.rtComOpsClient = rtComOpsClient;
        this.auditService = auditService;
        this.cashierCoreProperties = cashierCoreProperties;
    }

    /**
     * Login 100% délégué à iwm (mode façade traductrice, passthrough-all) : aucune table locale.
     * Vérifie les identifiants + récupère le contexte caisse côté iwm, renvoie le JWT iwm dans la
     * forme attendue par le front.
     */
    private Mono<LoginResponse> loginViaKernel(String email, String password) {
        return rtComOpsClient.loginViaKernel(email, password)
                .flatMap(result -> rtComOpsClient.fetchCashierContext(email, result.token())
                        .map(ctx -> {
                            LoginUserResponse user = new LoginUserResponse(
                                    result.userId(),
                                    StringUtils.hasText(result.username()) ? result.username() : email,
                                    ctx.kind(),
                                    ctx.kind(),
                                    ctx.agencyId(),
                                    ctx.organizationId());
                            return new LoginResponse(true, user, result.token(), "Bearer",
                                    jwtService.getAccessTokenTtlSeconds());
                        }));
    }

    /**
     * Authenticates a user based on the login request.
     *
     * @param request login request
     * @return login response
     */
    public Mono<LoginResponse> login(LoginRequest request) {
        if (request == null) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Login request is required"));
        }
        String email = resolveEmail(request);
        if (!StringUtils.hasText(email) || !StringUtils.hasText(request.getPassword())) {
            return Mono.error(new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Email and password are required"
            ));
        }

        // Mode façade traductrice : tout passe par iwm, aucune table locale.
        if (cashierCoreProperties.isPassthroughAll()) {
            return loginViaKernel(email, request.getPassword());
        }

        return rtComOpsClient.login(email, request.getPassword())
                .switchIfEmpty(Mono.error(new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "Invalid credentials"
                )))
                .flatMap(response -> resolvePersonFromRt(response, email)
                        .map(person -> new AuthenticatedPerson(person, response.getToken())))
                .switchIfEmpty(Mono.error(new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "User not found locally."
                )))
                .flatMap(authenticated -> buildLocalLoginResponse(
                        authenticated.person(), authenticated.kernelToken()));
    }

    /**
     * Returns the current session payload for an authenticated user.
     *
     * @param payload decoded JWT payload
     * @return session response
     */
    public Mono<AuthSessionResponse> getSession(JwtPayload payload) {
        if (payload == null) {
            return Mono.error(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized"));
        }
        LoginUserResponse user = new LoginUserResponse(
                payload.getUserId(),
                payload.getUsername(),
                payload.getRole(),
                payload.getRoleType(),
                payload.getAgencyId(),
                payload.getOrganizationId()
        );
        Mono<Optional<NamedEntityResponse>> organization = resolveOrganization(payload.getOrganizationId());
        Mono<Optional<NamedEntityResponse>> agency = resolveAgency(payload.getAgencyId());
        return Mono.zip(organization, agency)
                .map(tuple -> new AuthSessionResponse(
                        user,
                        tuple.getT1().orElse(null),
                        tuple.getT2().orElse(null)
                ));
    }

    /**
     * Logs out the current user and records an audit event.
     *
     * @param payload decoded JWT payload
     * @return logout response
     */
    public Mono<com.erp.cashier.dto.SuccessResponse> logout(JwtPayload payload) {
        if (payload == null) {
            return Mono.just(new com.erp.cashier.dto.SuccessResponse(true));
        }
        return recordAuthEvent("logout", payload)
                .thenReturn(new com.erp.cashier.dto.SuccessResponse(true));
    }

    private Mono<LoginResponse> buildLocalLoginResponse(Person person, String kernelToken) {
        return resolveProfiles(person)
                .flatMap(profile -> buildLoginResponse(person, profile, kernelToken));
    }

    private Mono<LoginResponse> buildLoginResponse(Person person, UserProfileContext profile, String kernelToken) {
        LoginUserResponse loginUser = new LoginUserResponse(
                person.getId(),
                resolveUsername(person),
                profile.role(),
                profile.roleType(),
                profile.agencyId(),
                profile.organizationId()
        );
        return attachSalesAgentAccounts(loginUser)
                .flatMap(enrichedUser -> {
                    String token = jwtService.generateAccessToken(enrichedUser);
                    kernelTokenRelayStore.store(token, kernelToken);
                    long expiresIn = jwtService.getAccessTokenTtlSeconds();
                    LoginResponse response = new LoginResponse(
                            true,
                            enrichedUser,
                            token,
                            TOKEN_TYPE_BEARER,
                            expiresIn
                    );
                    return buildOrganizations(profile).map(organizations -> {
                        response.setOrganizations(attachOrganizationTokens(person, organizations, profile, kernelToken));
                        return response;
                    }).flatMap(resp -> recordAuthEvent("login", resp.getUser()).thenReturn(resp));
                });
    }

    private Mono<Void> recordAuthEvent(String type, LoginUserResponse user) {
        if (user == null) {
            return Mono.empty();
        }
        return resolveAuthSessionId(user.getId(), user.getRole(), user.getRoleType())
                .flatMap(sessionId -> auditService.recordAuthEvent(
                        type,
                        user.getId(),
                        normalizeSessionId(sessionId),
                        buildAuthPayload(user)
                ))
                .onErrorResume(ex -> Mono.empty());
    }

    private Mono<Void> recordAuthEvent(String type, JwtPayload payload) {
        if (payload == null) {
            return Mono.empty();
        }
        return resolveAuthSessionId(payload.getUserId(), payload.getRole(), payload.getRoleType())
                .flatMap(sessionId -> auditService.recordAuthEvent(
                        type,
                        payload.getUserId(),
                        normalizeSessionId(sessionId),
                        buildAuthPayload(payload)
                ))
                .onErrorResume(ex -> Mono.empty());
    }

    private Mono<String> resolveAuthSessionId(String userId, String role, String roleType) {
        if (!ROLE_CASHIER.equalsIgnoreCase(role)
                || !ROLE_TYPE_SALESPERSON.equalsIgnoreCase(roleType)
                || !StringUtils.hasText(userId)) {
            return Mono.just("");
        }
        return sessionRepository.findLatestByOpenByAndState(userId, SESSION_STATE_OPEN)
                .map(CashRegisterSession::getId)
                .switchIfEmpty(Mono.just(""));
    }

    private String normalizeSessionId(String sessionId) {
        return StringUtils.hasText(sessionId) ? sessionId : null;
    }

    private Map<String, Object> buildAuthPayload(LoginUserResponse user) {
        Map<String, Object> payload = new java.util.LinkedHashMap<>();
        putIfPresent(payload, "user_id", user.getId());
        putIfPresent(payload, "username", user.getUsername());
        putIfPresent(payload, "role", user.getRole());
        putIfPresent(payload, "role_type", user.getRoleType());
        putIfPresent(payload, "organization_id", user.getOrganizationId());
        putIfPresent(payload, "agency_id", user.getAgencyId());
        putIfPresent(payload, "banking_account", user.getBankingAccount());
        putIfPresent(payload, "accounting_account", user.getAccountingAccount());
        return payload;
    }

    private Map<String, Object> buildAuthPayload(JwtPayload payload) {
        Map<String, Object> data = new java.util.LinkedHashMap<>();
        putIfPresent(data, "user_id", payload.getUserId());
        putIfPresent(data, "username", payload.getUsername());
        putIfPresent(data, "role", payload.getRole());
        putIfPresent(data, "role_type", payload.getRoleType());
        putIfPresent(data, "organization_id", payload.getOrganizationId());
        putIfPresent(data, "agency_id", payload.getAgencyId());
        return data;
    }

    private void putIfPresent(Map<String, Object> payload, String key, Object value) {
        if (payload == null || !StringUtils.hasText(key) || value == null) {
            return;
        }
        payload.put(key, value);
    }

    private String resolveEmail(LoginRequest request) {
        String email = StringUtils.trimWhitespace(request.getEmail());
        if (StringUtils.hasText(email)) {
            return email;
        }
        String username = StringUtils.trimWhitespace(request.getUsername());
        return StringUtils.hasText(username) ? username : null;
    }

    private String resolveUsername(Person person) {
        if (person == null) {
            return null;
        }
        if (StringUtils.hasText(person.getMail())) {
            return person.getMail().trim();
        }
        return StringUtils.hasText(person.getUserName()) ? person.getUserName() : person.getId();
    }

    private Mono<Optional<NamedEntityResponse>> resolveOrganization(String organizationId) {
        return resolveNamedEntity(
                organizationId,
                organizationRepository::findById,
                organization -> new NamedEntityResponse(organization.getId(), organization.getName())
        );
    }

    private Mono<Optional<NamedEntityResponse>> resolveAgency(String agencyId) {
        return resolveNamedEntity(
                agencyId,
                agencyRepository::findById,
                agency -> new NamedEntityResponse(agency.getId(), agency.getName())
        );
    }

    private <T> Mono<Optional<NamedEntityResponse>> resolveNamedEntity(
            String id,
            Function<String, Mono<T>> fetcher,
            Function<T, NamedEntityResponse> mapper
    ) {
        if (!StringUtils.hasText(id)) {
            return Mono.just(Optional.empty());
        }
        return fetcher.apply(id.trim())
                .map(entity -> Optional.of(mapper.apply(entity)))
                .defaultIfEmpty(Optional.empty());
    }

    private Mono<Person> resolvePersonByLogin(String login) {
        String trimmed = StringUtils.trimWhitespace(login);
        if (!StringUtils.hasText(trimmed)) {
            return Mono.empty();
        }
        return personRepository.findByUserName(trimmed)
                .switchIfEmpty(personRepository.findByMail(trimmed));
    }

    private Mono<Person> resolvePersonFromRt(RtAuthResponse response, String fallbackLogin) {
        if (response == null) {
            return Mono.empty();
        }
        RtUserResponse user = response.getUser();
        if (user == null) {
            return resolvePersonByLogin(fallbackLogin);
        }
        String userId = trimToNull(user.getId());
        if (StringUtils.hasText(userId)) {
            return personRepository.findById(userId)
                    .switchIfEmpty(resolvePersonByLogin(user.getEmail()));
        }
        return resolvePersonByLogin(user.getEmail());
    }

    private Mono<UserProfileContext> resolveProfiles(Person person) {
        Mono<Optional<AdminProfile>> adminMono = adminProfileRepository.findByPersonId(person.getId())
                .map(Optional::of)
                .defaultIfEmpty(Optional.empty());
        Mono<Optional<com.erp.cashier.model.CashierProfile>> cashierMono =
                cashierProfileRepository.findByPersonId(person.getId())
                        .map(Optional::of)
                        .defaultIfEmpty(Optional.empty());

        return Mono.zip(adminMono, cashierMono)
                .flatMap(tuple -> {
                    Optional<AdminProfile> admin = tuple.getT1();
                    Optional<com.erp.cashier.model.CashierProfile> cashier = tuple.getT2();
                    if (admin.isPresent()) {
                        return resolveAdminContext(admin.get());
                    }
                    if (cashier.isPresent()) {
                        Boolean active = cashier.get().getIsActive();
                        if (active != null && !active) {
                            LOGGER.info("Login blocked for cashier {}: profile deactivated", person.getId());
                            return Mono.error(new ResponseStatusException(
                                    HttpStatus.UNAUTHORIZED,
                                    "Invalid credentials"
                            ));
                        }
                        return requireOpenCashierSession(person.getId())
                                .then(resolveCashierContext(person));
                    }
                    return Mono.just(new UserProfileContext(ROLE_USER, null, null, null));
                });
    }

    private Mono<Void> requireOpenCashierSession(String cashierId) {
        if (!StringUtils.hasText(cashierId)) {
            return Mono.error(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials"));
        }
        return sessionRepository.findLatestByOpenByAndState(cashierId, SESSION_STATE_OPEN)
                .switchIfEmpty(Mono.error(new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "No open session found."
                )))
                .flatMap(session -> {
                    if (Boolean.TRUE.equals(session.getIsLocked())) {
                        return Mono.error(new ResponseStatusException(
                                HttpStatus.UNAUTHORIZED,
                                "Session is locked."
                        ));
                    }
                    return Mono.empty();
                });
    }

    private Mono<LoginUserResponse> attachSalesAgentAccounts(LoginUserResponse loginUser) {
        if (loginUser == null) {
            return Mono.empty();
        }
        if (!ROLE_CASHIER.equalsIgnoreCase(loginUser.getRole())
                || !ROLE_TYPE_SALESPERSON.equalsIgnoreCase(loginUser.getRoleType())) {
            return Mono.just(loginUser);
        }
        return sessionRepository.findLatestByOpenByAndState(loginUser.getId(), SESSION_STATE_OPEN)
                .flatMap(session -> entityTemplate.getDatabaseClient()
                        .sql("SELECT sale_agent_bank_account, sale_agent_accounting_account "
                                + "FROM cash_register WHERE id = $1")
                        .bind(0, session.getCashRegisterId())
                        .map((row, meta) -> {
                            loginUser.setBankingAccount(row.get("sale_agent_bank_account", String.class));
                            loginUser.setAccountingAccount(row.get("sale_agent_accounting_account", String.class));
                            return loginUser;
                        })
                        .one())
                .defaultIfEmpty(loginUser)
                .onErrorResume(ex -> Mono.just(loginUser));
    }

    private Mono<UserProfileContext> resolveAdminContext(AdminProfile admin) {
        String roleType = trimToNull(admin.getRoleType());
        String orgId = trimToNull(admin.getOrganizationId());
        String agencyId = trimToNull(admin.getAgencyId());
        String normalizedRoleType = AdminRoleResolver.normalizeRoleType(roleType, agencyId);
        if (StringUtils.hasText(agencyId) && !StringUtils.hasText(orgId)) {
            return agencyRepository.findById(agencyId)
                    .map(agency -> new UserProfileContext(
                            ROLE_ADMIN,
                            normalizedRoleType,
                            agency.getOrganizationId(),
                            agencyId
                    ))
                    .defaultIfEmpty(new UserProfileContext(ROLE_ADMIN, normalizedRoleType, null, agencyId));
        }
        return Mono.just(new UserProfileContext(ROLE_ADMIN, normalizedRoleType, orgId, agencyId));
    }

    private Mono<UserProfileContext> resolveCashierContext(Person person) {
        String sql = "SELECT ca.agency_id, ag.organization_id "
                + "FROM cashier_agency_assignment ca "
                + "JOIN agency ag ON ag.id = ca.agency_id "
                + "WHERE ca.cashier_id = $1 "
                + "AND ca.assigned_by IS NOT NULL "
                + "AND (ca.start_on IS NULL OR ca.start_on <= NOW()) "
                + "AND (ca.end_on IS NULL OR ca.end_on >= NOW()) "
                + "ORDER BY ca.assigned_on DESC LIMIT 1";
        Mono<UserProfileContext> activeAssignment = entityTemplate.getDatabaseClient()
                .sql(sql)
                .bind(0, person.getId())
                .map((row, meta) -> new UserProfileContext(
                        ROLE_CASHIER,
                        ROLE_TYPE_SALESPERSON,
                        row.get("organization_id", String.class),
                        row.get("agency_id", String.class)
                ))
                .one()
                .switchIfEmpty(resolveCashierBaseContext(person.getId()));
        return activeAssignment
                .defaultIfEmpty(new UserProfileContext(ROLE_CASHIER, ROLE_TYPE_SALESPERSON, null, null));
    }

    private Mono<UserProfileContext> resolveCashierBaseContext(String personId) {
        String sql = "SELECT cp.base_agency_id, ag.organization_id "
                + "FROM cashier_profile cp "
                + "LEFT JOIN agency ag ON ag.id = cp.base_agency_id "
                + "WHERE cp.person_id = $1";
        return entityTemplate.getDatabaseClient()
                .sql(sql)
                .bind(0, personId)
                .map((row, meta) -> new UserProfileContext(
                        ROLE_CASHIER,
                        ROLE_TYPE_SALESPERSON,
                        row.get("organization_id", String.class),
                        row.get("base_agency_id", String.class)
                ))
                .one();
    }

    private Mono<List<OrganizationMembershipResponse>> buildOrganizations(UserProfileContext profile) {
        if (profile == null) {
            return Mono.just(List.of());
        }
        String roleType = trimToNull(profile.roleType());
        if ("superadmin".equalsIgnoreCase(roleType)) {
            return organizationRepository.findByIsActiveOrderByNameAsc(true)
                    .map(org -> toMembership(org, "ROLE_SUPERADMIN", null))
                    .collectList();
        }
        if (!StringUtils.hasText(profile.organizationId())) {
            return Mono.just(List.of());
        }
        return organizationRepository.findById(profile.organizationId())
                .flatMap(org -> {
                    Mono<Agency> agencyMono = StringUtils.hasText(profile.agencyId())
                            ? agencyRepository.findById(profile.agencyId())
                            : Mono.empty();
                    return agencyMono
                            .map(agency -> toMembership(org, toRoleName(roleType), agency))
                            .defaultIfEmpty(toMembership(org, toRoleName(roleType), null));
                })
                .map(List::of)
                .defaultIfEmpty(List.of());
    }

    private OrganizationMembershipResponse toMembership(
            Organization organization,
            String roleName,
            Agency agency
    ) {
        OrganizationMembershipResponse membership = new OrganizationMembershipResponse();
        membership.setOrganizationId(organization != null ? organization.getId() : null);
        membership.setOrganizationName(organization != null ? organization.getName() : null);
        membership.setRoleName(roleName);
        if (agency != null) {
            membership.setAgencyId(agency.getId());
            membership.setAgencyName(agency.getName());
        }
        return membership;
    }

    private List<OrganizationMembershipResponse> attachOrganizationTokens(
            Person person,
            List<OrganizationMembershipResponse> organizations,
            UserProfileContext profile,
            String kernelToken
    ) {
        if (organizations == null || organizations.isEmpty()) {
            return organizations;
        }
        String username = resolveUsername(person);
        List<OrganizationMembershipResponse> enriched = new ArrayList<>(organizations.size());
        for (OrganizationMembershipResponse membership : organizations) {
            if (membership == null) {
                continue;
            }
            RoleInfo roleInfo = resolveRoleInfo(membership, profile);
            LoginUserResponse tokenUser = new LoginUserResponse(
                    person.getId(),
                    username,
                    roleInfo.role(),
                    roleInfo.roleType(),
                    membership.getAgencyId(),
                    membership.getOrganizationId()
            );
            String facadeToken = jwtService.generateAccessToken(tokenUser);
            kernelTokenRelayStore.store(facadeToken, kernelToken);
            membership.setAccessToken(facadeToken);
            enriched.add(membership);
        }
        return enriched;
    }

    private RoleInfo resolveRoleInfo(OrganizationMembershipResponse membership, UserProfileContext profile) {
        String roleName = normalizeRoleName(membership != null ? membership.getRoleName() : null);
        if (!StringUtils.hasText(roleName)) {
            return new RoleInfo(profile.role(), profile.roleType());
        }
        return switch (roleName) {
            case "ROLE_SALESPERSON", "ROLE_CASHIER" -> new RoleInfo(ROLE_CASHIER, ROLE_TYPE_SALESPERSON);
            case "ROLE_MANAGER", "ROLE_AGENCY_ADMIN" -> new RoleInfo(ROLE_ADMIN, "agency_admin");
            case "ROLE_ORG_ADMIN", "ROLE_ADMIN" -> new RoleInfo(ROLE_ADMIN, "organization_admin");
            case "ROLE_SUPERADMIN" -> new RoleInfo(ROLE_ADMIN, "superadmin");
            default -> new RoleInfo(profile.role(), profile.roleType());
        };
    }

    private String normalizeRoleName(String roleName) {
        if (!StringUtils.hasText(roleName)) {
            return null;
        }
        String normalized = roleName.trim().toUpperCase();
        return normalized.startsWith("ROLE_") ? normalized : "ROLE_" + normalized;
    }

    private String toRoleName(String roleType) {
        if (!StringUtils.hasText(roleType)) {
            return null;
        }
        String normalized = roleType.trim().toLowerCase();
        return switch (normalized) {
            case "superadmin" -> "ROLE_SUPERADMIN";
            case "organization_admin" -> "ROLE_ORG_ADMIN";
            case "agency_admin" -> "ROLE_MANAGER";
            default -> "ROLE_" + roleType.trim().toUpperCase();
        };
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private record UserProfileContext(
            String role,
            String roleType,
            String organizationId,
            String agencyId
    ) {
    }

    private record RoleInfo(String role, String roleType) {
    }
    private record AuthenticatedPerson(Person person, String kernelToken) {
    }

}
