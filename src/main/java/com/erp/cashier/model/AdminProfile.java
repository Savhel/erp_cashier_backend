package com.erp.cashier.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;
import lombok.Data;

/**
 * Admin profile entity mapped to the admin_profile table.
 *
 * @author ERP Cashier Team
 * @since 2025-01-02
 */
@Table("admin_profile")
@Data
public class AdminProfile {
    @Id
    private String id;

    @Column("person_id")
    private String personId;

    @Column("office_adress")
    private String officeAdress;

    @Column("role_type")
    private String roleType;

    @Column("agency_id")
    private String agencyId;

    @Column("organization_id")
    private String organizationId;

    /**
     * Default constructor for framework usage.
     */
    public AdminProfile() {
    }
}
