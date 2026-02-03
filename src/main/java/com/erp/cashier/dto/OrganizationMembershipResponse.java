package com.erp.cashier.dto;

import lombok.Data;

/**
 * Organization membership payload from RT_ComOps.
 *
 * @author ERP Cashier Team
 * @since 2026-01-30
 */
@Data
public class OrganizationMembershipResponse {
    private String organizationId;
    private String organizationName;
    private String roleId;
    private String roleName;
    private String agencyId;
    private String agencyName;
    private String accessToken;
    private Boolean isActive;
    private String joinedAt;

    /**
     * Default constructor for JSON serialization.
     */
    public OrganizationMembershipResponse() {
    }
}
