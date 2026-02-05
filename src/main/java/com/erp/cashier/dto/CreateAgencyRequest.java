package com.erp.cashier.dto;

import lombok.Data;

/**
 * Request payload for creating an agency.
 *
 * @author ERP Cashier Team
 * @since 2025-01-02
 */
@Data
public class CreateAgencyRequest {
    private String name;
    private String country;
    private String town;
    private String neighborhood;
    private String address;
    private String locationHint;
    private Boolean isActive;
    private Boolean requiresAdminAssignment;
    private String organizationId;
    private String telegramBotToken;

    /**
     * Default constructor for JSON serialization.
     */
    public CreateAgencyRequest() {
    }
}
