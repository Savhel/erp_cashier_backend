package com.erp.cashier.model;

import java.math.BigDecimal;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;
import lombok.Data;

/**
 * Currency denomination entity mapped to the currency_denomination table.
 *
 * @author ERP Cashier Team
 * @since 2025-01-02
 */
@Table("currency_denomination")
@Data
public class CurrencyDenomination {
    @Id
    private String id;

    @Column("currency")
    private String currency;

    @Column("value")
    private BigDecimal value;

    @Column("label")
    private String label;

    @Column("sort_order")
    private Integer sortOrder;

    @Column("is_active")
    private Boolean isActive;

    /**
     * Default constructor for framework usage.
     */
    public CurrencyDenomination() {
    }
}
