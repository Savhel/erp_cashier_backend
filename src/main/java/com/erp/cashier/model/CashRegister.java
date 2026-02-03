package com.erp.cashier.model;

import java.time.LocalDateTime;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;
import lombok.Data;

/**
 * Cash register entity mapped to the cash_register table.
 *
 * @author ERP Cashier Team
 * @since 2025-01-02
 */
@Table("cash_register")
@Data
public class CashRegister {
    @Id
    private String id;

    @Column("cashier")
    private String cashier;

    @Column("user_id")
    private String userId;

    @Column("agency_id")
    private String agencyId;

    @Column("is_active")
    private Boolean isActive;

    @Column("double_closing_count")
    private Boolean doubleClosingCount;

    @Column("justifiable_threshold")
    private Double justifiableThreshold;

    @Column("create_on")
    private LocalDateTime createOn;

    @Column("create_by")
    private String createBy;

    @Column("adress")
    private String adress;

    @Column("country")
    private String country;

    @Column("town")
    private String town;

    @Column("neighborhood")
    private String neighborhood;

    @Column("ip_address")
    private String ipAddress;

    @Column("mac_address")
    private String macAddress;

    @Column("image_url")
    private String imageUrl;

    @Column("min_open_time")
    private String minOpenTime;

    @Column("max_close_time")
    private String maxCloseTime;

    @Column("sale_agent_bank_account")
    private String saleAgentBankAccount;

    @Column("sale_agent_accounting_account")
    private String saleAgentAccountingAccount;

    /**
     * Default constructor for framework usage.
     */
    public CashRegister() {
    }
}
