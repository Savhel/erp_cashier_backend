package com.erp.cashier.model;

import java.time.LocalDateTime;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;
import lombok.Data;

/**
 * Agency entity mapped to the agency table.
 *
 * @author ERP Cashier Team
 * @since 2025-01-02
 */
@Table("agency")
@Data
public class Agency {
    @Id
    private String id;

    @Column("name")
    private String name;

    @Column("country")
    private String country;

    @Column("town")
    private String town;

    @Column("neighborhood")
    private String neighborhood;

    @Column("address")
    private String address;

    @Column("location_hint")
    private String locationHint;

    @Column("is_active")
    private Boolean isActive;

    @Column("requires_admin_assignment")
    private Boolean requiresAdminAssignment;

    @Column("organization_id")
    private String organizationId;

    @Column("create_on")
    private LocalDateTime createOn;

    /**
     * Default constructor for framework usage.
     */
    public Agency() {
    }
}
