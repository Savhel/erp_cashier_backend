package com.erp.cashier.dto.external;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.Data;

/**
 * User payload from RT_ComOps.
 *
 * @author ERP Cashier Team
 * @since 2026-01-30
 */
@Data
public class RtUserResponse {
    @JsonProperty("id")
    private String id;

    @JsonProperty("email")
    private String email;

    @JsonProperty("firstName")
    private String firstName;

    @JsonProperty("lastName")
    private String lastName;

    @JsonProperty("businessActorId")
    private String businessActorId;

    @JsonProperty("isActive")
    private Boolean isActive;

    @JsonProperty("roles")
    private List<String> roles;

    /**
     * Default constructor for JSON serialization.
     */
    public RtUserResponse() {
    }
}
