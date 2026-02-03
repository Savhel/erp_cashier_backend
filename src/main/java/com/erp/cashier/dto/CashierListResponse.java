package com.erp.cashier.dto;

import lombok.Data;
import org.springframework.util.StringUtils;

/**
 * Minimal cashier response payload for list endpoints.
 *
 * @author ERP Cashier Team
 * @since 2026-02-02
 */
@Data
public class CashierListResponse {
    private String id;
    private String userName;
    private String userFirstName;
    private CashierListProfileResponse cashierProfile;

    /**
     * Default constructor for JSON serialization.
     */
    public CashierListResponse() {
    }

    /**
     * Creates a minimal cashier response.
     *
     * @param id cashier identifier
     * @param userName username
     * @param userFirstName full name
     * @param cashierProfile cashier profile
     */
    public CashierListResponse(
            String id,
            String userName,
            String userFirstName,
            CashierListProfileResponse cashierProfile
    ) {
        this.id = id;
        this.userName = userName;
        this.userFirstName = userFirstName;
        this.cashierProfile = cashierProfile;
    }

    /**
     * Builds a minimal response from a full cashier response.
     *
     * @param response cashier response
     * @return minimal cashier response
     */
    public static CashierListResponse from(CashierResponse response) {
        if (response == null) {
            return null;
        }
        CashierProfileResponse profile = response.getCashierProfile();
        CashierListProfileResponse minimalProfile = null;
        if (profile != null && (StringUtils.hasText(profile.getWorkTown())
                || StringUtils.hasText(profile.getTownListChosen()))) {
            minimalProfile = new CashierListProfileResponse(
                    profile.getWorkTown(),
                    profile.getTownListChosen()
            );
        }
        return new CashierListResponse(
                response.getId(),
                response.getUserName(),
                response.getUserFirstName(),
                minimalProfile
        );
    }
}
