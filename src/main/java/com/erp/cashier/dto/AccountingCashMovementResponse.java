package com.erp.cashier.dto;

import lombok.Data;

/**
 * External accounting response payload.
 *
 * @author ERP Cashier Team
 * @since 2026-02-05
 */
@Data
public class AccountingCashMovementResponse {
    private Boolean success;
    private String message;
    private AccountingCashMovementResponseData data;
    private String timestamp;
    private String error;

    public static AccountingCashMovementResponse failure(String message) {
        AccountingCashMovementResponse response = new AccountingCashMovementResponse();
        response.setSuccess(false);
        response.setMessage(message);
        return response;
    }
}
