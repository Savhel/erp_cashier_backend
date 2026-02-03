package com.erp.cashier.dto;

import lombok.Data;

/**
 * Admin profile payload embedded in admin responses.
 *
 * @author ERP Cashier Team
 * @since 2025-01-02
 */
@Data
public class AdminProfileResponse {
    private String roleType;
    private String organizationId;
    private String agencyId;
    private OrganizationSummaryResponse organization;
    private AgencySummaryResponse agency;

    /**
     * Default constructor for JSON serialization.
     */
    public AdminProfileResponse() {
    }

    /**
     * Creates an admin profile response.
     *
     * @param roleType role type
     * @param organizationId organization identifier
     * @param agencyId agency identifier
     * @param organization organization summary
     * @param agency agency summary
     */
    public AdminProfileResponse(
            String roleType,
            String organizationId,
            String agencyId,
            OrganizationSummaryResponse organization,
            AgencySummaryResponse agency
    ) {
        this.roleType = roleType;
        this.organizationId = organizationId;
        this.agencyId = agencyId;
        this.organization = organization;
        this.agency = agency;
    }
}
