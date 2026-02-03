package com.erp.cashier.controller;

import com.erp.cashier.dto.DenominationResponse;
import com.erp.cashier.service.ConfigService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

/**
 * Configuration endpoints.
 *
 * @author ERP Cashier Team
 * @since 2025-01-20
 */
@RestController
@RequestMapping("/api/config")
public class ConfigController {
    private final ConfigService configService;

    /**
     * Creates the config controller.
     *
     * @param configService config service
     */
    public ConfigController(ConfigService configService) {
        this.configService = configService;
    }

    /**
     * Lists active denominations.
     *
     * @return denominations
     */
    @GetMapping("/denominations")
    @PreAuthorize("isAuthenticated()")
    public Flux<DenominationResponse> listDenominations() {
        return configService.listDenominations();
    }
}
