package com.erp.cashier.dto;

import lombok.Data;

/**
 * Request payload for updating an admin.
 *
 * @author ERP Cashier Team
 * @since 2025-01-02
 */
@Data
public class UpdateOrganizationAdminRequest {
    private String userName;
    private String userFirstName;
    private String mail;
    private String accountNumber;
    private String roleType;
    private String agencyId;
    private String organizationId;
    private String organizationBotToken;
    private String country;
    private String phone;
    private String telegramChatId;
    private Boolean actif;

    /**
     * Default constructor for JSON serialization.
     */
    public UpdateOrganizationAdminRequest() {
    }
}
