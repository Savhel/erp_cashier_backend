package com.erp.cashier.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "app.cashier-core")
public class CashierCoreProperties {
    private boolean enabled = true;
    private String baseUrl = "http://localhost:8080";
    /**
     * Mode "pur proxy" : quand true, TOUTES les routes /api/* sont relayées vers iwm
     * (le BFF n'exécute plus aucune logique/BD locale). Réversible. Défaut false : à activer
     * seulement après validation runtime de bout en bout (auth + toutes les routes via iwm).
     */
    private boolean passthroughAll = false;
}
