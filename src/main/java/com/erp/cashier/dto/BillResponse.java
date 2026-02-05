package com.erp.cashier.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Data;

/**
 * Bill response payload.
 *
 * @author ERP Cashier Team
 * @since 2025-01-20
 */
@Data
public class BillResponse {
    private String id;
    private String invoiceCode;
    private BigDecimal amount;
    private String customerName;
    private LocalDateTime dueDate;
    private String cashRegisterId;
    private String paymentMode;
    private List<BillItemResponse> items;
    private BillAccountResponse account;

    /**
     * Default constructor for JSON serialization.
     */
    public BillResponse() {
    }

    public BillResponse(
            String id,
            String invoiceCode,
            BigDecimal amount,
            String customerName,
            LocalDateTime dueDate,
            String cashRegisterId,
            String paymentMode,
            List<BillItemResponse> items,
            BillAccountResponse account
    ) {
        this.id = id;
        this.invoiceCode = invoiceCode;
        this.amount = amount;
        this.customerName = customerName;
        this.dueDate = dueDate;
        this.cashRegisterId = cashRegisterId;
        this.paymentMode = paymentMode;
        this.items = items;
        this.account = account;
    }
}
