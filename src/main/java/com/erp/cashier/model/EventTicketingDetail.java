package com.erp.cashier.model;

import java.math.BigDecimal;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;
import lombok.Data;

/**
 * Event ticketing detail entity mapped to the event_ticketing_detail table.
 *
 * @author ERP Cashier Team
 * @since 2025-01-02
 */
@Table("event_ticketing_detail")
@Data
public class EventTicketingDetail {
    @Id
    private String id;

    @Column("session_id")
    private String sessionId;

    @Column("connection_type")
    private String connectionType;

    @Column("quantity")
    private Integer quantity;

    @Column("value")
    private BigDecimal value;

    @Column("total")
    private BigDecimal total;

    @Column("denomination_id")
    private String denominationId;

    @Column("movement_id")
    private String movementId;

    /**
     * Default constructor for framework usage.
     */
    public EventTicketingDetail() {
    }
}
