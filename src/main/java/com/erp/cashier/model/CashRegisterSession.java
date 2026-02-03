package com.erp.cashier.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;
import lombok.Data;

/**
 * Cash register session entity mapped to the cash_register_session table.
 *
 * @author ERP Cashier Team
 * @since 2025-01-02
 */
@Table("cash_register_session")
@Data
public class CashRegisterSession {
    @Id
    private String id;

    @Column("open_by")
    private String openBy;

    @Column("state")
    private String state;

    @Column("cash_register_id")
    private String cashRegisterId;

    @Column("open_on")
    private LocalDateTime openOn;

    @Column("close_on")
    private LocalDateTime closeOn;

    @Column("close_by")
    private String closeBy;

    @Column("theorical_initial_funds")
    private BigDecimal theoricalInitialFunds;

    @Column("theorical_close_funds")
    private BigDecimal theoricalCloseFunds;

    @Column("previous_event_hash")
    private String previousEventHash;

    @Column("is_locked")
    private Boolean isLocked;

    /**
     * Default constructor for framework usage.
     */
    public CashRegisterSession() {
    }
}
