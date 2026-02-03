package com.erp.cashier.controller;

import com.erp.cashier.dto.CloseSessionRequest;
import com.erp.cashier.dto.CloseSessionResponse;
import com.erp.cashier.dto.MessageResponse;
import com.erp.cashier.dto.OpenSessionRequest;
import com.erp.cashier.dto.SessionResponse;
import com.erp.cashier.security.JwtPayload;
import com.erp.cashier.service.SessionAdminService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Session endpoints for admin and cashier users.
 *
 * @author ERP Cashier Team
 * @since 2025-01-02
 */
@RestController
@RequestMapping("/api/sessions")
public class SessionController {
    private final SessionAdminService sessionAdminService;

    /**
     * Creates the session controller.
     *
     * @param sessionAdminService session admin service
     */
    public SessionController(SessionAdminService sessionAdminService) {
        this.sessionAdminService = sessionAdminService;
    }

    /**
     * Lists sessions for the authenticated user.
     *
     * @param authentication authentication payload
     * @return sessions
     */
    @GetMapping
    @PreAuthorize("hasAuthority('ROLE_ADMIN') or hasAuthority('ROLE_CASHIER')")
    public Flux<SessionResponse> listSessions(Authentication authentication) {
        if (isCashier(authentication)) {
            return sessionAdminService.listSessionsByCashier(
                    resolveUserId(authentication),
                    resolveOrganizationId(authentication),
                    resolveAgencyId(authentication)
            );
        }
        String organizationId = resolveOrganizationId(authentication);
        String agencyId = resolveAgencyId(authentication);
        boolean restrictToOrganization = StringUtils.hasText(organizationId);
        boolean restrictToAgency = StringUtils.hasText(agencyId);
        return sessionAdminService.listSessions(
                organizationId,
                agencyId,
                restrictToAgency,
                restrictToOrganization
        );
    }

    /**
     * Opens a new session.
     *
     * @param request open session request
     * @param authentication authentication payload
     * @return created session
     */
    @PostMapping
    @PreAuthorize("hasAuthority('ROLE_ADMIN') or hasAuthority('ROLE_CASHIER')")
    public Mono<SessionResponse> openSession(
            @RequestBody OpenSessionRequest request,
            Authentication authentication
    ) {
        String organizationId = resolveOrganizationId(authentication);
        String agencyId = resolveAgencyId(authentication);
        boolean restrictToOrganization = StringUtils.hasText(organizationId);
        boolean restrictToAgency = StringUtils.hasText(agencyId);
        return sessionAdminService.openSession(
                request,
                resolveUserId(authentication),
                isCashier(authentication),
                organizationId,
                agencyId,
                restrictToOrganization,
                restrictToAgency
        );
    }

    /**
     * Closes an open session.
     *
     * @param sessionId session identifier
     * @param request close request
     * @param authentication authentication payload
     * @return close response
     */
    @PostMapping("/{sessionId}/close")
    @PreAuthorize("hasAuthority('ROLE_ADMIN') or hasAuthority('ROLE_CASHIER')")
    public Mono<CloseSessionResponse> closeSession(
            @PathVariable("sessionId") String sessionId,
            @RequestBody CloseSessionRequest request,
            Authentication authentication
    ) {
        String organizationId = resolveOrganizationId(authentication);
        String agencyId = resolveAgencyId(authentication);
        boolean restrictToOrganization = StringUtils.hasText(organizationId);
        boolean restrictToAgency = StringUtils.hasText(agencyId);
        return sessionAdminService.closeSession(
                        sessionId,
                        request != null ? request.getPhysicalTotal() : null,
                        resolveUserId(authentication),
                        isCashier(authentication),
                        organizationId,
                        agencyId,
                        restrictToOrganization,
                        restrictToAgency
                )
                .map(session -> new CloseSessionResponse(
                        true,
                        "Session closed successfully",
                        new CloseSessionResponse.CloseSessionReconciliationResponse(
                                session,
                                session.getReconciliation()
                        )
                ));
    }

    /**
     * Locks a session.
     *
     * @param sessionId session identifier
     * @param authentication authentication payload
     * @return lock response
     */
    @PostMapping("/{sessionId}/lock")
    @PreAuthorize("hasAuthority('ROLE_MANAGER')")
    public Mono<MessageResponse> lockSession(
            @PathVariable("sessionId") String sessionId,
            Authentication authentication
    ) {
        String organizationId = resolveOrganizationId(authentication);
        String agencyId = resolveAgencyId(authentication);
        boolean restrictToOrganization = StringUtils.hasText(organizationId);
        boolean restrictToAgency = StringUtils.hasText(agencyId);
        return sessionAdminService.setSessionLocked(
                        sessionId,
                        true,
                        organizationId,
                        agencyId,
                        restrictToAgency,
                        restrictToOrganization
                )
                .map(session -> new MessageResponse("Session locked"));
    }

    /**
     * Unlocks a session.
     *
     * @param sessionId session identifier
     * @param authentication authentication payload
     * @return unlock response
     */
    @DeleteMapping("/{sessionId}/lock")
    @PreAuthorize("hasAuthority('ROLE_MANAGER')")
    public Mono<MessageResponse> unlockSession(
            @PathVariable("sessionId") String sessionId,
            Authentication authentication
    ) {
        String organizationId = resolveOrganizationId(authentication);
        String agencyId = resolveAgencyId(authentication);
        boolean restrictToOrganization = StringUtils.hasText(organizationId);
        boolean restrictToAgency = StringUtils.hasText(agencyId);
        return sessionAdminService.setSessionLocked(
                        sessionId,
                        false,
                        organizationId,
                        agencyId,
                        restrictToAgency,
                        restrictToOrganization
                )
                .map(session -> new MessageResponse("Session unlocked"));
    }

    private String resolveUserId(Authentication authentication) {
        if (authentication == null) {
            return null;
        }
        Object details = authentication.getDetails();
        if (details instanceof JwtPayload payload) {
            return payload.getUserId();
        }
        return null;
    }

    private String resolveAgencyId(Authentication authentication) {
        if (authentication == null) {
            return null;
        }
        Object details = authentication.getDetails();
        if (details instanceof JwtPayload payload) {
            return payload.getAgencyId();
        }
        return null;
    }

    private String resolveOrganizationId(Authentication authentication) {
        if (authentication == null) {
            return null;
        }
        Object details = authentication.getDetails();
        if (details instanceof JwtPayload payload) {
            return payload.getOrganizationId();
        }
        return null;
    }

    private boolean isCashier(Authentication authentication) {
        if (authentication == null || authentication.getAuthorities() == null) {
            return false;
        }
        return authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_CASHIER".equals(authority.getAuthority()));
    }
}
