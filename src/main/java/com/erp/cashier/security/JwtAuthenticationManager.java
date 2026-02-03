package com.erp.cashier.security;

import java.util.ArrayList;
import java.util.List;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;

/**
 * Reactive authentication manager for bearer tokens.
 *
 * @author ERP Cashier Team
 * @since 2025-01-02
 */
@Component
public class JwtAuthenticationManager implements ReactiveAuthenticationManager {
    private static final String ROLE_PREFIX = "ROLE_";

    private final JwtService jwtService;

    /**
     * Creates the authentication manager.
     *
     * @param jwtService JWT service
     */
    public JwtAuthenticationManager(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    public Mono<Authentication> authenticate(Authentication authentication) {
        if (authentication == null || authentication.getCredentials() == null) {
            return Mono.empty();
        }
        String token = authentication.getCredentials().toString();
        JwtPayload payload;
        try {
            payload = jwtService.parseToken(token);
        } catch (Exception ex) {
            return Mono.error(new BadCredentialsException("Invalid token", ex));
        }
        List<GrantedAuthority> authorities = buildAuthorities(payload);
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                payload.getUserId(),
                token,
                authorities
        );
        auth.setDetails(payload);
        return Mono.just(auth);
    }

    private List<GrantedAuthority> buildAuthorities(JwtPayload payload) {
        List<GrantedAuthority> authorities = new ArrayList<>();
        if (payload == null) {
            return authorities;
        }
        String role = normalizeRole(payload.getRole());
        if (StringUtils.hasText(role)) {
            authorities.add(new SimpleGrantedAuthority(ROLE_PREFIX + role));
        }
        String roleType = normalizeRole(payload.getRoleType());
        if (StringUtils.hasText(roleType)) {
            authorities.add(new SimpleGrantedAuthority(ROLE_PREFIX + roleType));
        }
        if ("AGENCY_ADMIN".equals(roleType)) {
            authorities.add(new SimpleGrantedAuthority(ROLE_PREFIX + "MANAGER"));
        }
        return authorities;
    }

    private String normalizeRole(String role) {
        if (!StringUtils.hasText(role)) {
            return null;
        }
        String normalized = role.trim().toUpperCase();
        return normalized.startsWith(ROLE_PREFIX)
                ? normalized.substring(ROLE_PREFIX.length())
                : normalized;
    }
}
