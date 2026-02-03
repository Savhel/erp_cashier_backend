package com.erp.cashier.dto.external;

import lombok.Data;

/**
 * Auth response payload from RT_ComOps.
 *
 * @author ERP Cashier Team
 * @since 2026-01-30
 */
@Data
public class RtAuthResponse {
    private String token;
    private RtUserResponse user;

    /**
     * Default constructor for JSON serialization.
     */
    public RtAuthResponse() {
    }
}
