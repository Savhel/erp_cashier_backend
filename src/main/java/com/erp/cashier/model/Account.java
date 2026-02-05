package com.erp.cashier.model;

import java.time.LocalDateTime;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;
import lombok.Data;

/**
 * Account entity mapped to the account table.
 *
 * @author ERP Cashier Team
 * @since 2025-01-02
 */
@Table("account")
@Data
public class Account {
    @Id
    private String id;

    @Column("client_id")
    private String clientId;

    @Column("account_number")
    private String accountNumber;

    @Column("accounting_account")
    private String accountingAccount;

    @Column("is_active")
    private Boolean isActive;

    @Column("create_on")
    private LocalDateTime createOn;

    @Column("create_by")
    private String createBy;

    @Column("organization_id")
    private String organizationId;

    @Column("previous_event_hash")
    private String previousEventHash;

    @Column("total_funds")
    private Double totalFunds;

    /**
     * Default constructor for framework usage.
     */
    public Account() {
    }
}
