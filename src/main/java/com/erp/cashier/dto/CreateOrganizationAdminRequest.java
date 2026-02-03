package com.erp.cashier.dto;

import lombok.Data;

/**
 * Request payload for creating an organization admin.
 *
 * @author ERP Cashier Team
 * @since 2025-01-02
 */
@Data
public class CreateOrganizationAdminRequest {
    private String organizationId;
    private String personId;
    private String phone;
    private String agencyId;

    /**
     * Default constructor for JSON serialization.
     */
    public CreateOrganizationAdminRequest() {
    }
}
