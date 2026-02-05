package com.erp.cashier.service;

import org.springframework.util.StringUtils;

/**
 * Normalizes admin role types based on agency scope.
 *
 * @author ERP Cashier Team
 * @since 2026-02-04
 */
public final class AdminRoleResolver {
    private static final String ROLE_SUPERADMIN = "superadmin";
    private static final String ROLE_ORGANIZATION_ADMIN = "organization_admin";
    private static final String ROLE_AGENCY_ADMIN = "agency_admin";

    private AdminRoleResolver() {
    }

    /**
     * Ensures role type matches the rule: agency admins have agency_id set, organization admins do not.
     *
     * @param roleType requested role type
     * @param agencyId agency identifier (nullable)
     * @return normalized role type
     */
    public static String normalizeRoleType(String roleType, String agencyId) {
        if (StringUtils.hasText(roleType) && ROLE_SUPERADMIN.equalsIgnoreCase(roleType.trim())) {
            return ROLE_SUPERADMIN;
        }
        if (StringUtils.hasText(agencyId)) {
            return ROLE_AGENCY_ADMIN;
        }
        return ROLE_ORGANIZATION_ADMIN;
    }
}
