package com.erp.cashier.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import lombok.Data;

/**
 * Dashboard series point payload.
 *
 * @author ERP Cashier Team
 * @since 2025-01-20
 */
@Data
public class DashboardMetricPoint {
    @JsonProperty("name")
    private String name;

    @JsonProperty("total")
    private BigDecimal total;

    /**
     * Default constructor for JSON serialization.
     */
    public DashboardMetricPoint() {
    }

    /**
     * Creates a dashboard metric point.
     *
     * @param name name
     * @param total total
     */
    public DashboardMetricPoint(String name, BigDecimal total) {
        this.name = name;
        this.total = total;
    }
}
