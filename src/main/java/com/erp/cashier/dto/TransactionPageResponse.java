package com.erp.cashier.dto;

import java.util.List;
import lombok.Data;

/**
 * Transaction page response payload.
 *
 * @author ERP Cashier Team
 * @since 2025-01-20
 */
@Data
public class TransactionPageResponse {
    private List<MovementResponse> movements;
    private long total;
    private int page;
    private int totalPages;

    /**
     * Default constructor for JSON serialization.
     */
    public TransactionPageResponse() {
    }

    /**
     * Creates a transaction page response.
     *
     * @param movements movements
     * @param total total count
     * @param page page number
     * @param totalPages total pages
     */
    public TransactionPageResponse(
            List<MovementResponse> movements,
            long total,
            int page,
            int totalPages
    ) {
        this.movements = movements;
        this.total = total;
        this.page = page;
        this.totalPages = totalPages;
    }
}
