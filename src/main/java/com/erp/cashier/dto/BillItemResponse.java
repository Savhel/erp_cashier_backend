package com.erp.cashier.dto;

import java.math.BigDecimal;
import lombok.Data;

/**
 * Bill item payload.
 *
 * @author ERP Cashier Team
 * @since 2026-02-05
 */
@Data
public class BillItemResponse {
    private String description;
    private Integer quantity;
    private BigDecimal amount;

    /**
     * Default constructor for JSON serialization.
     */
    public BillItemResponse() {
    }

    public BillItemResponse(String description, Integer quantity, BigDecimal amount) {
        this.description = description;
        this.quantity = quantity;
        this.amount = amount;
    }
}
