package com.erp.cashier.dto;

import java.math.BigDecimal;
import lombok.Data;

/**
 * Currency denomination response payload.
 *
 * @author ERP Cashier Team
 * @since 2025-01-20
 */
@Data
public class DenominationResponse {
    private String id;
    private String currency;
    private BigDecimal value;
    private String label;
    private Integer order;
    private Boolean isActive;

    /**
     * Default constructor for JSON serialization.
     */
    public DenominationResponse() {
    }

    /**
     * Creates a denomination response.
     *
     * @param id denomination identifier
     * @param currency currency code
     * @param value denomination value
     * @param label denomination label
     * @param order sort order
     * @param isActive active flag
     */
    public DenominationResponse(
            String id,
            String currency,
            BigDecimal value,
            String label,
            Integer order,
            Boolean isActive
    ) {
        this.id = id;
        this.currency = currency;
        this.value = value;
        this.label = label;
        this.order = order;
        this.isActive = isActive;
    }
}
