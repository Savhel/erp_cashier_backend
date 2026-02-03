package com.erp.cashier.model;

import java.time.LocalDateTime;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;
import lombok.Data;

/**
 * Cashier manage cash register entity mapped to the cashier_manage_cash_register table.
 *
 * @author ERP Cashier Team
 * @since 2025-01-02
 */
@Table("cashier_manage_cash_register")
@Data
public class CashierManageCashRegister {
    @Id
    private String id;

    @Column("cash_register_id")
    private String cashRegisterId;

    @Column("user_id")
    private String userId;

    @Column("day")
    private LocalDateTime day;

    /**
     * Default constructor for framework usage.
     */
    public CashierManageCashRegister() {
    }
}
