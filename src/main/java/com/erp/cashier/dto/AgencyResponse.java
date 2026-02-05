package com.erp.cashier.dto;

import java.time.LocalDateTime;
import java.util.List;
import lombok.Data;

/**
 * Agency response payload for admin endpoints.
 *
 * @author ERP Cashier Team
 * @since 2025-01-02
 */
@Data
public class AgencyResponse {
    private String id;
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
    private LocalDateTime createOn;
    private Boolean hasBlockingSession;
    private List<AgencyCashRegisterResponse> cashRegisters;

    /**
     * Default constructor for JSON serialization.
     */
    public AgencyResponse() {
    }

    /**
     * Creates an agency response.
     *
     * @param id agency identifier
     * @param name agency name
     * @param country agency country
     * @param town agency town
     * @param neighborhood agency neighborhood
     * @param address agency address
     * @param locationHint agency location hint
     * @param isActive active flag
     * @param requiresAdminAssignment assignment requirement
     * @param organizationId organization identifier
     * @param telegramBotToken telegram bot token
     * @param createOn creation timestamp
     * @param hasBlockingSession blocking session flag
     * @param cashRegisters cash register summaries
     */
    public AgencyResponse(
            String id,
            String name,
            String country,
            String town,
            String neighborhood,
            String address,
            String locationHint,
            Boolean isActive,
            Boolean requiresAdminAssignment,
            String organizationId,
            String telegramBotToken,
            LocalDateTime createOn,
            Boolean hasBlockingSession,
            List<AgencyCashRegisterResponse> cashRegisters
    ) {
        this.id = id;
        this.name = name;
        this.country = country;
        this.town = town;
        this.neighborhood = neighborhood;
        this.address = address;
        this.locationHint = locationHint;
        this.isActive = isActive;
        this.requiresAdminAssignment = requiresAdminAssignment;
        this.organizationId = organizationId;
        this.telegramBotToken = telegramBotToken;
        this.createOn = createOn;
        this.hasBlockingSession = hasBlockingSession;
        this.cashRegisters = cashRegisters;
    }
}
