package com.erp.cashier.dto;

import lombok.Data;

/**
 * Admin user response payload.
 *
 * @author ERP Cashier Team
 * @since 2025-01-02
 */
@Data
public class AdminUserResponse {
    private String id;
    private String userName;
    private String userFirstName;
    private String mail;
    private String accountNumber;
    private String telegramChatId;
    private String country;
    private String phone;
    private Boolean actif;
    private AdminProfileResponse adminProfile;

    /**
     * Default constructor for JSON serialization.
     */
    public AdminUserResponse() {
    }

    /**
     * Creates an admin user response.
     *
     * @param id person identifier
     * @param userName username
     * @param userFirstName first name
     * @param mail email address
     * @param accountNumber account number
     * @param telegramChatId telegram chat identifier
     * @param country country
     * @param phone phone number
     * @param actif active flag
     * @param adminProfile admin profile payload
     */
    public AdminUserResponse(
            String id,
            String userName,
            String userFirstName,
            String mail,
            String accountNumber,
            String telegramChatId,
            String country,
            String phone,
            Boolean actif,
            AdminProfileResponse adminProfile
    ) {
        this.id = id;
        this.userName = userName;
        this.userFirstName = userFirstName;
        this.mail = mail;
        this.accountNumber = accountNumber;
        this.telegramChatId = telegramChatId;
        this.country = country;
        this.phone = phone;
        this.actif = actif;
        this.adminProfile = adminProfile;
    }
}
