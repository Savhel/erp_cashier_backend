package com.erp.cashier.controller;

import com.erp.cashier.dto.OrganizationResponse;
import com.erp.cashier.model.Agency;
import com.erp.cashier.model.Organization;
import com.erp.cashier.repository.AgencyRepository;
import com.erp.cashier.repository.OrganizationRepository;
import com.erp.cashier.security.JwtPayload;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Organization endpoints backed by local database.
 *
 * @author ERP Cashier Team
 * @since 2026-01-30
 */
@RestController
@RequestMapping("/api/organizations")
public class ExternalOrganizationController {
    private final OrganizationRepository organizationRepository;
    private final AgencyRepository agencyRepository;

    /**
     * Creates the organization controller.
     *
     * @param organizationRepository organization repository
     * @param agencyRepository agency repository
     */
    public ExternalOrganizationController(
            OrganizationRepository organizationRepository,
            AgencyRepository agencyRepository
    ) {
        this.organizationRepository = organizationRepository;
        this.agencyRepository = agencyRepository;
    }

    /**
     * Returns the current organization from the local database.
     *
     * @param authentication authentication payload
     * @return current organization
     */
    @GetMapping("/current")
    public Mono<OrganizationResponse> getCurrentOrganization(Authentication authentication) {
        JwtPayload payload = resolvePayload(authentication);
        String organizationId = resolveOrganizationId(payload);
        if (!StringUtils.hasText(organizationId)) {
            return Mono.error(new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Organization context is required."
            ));
        }
        return organizationRepository.findById(organizationId)
                .map(this::toResponse)
                .switchIfEmpty(Mono.error(new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Organization not found."
                )));
    }

    /**
     * Lists organizations for the current user (local database).
     *
     * @param authentication authentication payload
     * @return organizations
     */
    @GetMapping("/my")
    public Flux<OrganizationResponse> listMyOrganizations(Authentication authentication) {
        JwtPayload payload = resolvePayload(authentication);
        String roleType = payload.getRoleType();
        if ("superadmin".equalsIgnoreCase(roleType)) {
            return organizationRepository.findByIsActiveOrderByNameAsc(true)
                    .map(this::toResponse);
        }
        return resolveOrganizationIdFromPayload(payload)
                .flatMapMany(orgId -> organizationRepository.findById(orgId)
                        .map(this::toResponse)
                        .flux());
    }

    private Mono<String> resolveOrganizationIdFromPayload(JwtPayload payload) {
        String organizationId = resolveOrganizationId(payload);
        if (StringUtils.hasText(organizationId)) {
            return Mono.just(organizationId);
        }
        String agencyId = payload != null ? payload.getAgencyId() : null;
        if (!StringUtils.hasText(agencyId)) {
            return Mono.empty();
        }
        return agencyRepository.findById(agencyId)
                .map(Agency::getOrganizationId)
                .filter(StringUtils::hasText);
    }

    private String resolveOrganizationId(JwtPayload payload) {
        if (payload == null) {
            return null;
        }
        String organizationId = payload.getOrganizationId();
        return StringUtils.hasText(organizationId) ? organizationId : null;
    }

    private JwtPayload resolvePayload(Authentication authentication) {
        if (authentication == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required.");
        }
        Object details = authentication.getDetails();
        if (details instanceof JwtPayload payload) {
            return payload;
        }
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required.");
    }

    private OrganizationResponse toResponse(Organization organization) {
        if (organization == null) {
            return null;
        }
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
}
