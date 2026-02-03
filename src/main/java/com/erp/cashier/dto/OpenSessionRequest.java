package com.erp.cashier.dto;

import java.math.BigDecimal;
import lombok.Data;

/**
 * Request payload for opening sessions.
 *
 * @author ERP Cashier Team
 * @since 2025-01-02
 */
@Data
public class OpenSessionRequest {
    private String cashRegisterId;
    private String openBy;
    private BigDecimal theoricalInitialFunds;

    /**
     * Default constructor for JSON serialization.
     */
    public OpenSessionRequest() {
    }
}
