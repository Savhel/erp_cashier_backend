package com.erp.cashier.service;

import com.erp.cashier.dto.DenominationResponse;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

/**
 * Service for configuration lookups.
 *
 * @author ERP Cashier Team
 * @since 2025-01-20
 */
@Service
public class ConfigService {
    private final R2dbcEntityTemplate entityTemplate;

    /**
     * Creates the config service.
     *
     * @param entityTemplate entity template
     */
    public ConfigService(R2dbcEntityTemplate entityTemplate) {
        this.entityTemplate = entityTemplate;
    }

    /**
     * Lists active currency denominations.
     *
     * @return active denominations
     */
    public Flux<DenominationResponse> listDenominations() {
        String sql = "SELECT id, currency, value, label, sort_order, is_active "
                + "FROM currency_denomination "
                + "WHERE is_active = true "
                + "ORDER BY sort_order ASC";
        DatabaseClient client = entityTemplate.getDatabaseClient();
        return client.sql(sql)
                .map((row, meta) -> new DenominationResponse(
                        row.get("id", String.class),
                        row.get("currency", String.class),
                        row.get("value", java.math.BigDecimal.class),
                        row.get("label", String.class),
                        row.get("sort_order", Integer.class),
                        row.get("is_active", Boolean.class)
                ))
                .all();
    }
}
