package com.erp.cashier.dto;

import lombok.Data;

/**
 * Movement party payload.
 *
 * @author ERP Cashier Team
 * @since 2025-01-20
 */
@Data
public class MovementPartyResponse {
    private String id;
    private String name;
    private String username;
    private String role;
    private MovementPersonResponse person;

    /**
     * Default constructor for JSON serialization.
     */
    public MovementPartyResponse() {
    }

    /**
     * Creates a movement party response.
     *
     * @param id party identifier
     * @param name party name
     * @param username party username
     * @param role party role
     */
    public MovementPartyResponse(String id, String name, String username, String role) {
        this.id = id;
        this.name = name;
        this.username = username;
        this.role = role;
    }

    /**
     * Creates a movement party response with person payload.
     *
     * @param id party identifier
     * @param name party name
     * @param username party username
     * @param role party role
     * @param person person payload
     */
    public MovementPartyResponse(
            String id,
            String name,
            String username,
            String role,
            MovementPersonResponse person
    ) {
        this.id = id;
        this.name = name;
        this.username = username;
        this.role = role;
        this.person = person;
    }
}
