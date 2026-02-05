package com.erp.cashier.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Data;

/**
 * Fund request response payload.
 */
@Data
public class FundRequestResponse {
    private String id;
    private BigDecimal amount;
    private String reason;
    private String sense;

    @JsonProperty("create_on")
    private LocalDateTime createOn;

    private SessionInfo session;

    private List<TicketingDetail> ticketingDetails;

    private RegisterInfo sourceRegister;

    private RegisterInfo destinationRegister;

    /**
     * Default constructor for JSON serialization.
     */
    public FundRequestResponse() {
    }

    @Data
    public static class SessionInfo {
        private CashRegisterInfo cashRegister;

        public SessionInfo() {
        }

        public SessionInfo(CashRegisterInfo cashRegister) {
            this.cashRegister = cashRegister;
        }
    }

    @Data
    public static class CashRegisterInfo {
        private String town;

        @JsonProperty("mac_address")
        private String macAddress;

        public CashRegisterInfo() {
        }

        public CashRegisterInfo(String town, String macAddress) {
            this.town = town;
            this.macAddress = macAddress;
        }
    }

    @Data
    public static class RegisterInfo {
        private String town;
        private String country;
        private String adress;

        @JsonProperty("mac_address")
        private String macAddress;

        public RegisterInfo() {
        }

        public RegisterInfo(String town, String country, String adress, String macAddress) {
            this.town = town;
            this.country = country;
            this.adress = adress;
            this.macAddress = macAddress;
        }
    }

    @Data
    public static class TicketingDetail {
        private Integer quantity;
        private BigDecimal value;
        private BigDecimal total;
        private CashRegisterTicketingDenominationResponse denomination;

        public TicketingDetail() {
        }

        public TicketingDetail(
                Integer quantity,
                BigDecimal value,
                BigDecimal total,
                CashRegisterTicketingDenominationResponse denomination
        ) {
            this.quantity = quantity;
            this.value = value;
            this.total = total;
            this.denomination = denomination;
        }
    }
}
