package com.erp.cashier.dto;

import java.math.BigDecimal;
import java.util.Map;
import lombok.Data;

/**
 * Request payload for assigning cashiers to registers.
 *
 * @author ERP Cashier Team
 * @since 2025-01-02
 */
@Data
public class AssignCashierRequest {
    private String cashierId;
    private InitialFunds initialFunds;

    /**
     * Default constructor for JSON serialization.
     */
    public AssignCashierRequest() {
    }

    /**
     * Initial funds payload with billetage details.
     */
    @Data
    public static class InitialFunds {
        private BigDecimal total;
        private Map<String, Integer> denominations;

        /**
         * Default constructor for JSON serialization.
         */
        public InitialFunds() {
        }
    }
}
