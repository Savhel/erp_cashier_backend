package com.erp.cashier.dto;

import java.math.BigDecimal;
import lombok.Data;

/**
 * Request payload for closing a session.
 *
 * @author ERP Cashier Team
 * @since 2025-01-20
 */
@Data
public class CloseSessionRequest {
    private BigDecimal physicalTotal;

    /**
     * Default constructor for JSON serialization.
     */
    public CloseSessionRequest() {
    }
}
