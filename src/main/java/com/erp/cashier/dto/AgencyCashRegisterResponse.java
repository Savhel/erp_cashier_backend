package com.erp.cashier.dto;

import java.util.List;
import lombok.Data;

/**
 * Cash register summary for agency listings.
 *
 * @author ERP Cashier Team
 * @since 2026-02-02
 */
@Data
public class AgencyCashRegisterResponse {
    private String id;
    private List<AgencyCashRegisterSessionResponse> sessions;

    /**
     * Default constructor for JSON serialization.
     */
    public AgencyCashRegisterResponse() {
    }

    /**
     * Creates a cash register response.
     *
     * @param id register identifier
     * @param sessions session summaries
     */
    public AgencyCashRegisterResponse(String id, List<AgencyCashRegisterSessionResponse> sessions) {
        this.id = id;
        this.sessions = sessions;
    }
}
