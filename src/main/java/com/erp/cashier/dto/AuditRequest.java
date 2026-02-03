package com.erp.cashier.dto;

import java.util.Map;
import lombok.Data;

/**
 * Request payload for audit logging.
 *
 * @author ERP Cashier Team
 * @since 2025-01-20
 */
@Data
public class AuditRequest {
    private String type;
    private String path;
    private String method;
    private String ip;
    private Map<String, Object> payload;

    /**
     * Default constructor for JSON serialization.
     */
    public AuditRequest() {
    }
}
