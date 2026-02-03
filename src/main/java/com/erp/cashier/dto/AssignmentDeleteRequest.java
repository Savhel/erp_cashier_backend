package com.erp.cashier.dto;

import lombok.Data;

/**
 * Request payload for assignment deletion.
 *
 * @author ERP Cashier Team
 * @since 2025-01-20
 */
@Data
public class AssignmentDeleteRequest {
    private String id;

    /**
     * Default constructor for JSON serialization.
     */
    public AssignmentDeleteRequest() {
    }
}
