package com.erp.cashier.service;

import com.erp.cashier.config.RtSyncProperties;
import com.erp.cashier.dto.external.RtAuthResponse;
import com.erp.cashier.model.RtSyncState;
import com.erp.cashier.repository.RtSyncStateRepository;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Scheduled synchronization job for RT_ComOps data.
 *
 * @author ERP Cashier Team
 * @since 2026-02-01
 */
@Service
public class RtSyncScheduler {
    private static final Logger LOGGER = LoggerFactory.getLogger(RtSyncScheduler.class);
    private static final int ORG_SYNC_CONCURRENCY = 1;

    private final RtSyncProperties properties;
    private final RtComOpsClient rtComOpsClient;
    private final OrganizationSyncService organizationSyncService;
    private final RtSyncStateRepository syncStateRepository;
    private final R2dbcEntityTemplate entityTemplate;
    private final AuditService auditService;
    private final AtomicBoolean running = new AtomicBoolean(false);

    /**
     * Creates the scheduler.
     *
     * @param properties sync properties
     * @param rtComOpsClient RT_ComOps client
     * @param organizationSyncService organization sync service
     * @param syncStateRepository sync state repository
     * @param entityTemplate entity template
     * @param auditService audit service
     */
    public RtSyncScheduler(
            RtSyncProperties properties,
            RtComOpsClient rtComOpsClient,
            OrganizationSyncService organizationSyncService,
            RtSyncStateRepository syncStateRepository,
            @Qualifier("rtEntityTemplate") R2dbcEntityTemplate entityTemplate,
            AuditService auditService
    ) {
        this.properties = properties;
        this.rtComOpsClient = rtComOpsClient;
        this.organizationSyncService = organizationSyncService;
        this.syncStateRepository = syncStateRepository;
        this.entityTemplate = entityTemplate;
        this.auditService = auditService;
    }

    /**
     * Runs the RT_ComOps synchronization job.
     */
    @Scheduled(
            fixedDelayString = "${app.sync.rt.fixed-delay:PT1H}",
            initialDelayString = "${app.sync.rt.initial-delay:PT1M}"
    )
    public void syncFromRt() {
        if (!properties.isEnabled()) {
            return;
        }
        if (!running.compareAndSet(false, true)) {
            LOGGER.info("RT sync already running; skipping this cycle.");
            return;
        }
        LOGGER.info("RT sync started.");
        String email = StringUtils.trimWhitespace(properties.getSuperadminEmail());
        String password = properties.getSuperadminPassword();

        Mono<Void> syncFlow = safeAudit("rt_sync_start", Map.of("message", "RT sync started"))
                .then(Mono.defer(() -> {
                    if (!StringUtils.hasText(email) || !StringUtils.hasText(password)) {
                        LOGGER.warn("RT sync skipped: missing superadmin credentials.");
                        return safeAudit("rt_sync_skipped", Map.of("reason", "missing_credentials"));
                    }
                    return rtComOpsClient.login(email, password)
                            .flatMapMany(this::syncFromLogin)
                            .then();
                }))
                .materialize()
                .flatMap(signal -> {
                    Mono<Void> failureAudit = Mono.empty();
                    if (signal.isOnError()) {
                        Throwable ex = signal.getThrowable();
                        LOGGER.error("RT sync failed.", ex);
                        failureAudit = safeAudit(
                                "rt_sync_failed",
                                Map.of("error", String.valueOf(ex != null ? ex.getMessage() : null))
                        );
                    }
                    Mono<Void> finishedAudit = safeAudit(
                            "rt_sync_finished",
                            Map.of("signal", String.valueOf(signal.getType()))
                    );
                    if (signal.isOnError()) {
                        Throwable ex = signal.getThrowable();
                        return failureAudit
                                .then(finishedAudit)
                                .then(Mono.error(ex != null ? ex : new RuntimeException("RT sync failed")));
                    }
                    return failureAudit.then(finishedAudit);
                })
                .doFinally(signal -> {
                    running.set(false);
                    LOGGER.info("RT sync finished.");
                });

        syncFlow.subscribe();
    }

