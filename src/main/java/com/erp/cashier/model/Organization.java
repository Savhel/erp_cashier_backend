package com.erp.cashier.model;

import java.time.LocalDateTime;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;
import lombok.Data;

/**
 * Organization entity mapped to the organization table.
 *
 * @author ERP Cashier Team
 * @since 2025-01-02
 */
@Table("organization")
@Data
public class Organization {
    @Id
    private String id;

    @Column("name")
    private String name;

    @Column("country")
    private String country;

    @Column("description")
    private String description;

    @Column("is_active")
    private Boolean isActive;

    @Column("create_on")
    private LocalDateTime createOn;

    @Column("create_by")
    private String createBy;

    @Column("telegram_bot_token")
    private String telegramBotToken;

    /**
     * Default constructor for framework usage.
     */
    public Organization() {
    }
}
