package com.erp.cashier.model;

import java.time.LocalDateTime;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;
import lombok.Data;

/**
 * Customer profile entity mapped to the customer_profile table.
 *
 * @author ERP Cashier Team
 * @since 2025-01-02
 */
@Table("customer_profile")
@Data
public class CustomerProfile {
    @Id
    private String id;

    @Column("person_id")
    private String personId;

    @Column("profession")
    private String profession;

    @Column("date_of_joining")
    private LocalDateTime dateOfJoining;

    /**
     * Default constructor for framework usage.
     */
    public CustomerProfile() {
    }
}
