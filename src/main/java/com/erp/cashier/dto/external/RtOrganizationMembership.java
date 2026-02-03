package com.erp.cashier.dto.external;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * Organization membership payload from RT_ComOps.
 *
 * @author ERP Cashier Team
 * @since 2026-01-30
 */
@Data
public class RtOrganizationMembership {
    @JsonProperty("organizationId")
    private String organizationId;

    @JsonProperty("organizationName")
    private String organizationName;

    @JsonProperty("roleId")
    private String roleId;

    @JsonProperty("roleName")
    private String roleName;

    @JsonProperty("agencyId")
    private String agencyId;

    @JsonProperty("agencyName")
    private String agencyName;

    @JsonProperty("isActive")
    private Boolean isActive;

    @JsonProperty("joinedAt")
    private String joinedAt;

    /**
     * Default constructor for JSON serialization.
     */
    public RtOrganizationMembership() {
    }
}
