package com.erp.cashier.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Data;

/**
 * Bill detail payload for cashier.
 *
 * @author ERP Cashier Team
 * @since 2026-02-05
 */
@Data
public class BillDetailResponse {
    private String id;
    private String invoiceCode;
    private BigDecimal amount;
    private String customerName;
    private LocalDateTime dueDate;
    private String paymentMode;
    private List<BillItemResponse> items;
    private BillAccountResponse account;

    /**
     * Default constructor for JSON serialization.
     */
    public BillDetailResponse() {
    }

    public BillDetailResponse(
            String id,
            String invoiceCode,
            BigDecimal amount,
            String customerName,
            LocalDateTime dueDate,
            String paymentMode,
            List<BillItemResponse> items,
            BillAccountResponse account
    ) {
        this.id = id;
        this.invoiceCode = invoiceCode;
        this.amount = amount;
        this.customerName = customerName;
        this.dueDate = dueDate;
        this.paymentMode = paymentMode;
        this.items = items;
        this.account = account;
    }
}
