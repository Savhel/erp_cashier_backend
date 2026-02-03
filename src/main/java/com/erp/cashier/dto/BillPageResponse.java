package com.erp.cashier.dto;

import java.util.List;
import lombok.Data;

/**
 * Bill page response payload.
 *
 * @author ERP Cashier Team
 * @since 2025-01-20
 */
@Data
public class BillPageResponse {
    private List<BillResponse> bills;
    private long total;
    private int page;
    private int totalPages;

    /**
     * Default constructor for JSON serialization.
     */
    public BillPageResponse() {
    }

    public BillPageResponse(List<BillResponse> bills, long total, int page, int totalPages) {
        this.bills = bills;
        this.total = total;
        this.page = page;
        this.totalPages = totalPages;
    }
}
