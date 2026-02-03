package com.erp.cashier.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;
import lombok.Data;

/**
 * Cash reconciliation entity mapped to the cash_reconciliation table.
 *
 * @author ERP Cashier Team
 * @since 2025-01-02
 */
@Table("cash_reconciliation")
@Data
public class CashReconciliation {
    @Id
    private String id;

    @Column("session_id")
    private String sessionId;

    @Column("physical_total")
    private BigDecimal physicalTotal;

    @Column("theorical_total")
    private BigDecimal theoricalTotal;

    @Column("difference")
    private BigDecimal difference;

    @Column("statut")
    private String statut;

    @Column("justification")
    private String justification;

    @Column("create_on")
    private LocalDateTime createOn;

    @Column("create_by")
    private String createBy;

    @Column("check_on")
    private LocalDateTime checkOn;

    @Column("check_by")
    private String checkBy;

    /**
     * Default constructor for framework usage.
     */
    public CashReconciliation() {
    }
}
