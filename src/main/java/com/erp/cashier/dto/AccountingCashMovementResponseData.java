package com.erp.cashier.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * External accounting response payload data.
 *
 * @author ERP Cashier Team
 * @since 2026-02-05
 */
@Data
public class AccountingCashMovementResponseData {
    @JsonProperty("movement_id")
    private String movementId;
    private String status;
    @JsonProperty("ecriture_id")
    private String ecritureId;
}
