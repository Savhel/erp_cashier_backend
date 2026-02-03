package com.erp.cashier.dto;

import lombok.Data;

/**
 * Organization creator payload embedded in organization responses.
 *
 * @author ERP Cashier Team
 * @since 2025-01-02
 */
@Data
public class OrganizationCreatorResponse {
    private String id;
    private String userName;
    private String userFirstName;

    /**
     * Default constructor for JSON serialization.
     */
    public OrganizationCreatorResponse() {
    }

    /**
     * Creates an organization creator response.
     *
     * @param id creator identifier
     * @param userName creator username
     * @param userFirstName creator first name
     */
    public OrganizationCreatorResponse(String id, String userName, String userFirstName) {
        this.id = id;
        this.userName = userName;
        this.userFirstName = userFirstName;
    }
}
