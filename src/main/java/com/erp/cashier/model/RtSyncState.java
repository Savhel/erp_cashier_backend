package com.erp.cashier.model;

import java.time.LocalDateTime;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * Synchronization state for RT_ComOps data.
 *
 * @author ERP Cashier Team
 * @since 2026-02-01
 */
@Table("rt_sync_state")
@Data
public class RtSyncState {
    @Id
    @Column("organization_id")
    private String organizationId;

    @Column("last_synced_at")
    private LocalDateTime lastSyncedAt;

    /**
     * Default constructor for framework usage.
     */
    public RtSyncState() {
    }
}
