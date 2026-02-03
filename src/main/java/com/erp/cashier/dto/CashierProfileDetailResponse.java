package com.erp.cashier.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * Cashier profile payload with base agency details.
 *
 * @author ERP Cashier Team
 * @since 2026-02-01
 */
@Data
public class CashierProfileDetailResponse {
    private String townListChosen;
    private String workTown;
    private LocalDateTime hireDate;
    private String baseAgencyId;
    private String organizationId;

    @JsonProperty("baseAgency")
    private BaseAgencyResponse baseAgency;

    /**
     * Default constructor for JSON serialization.
     */
    public CashierProfileDetailResponse() {
    }

    /**
     * Creates a cashier profile detail response.
     *
     * @param townListChosen town list payload
     * @param workTown work town
     * @param hireDate hire date
     * @param baseAgencyId base agency identifier
     * @param organizationId organization identifier
     * @param baseAgency base agency payload
     */
    public CashierProfileDetailResponse(
            String townListChosen,
            String workTown,
            LocalDateTime hireDate,
            String baseAgencyId,
            String organizationId,
            BaseAgencyResponse baseAgency
    ) {
        this.townListChosen = townListChosen;
        this.workTown = workTown;
        this.hireDate = hireDate;
        this.baseAgencyId = baseAgencyId;
        this.organizationId = organizationId;
        this.baseAgency = baseAgency;
    }
}
