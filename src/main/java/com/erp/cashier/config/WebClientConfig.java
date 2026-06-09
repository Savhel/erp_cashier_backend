package com.erp.cashier.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Fournit explicitement le bean {@link WebClient.Builder}.
 * En Spring Boot 4, l'auto-configuration ne l'expose pas systématiquement ; plusieurs services
 * (RtComOpsClient, AccountingCashMovementService, etc.) l'injectent.
 */
@Configuration
public class WebClientConfig {

    @Bean
    public WebClient.Builder webClientBuilder() {
        return WebClient.builder();
    }
}
