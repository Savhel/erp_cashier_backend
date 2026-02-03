package com.erp.cashier.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.annotation.method.configuration.EnableReactiveMethodSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.authentication.AuthenticationWebFilter;
import org.springframework.security.web.server.context.NoOpServerSecurityContextRepository;
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatchers;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import com.erp.cashier.security.JwtAuthenticationManager;
import com.erp.cashier.security.JwtServerAuthenticationConverter;
import com.erp.cashier.security.RestAuthenticationEntryPoint;
import org.springframework.security.web.server.SecurityWebFilterChain;

/**
 * Security configuration for public endpoints.
 *
 * @author ERP Cashier Team
 * @since 2025-01-02
 */
@Configuration
@EnableWebFluxSecurity
@EnableReactiveMethodSecurity
public class SecurityConfig {
    /**
     * Configures the reactive security filter chain.
     *
     * @param http server http security
     * @param authenticationManager authentication manager
     * @param authenticationConverter authentication converter
     * @param authenticationEntryPoint authentication entry point
     * @param agencyActiveWebFilter agency activity filter
     * @return security filter chain
     */
    @Bean
    public SecurityWebFilterChain springSecurityFilterChain(
            ServerHttpSecurity http,
            JwtAuthenticationManager authenticationManager,
            JwtServerAuthenticationConverter authenticationConverter,
            RestAuthenticationEntryPoint authenticationEntryPoint,
            com.erp.cashier.security.AgencyActiveWebFilter agencyActiveWebFilter
    ) {
        AuthenticationWebFilter authenticationFilter = new AuthenticationWebFilter(authenticationManager);
        authenticationFilter.setServerAuthenticationConverter(authenticationConverter);
        authenticationFilter.setSecurityContextRepository(NoOpServerSecurityContextRepository.getInstance());
        authenticationFilter.setRequiresAuthenticationMatcher(ServerWebExchangeMatchers.pathMatchers("/api/**"));

        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .authorizeExchange(exchanges -> exchanges
                        .pathMatchers(
                                "/api/public/**",
                                "/api/auth/**",
                                "/api/notify-unauthorized",
                                "/v3/api-docs/**",
                                "/swagger-ui.html",
                                "/swagger-ui/**",
                                "/webjars/**",
                                "/actuator/**"
                        ).permitAll()
                        .anyExchange().authenticated()
                )
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(authenticationEntryPoint)
                )
                .addFilterAt(authenticationFilter, SecurityWebFiltersOrder.AUTHENTICATION)
                .addFilterAfter(agencyActiveWebFilter, SecurityWebFiltersOrder.AUTHENTICATION)
                .build();
    }
}
