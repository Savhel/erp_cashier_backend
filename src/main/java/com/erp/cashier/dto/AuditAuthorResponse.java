package com.erp.cashier.dto;

import lombok.Data;

/**
 * Audit author payload.
 *
 * @author ERP Cashier Team
 * @since 2025-01-20
 */
@Data
public class AuditAuthorResponse {
    private String userFirstName;
    private String userName;
    private AuditAuthorProfileResponse adminProfile;

    /**
     * Default constructor for JSON serialization.
     */
    public AuditAuthorResponse() {
    }

    /**
     * Creates an audit author payload.
     *
     * @param userFirstName author first name
     * @param userName author username
     * @param adminProfile admin profile summary
     */
    public AuditAuthorResponse(String userFirstName, String userName, AuditAuthorProfileResponse adminProfile) {
        this.userFirstName = userFirstName;
        this.userName = userName;
        this.adminProfile = adminProfile;
    }
}
