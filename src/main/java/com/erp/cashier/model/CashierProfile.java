package com.erp.cashier.model;

import java.time.LocalDateTime;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;
import lombok.Data;

/**
 * Cashier profile entity mapped to the cashier_profile table.
 *
 * @author ERP Cashier Team
 * @since 2025-01-02
 */
@Table("cashier_profile")
@Data
public class CashierProfile {
    @Id
    private String id;

    @Column("person_id")
    private String personId;

    @Column("town_list_chosen")
    private String townListChosen;

    @Column("work_town")
    private String workTown;

    @Column("hire_date")
    private LocalDateTime hireDate;

    @Column("base_agency_id")
    private String baseAgencyId;

    @Column("is_active")
    private Boolean isActive;

    /**
     * Default constructor for framework usage.
     */
    public CashierProfile() {
    }
}
