package com.erp.cashier.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * Account event response for admin endpoints.
 *
 * @author ERP Cashier Team
 * @since 2026-02-03
 */
@Data
public class AdminAccountEventResponse {
    @JsonProperty("id")
    private String id;

    @JsonProperty("type")
    private String type;

    @JsonProperty("date_time")
    private LocalDateTime dateTime;

    @JsonProperty("payload")
    private String payload;

    /**
     * Default constructor for JSON serialization.
     */
    public AdminAccountEventResponse() {
    }

    /**
     * Creates an admin account event response.
     *
     * @param id event identifier
     * @param type event type
     * @param dateTime event timestamp
     * @param payload event payload
     */
    public AdminAccountEventResponse(String id, String type, LocalDateTime dateTime, String payload) {
        this.id = id;
        this.type = type;
        this.dateTime = dateTime;
        this.payload = payload;
    }
}
