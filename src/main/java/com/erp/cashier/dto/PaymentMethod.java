package com.erp.cashier.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Supported payment methods for movements.
 *
 * @author ERP Cashier Team
 * @since 2026-02-04
 */
public enum PaymentMethod {
    OM,
    MOMO,
    CASH,
    CHEQUE;

    @JsonCreator
    public static PaymentMethod fromValue(String value) {
        if (value == null) {
            return null;
        }
        return PaymentMethod.valueOf(value.trim().toUpperCase());
    }

    @JsonValue
    public String toJson() {
        return name();
    }
}
