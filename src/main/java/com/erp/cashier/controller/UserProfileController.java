package com.erp.cashier.controller;

import com.erp.cashier.dto.UpdateUserProfileRequest;
import com.erp.cashier.dto.UserProfileResponse;
import com.erp.cashier.security.JwtPayload;
import com.erp.cashier.service.UserProfileService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * Profile endpoints for the authenticated user.
 *
 * @author ERP Cashier Team
 * @since 2025-01-20
 */
@RestController
@RequestMapping("/api/users/profile")
public class UserProfileController {
    private final UserProfileService userProfileService;

    /**
     * Creates the user profile controller.
     *
     * @param userProfileService user profile service
     */
    public UserProfileController(UserProfileService userProfileService) {
        this.userProfileService = userProfileService;
    }

    /**
     * Returns the current user profile.
     *
     * @param authentication authentication payload
     * @return user profile
     */
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public Mono<UserProfileResponse> getProfile(Authentication authentication) {
        return userProfileService.getProfile(resolvePayload(authentication));
    }

    /**
     * Updates the current user profile.
     *
     * @param request update request
     * @param authentication authentication payload
     * @return updated profile
     */
    @PutMapping
    @PreAuthorize("isAuthenticated()")
    public Mono<UserProfileResponse> updateProfile(
            @RequestBody UpdateUserProfileRequest request,
            Authentication authentication
    ) {
        return userProfileService.updateProfile(resolvePayload(authentication), request);
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
