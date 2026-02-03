package com.erp.cashier.model;

import java.time.LocalDateTime;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;
import lombok.Data;

/**
 * Cash register event entity mapped to the cash_register_event table.
 *
 * @author ERP Cashier Team
 * @since 2025-01-02
 */
@Table("cash_register_event")
@Data
public class CashRegisterEvent {
    @Id
    private String id;

    @Column("session_id")
    private String sessionId;

    @Column("account_id")
    private String accountId;

    @Column("type")
    private String type;

    @Column("idempotency")
    private String idempotency;

    @Column("date_time")
    private LocalDateTime dateTime;

    @Column("author_id")
    private String authorId;

    @Column("payload")
    private String payload;

    @Column("subject_type")
    private String subjectType;

    @Column("subject_id")
    private String subjectId;

    @Column("hash")
    private String hash;

    @Column("previous_hash")
    private String previousHash;

    /**
     * Default constructor for framework usage.
     */
    public CashRegisterEvent() {
    }
}
