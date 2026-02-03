package com.erp.cashier.service;

import reactor.core.publisher.Mono;

/**
 * External platform validation service.
 *
 * @author ERP Cashier Team
 * @since 2025-01-28
 */
public interface ExternalPlatformService {
    /**
     * Validates login access against external platforms.
     *
     * @param context login context
     * @return completion signal
     */
    Mono<Void> validateLogin(ExternalLoginContext context);
}
