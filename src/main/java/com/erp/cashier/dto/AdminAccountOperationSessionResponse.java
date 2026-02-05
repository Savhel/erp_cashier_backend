package com.erp.cashier.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * Session wrapper for admin account operations.
 *
 * @author ERP Cashier Team
 * @since 2026-02-03
 */
@Data
public class AdminAccountOperationSessionResponse {
    @JsonProperty("cashRegister")
    private AdminAccountOperationRegisterResponse cashRegister;

    /**
     * Default constructor for JSON serialization.
     */
    public AdminAccountOperationSessionResponse() {
    }

    /**
     * Creates a session wrapper.
     *
     * @param cashRegister cash register summary
     */
    public AdminAccountOperationSessionResponse(AdminAccountOperationRegisterResponse cashRegister) {
        this.cashRegister = cashRegister;
    }
}
