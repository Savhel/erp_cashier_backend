package com.erp.cashier.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * Cash register movement entity mapped to the cash_register_movement table.
 *
 * @author ERP Cashier Team
 * @since 2025-01-02
 */
@Table("cash_register_movement")
@Data
public class CashRegisterMovement implements Persistable<String> {
    @Id
    private String id;

    @Transient
    private boolean isNew;

    @Column("session_id")
    private String sessionId;

    @Column("sense")
    private String sense;

    @Column("amount")
    private BigDecimal amount;

    @Column("reason")
    private String reason;

    @Column("reason_detail")
    private String reasonDetail;

    @Column("recipient_id")
    private String recipientId;

    @Column("emitter_id")
    private String emitterId;

    @Column("is_accounted")
    private Boolean isAccounted;

    @Column("event_ticketing_details")
    private Boolean eventTicketingDetails;

    @Column("external_reference")
    private String externalReference;

    @Column("payment_method")
    private String paymentMethod;

    @Column("create_on")
    private LocalDateTime createOn;

    @Column("create_by")
    private String createBy;

    @Column("is_deleted")
    private Boolean isDeleted;

    /**
     * Default constructor for framework usage.
     */
    public CashRegisterMovement() {
    }

    @Override
    public boolean isNew() {
        return isNew;
    }

    public void markNew() {
        this.isNew = true;
    }
}
