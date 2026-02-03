package com.erp.cashier.dto;

import lombok.Data;

/**
 * Request payload for register report generation.
 *
 * @author ERP Cashier Team
 * @since 2025-01-20
 */
@Data
public class RegisterReportRequest {
    private String startDate;
    private String endDate;

    /**
     * Default constructor for JSON serialization.
     */
    public RegisterReportRequest() {
    }
}
