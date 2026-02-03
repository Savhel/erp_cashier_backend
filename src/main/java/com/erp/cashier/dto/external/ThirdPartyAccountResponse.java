package com.erp.cashier.dto.external;

import lombok.Data;

/**
 * Third-party account payload.
 *
 * @author ERP Cashier Team
 * @since 2026-02-03
 */
@Data
public class ThirdPartyAccountResponse {
    private String id;
    private String tenantId;
    private String code;
    private String name;
    private String accountingAccount;
    private String bankAccountNumber;
    private String kind;

    /**
     * Default constructor for JSON serialization.
     */
    public ThirdPartyAccountResponse() {
    }
}
