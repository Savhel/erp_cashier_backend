package com.erp.cashier.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * Bill list payload for cashier.
 *
 * @author ERP Cashier Team
 * @since 2026-02-05
 */
@Data
public class BillListResponse {
    private String id;
    private String invoiceCode;
    private BigDecimal amount;
    private String customerName;
    private LocalDateTime dueDate;
    private String paymentMode;
    private BillListAccountResponse account;

    /**
     * Default constructor for JSON serialization.
     */
    public BillListResponse() {
    }

    public BillListResponse(
            String id,
            String invoiceCode,
            BigDecimal amount,
            String customerName,
            LocalDateTime dueDate,
            String paymentMode,
            BillListAccountResponse account
    ) {
        this.id = id;
        this.invoiceCode = invoiceCode;
        this.amount = amount;
        this.customerName = customerName;
        this.dueDate = dueDate;
        this.paymentMode = paymentMode;
        this.account = account;
    }
}
