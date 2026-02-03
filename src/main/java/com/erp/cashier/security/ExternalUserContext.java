package com.erp.cashier.security;

import java.time.Instant;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * External token context resolved from RT_ComOps login.
 *
 * @author ERP Cashier Team
 * @since 2026-01-30
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ExternalUserContext {
    private String userId;
    private String username;
    private List<String> roles;
    private String organizationId;
    private String agencyId;
    private Instant expiresAt;
}
