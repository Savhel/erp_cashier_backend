package com.erp.cashier.dto;

import lombok.Data;

/**
 * Admin profile summary for audit author.
 *
 * @author ERP Cashier Team
 * @since 2026-02-03
 */
@Data
public class AuditAuthorProfileResponse {
    private String roleType;
    private String agencyId;

    /**
     * Default constructor for JSON serialization.
     */
    public AuditAuthorProfileResponse() {
    }

    /**
     * Creates an admin profile summary.
     *
     * @param roleType role type
     * @param agencyId agency identifier
     */
    public AuditAuthorProfileResponse(String roleType, String agencyId) {
        this.roleType = roleType;
        this.agencyId = agencyId;
    }
}
