package com.erp.cashier.model;

import java.time.LocalDateTime;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;
import lombok.Data;

/**
 * Cashier agency assignment entity mapped to the cashier_agency_assignment table.
 *
 * @author ERP Cashier Team
 * @since 2025-01-02
 */
@Table("cashier_agency_assignment")
@Data
public class CashierAgencyAssignment {
    @Id
    private String id;

    @Column("cashier_id")
    private String cashierId;

    @Column("agency_id")
    private String agencyId;

    @Column("start_on")
    private LocalDateTime startOn;

    @Column("end_on")
    private LocalDateTime endOn;

    @Column("assigned_on")
    private LocalDateTime assignedOn;

    @Column("assigned_by")
    private String assignedBy;

    /**
     * Default constructor for framework usage.
     */
    public CashierAgencyAssignment() {
    }
}
