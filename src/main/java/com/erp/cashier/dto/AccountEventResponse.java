package com.erp.cashier.dto;

import java.time.LocalDateTime;
import lombok.Data;

/**
 * Account event payload.
 *
 * @author ERP Cashier Team
 * @since 2025-01-20
 */
@Data
public class AccountEventResponse {
    private String id;
    private String type;
    private LocalDateTime dateTime;

    /**
     * Default constructor for JSON serialization.
     */
    public AccountEventResponse() {
    }

    /**
     * Creates an account event response.
     *
     * @param id event identifier
     * @param type event type
     * @param dateTime event timestamp
     */
    public AccountEventResponse(String id, String type, LocalDateTime dateTime) {
        this.id = id;
        this.type = type;
        this.dateTime = dateTime;
    }
}
