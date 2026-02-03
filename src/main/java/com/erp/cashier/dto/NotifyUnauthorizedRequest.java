package com.erp.cashier.dto;

import java.util.Map;
import lombok.Data;

/**
 * Request payload for unauthorized access notifications.
 *
 * @author ERP Cashier Team
 * @since 2025-01-20
 */
@Data
public class NotifyUnauthorizedRequest {
    private String path;
    private String method;
    private String ip;
    private Map<String, Object> payload;
    private String username;
    private String userId;
    private String agencyId;
    private String organizationId;
    private String userAgent;
    private String macAddress;

    /**
     * Default constructor for JSON serialization.
     */
    public NotifyUnauthorizedRequest() {
    }
}
