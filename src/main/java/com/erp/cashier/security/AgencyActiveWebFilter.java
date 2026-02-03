package com.erp.cashier.security;

import com.erp.cashier.repository.AgencyRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * Blocks requests when the authenticated agency is inactive.
 *
 * @author ERP Cashier Team
 * @since 2026-02-02
 */
@Component
public class AgencyActiveWebFilter implements WebFilter {
    private final AgencyRepository agencyRepository;

    /**
     * Creates the agency filter.
     *
     * @param agencyRepository agency repository
     */
    public AgencyActiveWebFilter(AgencyRepository agencyRepository) {
        this.agencyRepository = agencyRepository;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        return ReactiveSecurityContextHolder.getContext()
                .map(context -> context.getAuthentication())
                .flatMap(authentication -> enforceAgencyActive(authentication).then(chain.filter(exchange)))
                .switchIfEmpty(chain.filter(exchange));
    }

    private Mono<Void> enforceAgencyActive(Authentication authentication) {
        if (authentication == null) {
            return Mono.empty();
        }
        Object details = authentication.getDetails();
        if (!(details instanceof JwtPayload payload)) {
            return Mono.empty();
        }
        String agencyId = payload.getAgencyId();
        if (!StringUtils.hasText(agencyId)) {
            return Mono.empty();
        }
        return agencyRepository.findById(agencyId)
                .switchIfEmpty(Mono.error(new ResponseStatusException(
                        HttpStatus.FORBIDDEN,
                        "Agency is inactive."
                )))
                .flatMap(agency -> {
                    if (Boolean.TRUE.equals(agency.getIsActive())) {
                        return Mono.empty();
                    }
                    return Mono.error(new ResponseStatusException(
                            HttpStatus.FORBIDDEN,
                            "Agency is inactive."
                    ));
                });
    }
}
