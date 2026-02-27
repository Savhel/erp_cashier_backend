package com.erp.cashier.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * Request payload used by agency admins to provision a cashier fund request.
 */
@Data
public class FundRequestProvisionRequest {
    private TicketingRequest ticketing;
    private String reference;

    @JsonProperty("source_mac_address")
    private String sourceMacAddress;

    /**
     * Default constructor for JSON serialization.
     */
    public FundRequestProvisionRequest() {
    }
}
