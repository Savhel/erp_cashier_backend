package com.erp.cashier.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Fournit explicitement les beans d'infra que l'auto-configuration de Spring Boot 4
 * (modularisée) n'expose pas systématiquement ici : {@link WebClient.Builder} et
 * {@link ObjectMapper}. Plusieurs services les injectent (RtComOpsClient, AuditService, etc.).
 */
@Configuration
public class WebClientConfig {

    @Bean
    @ConditionalOnMissingBean
    public WebClient.Builder webClientBuilder() {
        return WebClient.builder();
    }

    @Bean
    @ConditionalOnMissingBean
    public ObjectMapper objectMapper() {
        return new ObjectMapper().findAndRegisterModules();
    }
}