    private Flux<Void> syncFromLogin(RtAuthResponse response) {
        if (response == null || response.getUser() == null
                || !StringUtils.hasText(response.getUser().getId())
                || !StringUtils.hasText(response.getToken())) {
            LOGGER.warn("RT sync skipped: invalid auth response.");
            return Flux.empty();
        }
        String token = response.getToken();
        String userId = response.getUser().getId();
        return rtComOpsClient.listOrganizationsOverview(token)
                .collectList()
                .flatMapMany(overviews -> {
                    LOGGER.info("RT sync fetched {} organization overviews.", overviews.size());
                    return safeAudit(
                                    "rt_sync_overviews",
                                    Map.of("count", overviews.size())
                            )
                            .thenMany(Flux.fromIterable(overviews));
                })
                .flatMap(
                        overview -> logOverviewSummary(overview)
                                .then(syncOrganization(overview, userId)),
                        ORG_SYNC_CONCURRENCY
                );
    }

    private Mono<Void> syncOrganization(Map<String, Object> overview, String actorId) {
        String organizationId = extractOrganizationId(overview);
        if (!StringUtils.hasText(organizationId)) {
            return Mono.empty();
        }
        return syncStateRepository.findById(organizationId)
                .map(RtSyncState::getLastSyncedAt)
                .flatMap(lastSyncedAt -> organizationSyncService
                        .syncOrganizationOverviewIncremental(overview, lastSyncedAt, actorId))
                .switchIfEmpty(organizationSyncService
                        .syncOrganizationOverviewIncremental(overview, null, actorId))
                .then(updateLastSyncedAt(organizationId))
                .then(safeAudit(
                        "rt_sync_org_success",
                        Map.of("organization_id", organizationId)
                ))
                .doOnSuccess(ignored -> LOGGER.info("RT sync completed for organization {}", organizationId))
                .onErrorResume(ex -> {
                    LOGGER.error("RT sync failed for organization {}", organizationId, ex);
                    return safeAudit(
                                    "rt_sync_org_failed",
                                    Map.of(
                                            "organization_id",
                                            organizationId,
                                            "error",
                                            String.valueOf(ex.getMessage())
                                    )
                            )
                            .then();
                });
    }

    private Mono<Void> updateLastSyncedAt(String organizationId) {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        return entityTemplate.getDatabaseClient()
                .sql("INSERT INTO rt_sync_state (organization_id, last_synced_at) VALUES ($1, $2) "
                        + "ON CONFLICT (organization_id) DO UPDATE SET last_synced_at = EXCLUDED.last_synced_at")
                .bind(0, organizationId)
                .bind(1, now)
                .then();
    }

    private String extractOrganizationId(Map<String, Object> overview) {
        if (overview == null) {
            return null;
        }
        Object organization = overview.get("organization");
        if (!(organization instanceof Map<?, ?> orgMap)) {
            return null;
        }
        Object id = orgMap.get("id");
        return id != null ? String.valueOf(id) : null;
    }

    private Mono<Void> logOverviewSummary(Map<String, Object> overview) {
        String orgId = extractOrganizationId(overview);
        int warehouses = countIterable(overview != null ? overview.get("warehouses") : null);
        int orgAdmins = countIterable(overview != null ? overview.get("orgAdmins") : null);
        int cashiers = countIterable(overview != null ? overview.get("cashiers") : null);
        int agencyAdmins = countIterable(overview != null ? overview.get("agencyAdmins") : null);
        LOGGER.info(
                "RT overview org={} warehouses={} orgAdmins={} cashiers={} agencyAdmins={}",
                orgId,
                warehouses,
                orgAdmins,
                cashiers,
                agencyAdmins
        );
        return safeAudit(
                "rt_org_overview",
                Map.of(
                        "organization_id",
                        orgId,
                        "warehouses",
                        warehouses,
                        "org_admins",
                        orgAdmins,
                        "cashiers",
                        cashiers,
                        "agency_admins",
                        agencyAdmins
                )
        );
    }

    private int countIterable(Object value) {
        if (!(value instanceof Iterable<?> iterable)) {
            return 0;
        }
        int count = 0;
        for (Object ignored : iterable) {
            count++;
        }
        return count;
    }

    private Mono<Void> safeAudit(String type, Object payload) {
        return auditService.recordEvent(type, null, payload)
                .onErrorResume(ex -> Mono.empty());
    }
}
