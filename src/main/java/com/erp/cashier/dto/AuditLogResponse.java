package com.erp.cashier.dto;

import java.time.LocalDateTime;
import lombok.Data;

/**
 * Audit log response payload.
 *
 * @author ERP Cashier Team
 * @since 2025-01-20
 */
@Data
public class AuditLogResponse {
    private String id;
    private String type;
    private LocalDateTime dateTime;
    private String payload;
    private AuditAuthorResponse author;

    /**
     * Default constructor for JSON serialization.
     */
    public AuditLogResponse() {
    }

    /**
     * Creates an audit log response.
     *
     * @param id log identifier
     * @param type log type
     * @param dateTime log timestamp
     * @param payload log payload
     * @param author author payload
     */
    public AuditLogResponse(
            String id,
            String type,
            LocalDateTime dateTime,
            String payload,
            AuditAuthorResponse author
    ) {
        this.id = id;
        this.type = type;
        this.dateTime = dateTime;
        this.payload = payload;
        this.author = author;
    }
}
