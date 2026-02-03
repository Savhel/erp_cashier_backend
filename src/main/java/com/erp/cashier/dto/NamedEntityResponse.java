package com.erp.cashier.dto;

import lombok.Data;

/**
 * Simple id/name payload for session scope responses.
 *
 * @author ERP Cashier Team
 * @since 2025-01-02
 */
@Data
public class NamedEntityResponse {
    private String id;
    private String name;

    /**
     * Default constructor for JSON serialization.
     */
    public NamedEntityResponse() {
    }

    /**
     * Creates a named entity response.
     *
     * @param id entity identifier
     * @param name entity name
     */
    public NamedEntityResponse(String id, String name) {
        this.id = id;
        this.name = name;
    }
}
