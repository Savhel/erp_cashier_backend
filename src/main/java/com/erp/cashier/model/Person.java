package com.erp.cashier.model;

import java.time.LocalDateTime;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;
import lombok.Data;

/**
 * Person entity mapped to the person table.
 *
 * @author ERP Cashier Team
 * @since 2025-01-02
 */
@Table("person")
@Data
public class Person {
    @Id
    private String id;

    @Column("user_name")
    private String userName;

    @Column("account_number")
    private String accountNumber;

    @Column("password")
    private String password;

    @Column("actif")
    private Boolean actif;

    @Column("user_first_name")
    private String userFirstName;

    @Column("telegram_chat_id")
    private String telegramChatId;

    @Column("phone")
    private String phone;

    @Column("mail")
    private String mail;

    @Column("card_id")
    private String cardId;

    @Column("born_on")
    private LocalDateTime bornOn;

    @Column("sexe")
    private String sexe;

    @Column("adresse")
    private String adresse;

    @Column("country")
    private String country;

    /**
     * Default constructor for framework usage.
     */
    public Person() {
    }
}
