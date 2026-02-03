package com.erp.cashier.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.util.List;
import lombok.Data;

/**
 * Dashboard stats payload.
 *
 * @author ERP Cashier Team
 * @since 2025-01-20
 */
@Data
public class DashboardStatsResponse {
    @JsonProperty("role")
    private String role;

    @JsonProperty("totalRevenue")
    private BigDecimal totalRevenue;

    @JsonProperty("activeSessions")
    private long activeSessions;

    @JsonProperty("todayMovements")
    private long todayMovements;

    @JsonProperty("todayTotal")
    private BigDecimal todayTotal;

    @JsonProperty("monthlyRevenue")
    private List<DashboardMetricPoint> monthlyRevenue;

    @JsonProperty("dailyRevenue")
    private List<DashboardMetricPoint> dailyRevenue;

    @JsonProperty("hourlyRevenue")
    private List<DashboardMetricPoint> hourlyRevenue;

    /**
     * Default constructor for JSON serialization.
     */
    public DashboardStatsResponse() {
    }

    /**
     * Creates a dashboard stats response.
     *
     * @param totalRevenue total revenue
     * @param activeSessions active sessions
     * @param todayMovements today movements
     * @param todayTotal today total
     * @param role role
     * @param monthlyRevenue monthly revenue
     * @param dailyRevenue daily revenue series
     * @param hourlyRevenue hourly revenue series
     */
    public DashboardStatsResponse(
            BigDecimal totalRevenue,
            long activeSessions,
            long todayMovements,
            BigDecimal todayTotal,
            String role,
            List<DashboardMetricPoint> monthlyRevenue,
            List<DashboardMetricPoint> dailyRevenue,
            List<DashboardMetricPoint> hourlyRevenue
    ) {
        this.totalRevenue = totalRevenue;
        this.activeSessions = activeSessions;
        this.todayMovements = todayMovements;
        this.todayTotal = todayTotal;
        this.role = role;
        this.monthlyRevenue = monthlyRevenue;
        this.dailyRevenue = dailyRevenue;
        this.hourlyRevenue = hourlyRevenue;
    }
}
