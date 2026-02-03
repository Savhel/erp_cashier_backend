package com.erp.cashier.controller;

import com.erp.cashier.dto.AuthSessionResponse;
import com.erp.cashier.dto.LoginRequest;
import com.erp.cashier.dto.LoginResponse;
import com.erp.cashier.dto.SuccessResponse;
import com.erp.cashier.security.JwtPayload;
import com.erp.cashier.service.AuthService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.GetMapping;
import reactor.core.publisher.Mono;

/**
 * Authentication endpoints for the BFF layer.
 *
 * @author ERP Cashier Team
 * @since 2025-01-02
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService authService;

    /**
     * Creates the authentication controller.
     *
     * @param authService authentication service
     */
    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * Authenticates a user and returns session data.
     *
     * @param request login request
     * @return login response
     */
    @PostMapping("/login")
    public Mono<LoginResponse> login(@RequestBody LoginRequest request) {
        return authService.login(request);
    }

    /**
     * Logs out the current user.
     *
     * @return logout response
     */
    @PostMapping("/logout")
    public Mono<SuccessResponse> logout() {
        return Mono.just(new SuccessResponse(true));
    }

    /**
     * Returns the current authenticated session.
     *
     * @param authentication authentication payload
     * @return session response
     */
    @GetMapping("/session")
    @PreAuthorize("isAuthenticated()")
    public Mono<AuthSessionResponse> session(Authentication authentication) {
        JwtPayload payload = resolvePayload(authentication);
        return authService.getSession(payload);
    }

    private JwtPayload resolvePayload(Authentication authentication) {
        if (authentication == null) {
            return null;
        }
        Object details = authentication.getDetails();
        if (details instanceof JwtPayload payload) {
            return payload;
        }
        return null;
    }

}
