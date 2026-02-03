package com.erp.cashier;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Entry point for the ERP Cashier backend application.
 *
 * @author ERP Cashier Team
 * @since 2025-10-01
 */

@SpringBootApplication
@EnableScheduling
public class ErpCashierBackendApplication {
    /**
     * Starts the Spring Boot application.
     *
     * @param args application arguments
     */
    public static void main(String[] args) {
        SpringApplication.run(ErpCashierBackendApplication.class, args);
    }
}
