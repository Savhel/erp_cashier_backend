package com.erp.cashier.dto.external;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * Organization member payload from RT_ComOps.
 *
 * @author ERP Cashier Team
 * @since 2026-01-31
 */
@Data
public class RtOrganizationMember {
    @JsonProperty("id")
    private String id;

    @JsonProperty("organizationId")
    private String organizationId;

    @JsonProperty("userId")
    private String userId;

    @JsonProperty("userEmail")
    private String userEmail;

    @JsonProperty("userFirstName")
    private String userFirstName;

    @JsonProperty("userLastName")
    private String userLastName;

    @JsonProperty("agencyId")
    private String agencyId;

    @JsonProperty("agencyName")
    private String agencyName;

    @JsonProperty("roleId")
    private String roleId;

    @JsonProperty("roleName")
    private String roleName;

    @JsonProperty("isActive")
    private Boolean isActive;

    @JsonProperty("joinedAt")
    private String joinedAt;

    /**
     * Default constructor for JSON serialization.
     */
    public RtOrganizationMember() {
    }
}
