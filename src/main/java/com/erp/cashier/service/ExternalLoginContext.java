package com.erp.cashier.service;

import lombok.Data;

/**
 * Context payload for external platform login validation.
 *
 * @author ERP Cashier Team
 * @since 2025-01-28
 */
@Data
public class ExternalLoginContext {
    private String userId;
    private String username;
    private String password;
    private String role;
    private String roleType;
    private String organizationId;
    private String agencyId;
}
