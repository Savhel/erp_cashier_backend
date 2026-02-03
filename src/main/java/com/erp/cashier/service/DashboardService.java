package com.erp.cashier.service;

import com.erp.cashier.dto.DashboardMetricPoint;
import com.erp.cashier.dto.DashboardStatsResponse;
import com.erp.cashier.security.JwtPayload;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

/**
 * Service for dashboard statistics.
 *
 * @author ERP Cashier Team
 * @since 2025-01-20
 */
@Service
public class DashboardService {
    private static final String STATE_OPEN = "ouverte";
    private static final BigDecimal HUNDRED_THOUSAND = new BigDecimal("100000");
    private static final DateTimeFormatter DAILY_LABEL_FORMAT =
            DateTimeFormatter.ofPattern("EEE dd MMM", Locale.FRENCH);

    private final R2dbcEntityTemplate entityTemplate;

    /**
     * Creates the dashboard service.
     *
     * @param entityTemplate entity template
     */
    public DashboardService(R2dbcEntityTemplate entityTemplate) {
        this.entityTemplate = entityTemplate;
    }

    /**
     * Returns dashboard stats for the authenticated user.
     *
     * @param payload authentication payload
     * @return dashboard stats
     */
    public Mono<DashboardStatsResponse> getStats(JwtPayload payload) {
        if (payload == null || !StringUtils.hasText(payload.getUserId())) {
            return Mono.error(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required."));
        }
        String orgId = trimToNull(payload.getOrganizationId());
        if (!StringUtils.hasText(orgId)) {
            return Mono.error(new ResponseStatusException(HttpStatus.FORBIDDEN, "Organization scope is required."));
        }
        String agencyId = trimToNull(payload.getAgencyId());
        boolean isCashier = isCashier(payload.getRole());
        LocalDate today = LocalDate.now();
        LocalDateTime todayStart = today.atStartOfDay();
        LocalDateTime tomorrowStart = today.plusDays(1).atStartOfDay();

        Mono<Long> activeSessions = countActiveSessions(orgId, agencyId);
        Mono<BigDecimal> totalRevenue = sumRevenue(orgId, agencyId, null, null);
        Mono<Long> todayMovements = countMovements(orgId, agencyId, todayStart, tomorrowStart);
        Mono<BigDecimal> todayTotal = sumRevenue(orgId, agencyId, todayStart, tomorrowStart);
        Mono<List<DashboardMetricPoint>> monthlyRevenue = listMonthlyRevenue(orgId, agencyId);
        Mono<List<DashboardMetricPoint>> dailyRevenue = listDailyRevenue(orgId, agencyId);
        Mono<List<DashboardMetricPoint>> hourlyRevenue = listHourlyRevenue(orgId, agencyId, todayStart, tomorrowStart);

        return Mono.zip(activeSessions, totalRevenue, todayMovements, todayTotal,
                        monthlyRevenue, dailyRevenue, hourlyRevenue)
                .map(tuple -> new DashboardStatsResponse(
                        tuple.getT2(),
                        tuple.getT1(),
                        tuple.getT3(),
                        tuple.getT4(),
                        isCashier ? "cashier" : "admin",
                        tuple.getT5(),
                        tuple.getT6(),
                        tuple.getT7()
                ));
    }

    private Mono<Long> countActiveSessions(String organizationId, String agencyId) {
        String sql = "SELECT COUNT(*) AS total "
                + "FROM cash_register_session s "
                + "JOIN cash_register r ON r.id = s.cash_register_id "
                + "JOIN agency a ON a.id = r.agency_id "
                + "WHERE s.state = :state AND a.organization_id = :orgId";
        if (StringUtils.hasText(agencyId)) {
            sql += " AND a.id = :agencyId";
        }
        DatabaseClient client = entityTemplate.getDatabaseClient();
        DatabaseClient.GenericExecuteSpec spec = client.sql(sql)
                .bind("state", STATE_OPEN)
                .bind("orgId", organizationId);
        if (StringUtils.hasText(agencyId)) {
            spec = spec.bind("agencyId", agencyId);
        }
        return spec.map((row, meta) -> row.get("total", Long.class))
                .one()
                .defaultIfEmpty(0L);
    }

    private Mono<Long> countMovements(
            String organizationId,
            String agencyId,
            LocalDateTime start,
            LocalDateTime end
    ) {
        String sql = "SELECT COUNT(*) AS total "
                + "FROM cash_register_movement m "
                + "JOIN cash_register_session s ON s.id = m.session_id "
                + "JOIN cash_register r ON r.id = s.cash_register_id "
                + "JOIN agency a ON a.id = r.agency_id "
                + "WHERE a.organization_id = :orgId AND m.is_deleted = false "
                + "AND m.create_on >= :start AND m.create_on < :end";
        if (StringUtils.hasText(agencyId)) {
            sql += " AND a.id = :agencyId";
        }
        DatabaseClient client = entityTemplate.getDatabaseClient();
        DatabaseClient.GenericExecuteSpec spec = client.sql(sql)
                .bind("orgId", organizationId)
                .bind("start", start)
                .bind("end", end);
        if (StringUtils.hasText(agencyId)) {
            spec = spec.bind("agencyId", agencyId);
        }
        return spec.map((row, meta) -> row.get("total", Long.class))
                .one()
                .defaultIfEmpty(0L);
    }

    private Mono<BigDecimal> sumRevenue(
            String organizationId,
            String agencyId,
            LocalDateTime start,
            LocalDateTime end
    ) {
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT COALESCE(SUM(CASE ");
        sql.append("WHEN m.sense IN ('entree', 'in') THEN m.amount ");
        sql.append("WHEN m.sense IN ('sortie', 'out') THEN -m.amount ");
        sql.append("ELSE 0 END), 0) AS total ");
        sql.append("FROM cash_register_movement m ");
        sql.append("JOIN cash_register_session s ON s.id = m.session_id ");
        sql.append("JOIN cash_register r ON r.id = s.cash_register_id ");
        sql.append("JOIN agency a ON a.id = r.agency_id ");
        sql.append("WHERE a.organization_id = :orgId AND m.is_deleted = false ");
        if (StringUtils.hasText(agencyId)) {
            sql.append("AND a.id = :agencyId ");
        }
        if (start != null && end != null) {
            sql.append("AND m.create_on >= :start AND m.create_on < :end ");
        }
        DatabaseClient.GenericExecuteSpec spec = entityTemplate.getDatabaseClient()
                .sql(sql.toString())
                .bind("orgId", organizationId);
        if (StringUtils.hasText(agencyId)) {
            spec = spec.bind("agencyId", agencyId);
        }
        if (start != null && end != null) {
            spec = spec.bind("start", start).bind("end", end);
        }
        return spec.map((row, meta) -> row.get("total", BigDecimal.class))
                .one()
                .defaultIfEmpty(BigDecimal.ZERO);
    }

    private Mono<List<DashboardMetricPoint>> listMonthlyRevenue(String organizationId, String agencyId) {
        String scopedWhere = "WHERE a.organization_id = :orgId";
        if (StringUtils.hasText(agencyId)) {
            scopedWhere = scopedWhere + " AND a.id = :agencyId";
        }
        String sql = "SELECT date_trunc('month', m.create_on) AS period, "
                + "COALESCE(SUM(CASE "
                + "WHEN m.sense IN ('entree', 'in') THEN m.amount "
                + "WHEN m.sense IN ('sortie', 'out') THEN -m.amount "
                + "ELSE 0 END), 0) AS total "
                + "FROM cash_register_movement m "
                + "JOIN cash_register_session s ON s.id = m.session_id "
                + "JOIN cash_register r ON r.id = s.cash_register_id "
                + "JOIN agency a ON a.id = r.agency_id "
                + scopedWhere + " AND m.is_deleted = false "
                + "AND m.create_on >= date_trunc('month', now()) - interval '11 months' "
                + "GROUP BY period ORDER BY period";
        DatabaseClient.GenericExecuteSpec spec = entityTemplate.getDatabaseClient()
                .sql(sql)
                .bind("orgId", organizationId);
        if (StringUtils.hasText(agencyId)) {
            spec = spec.bind("agencyId", agencyId);
        }
        return spec.map((row, meta) -> {
            LocalDateTime period = row.get("period", LocalDateTime.class);
            BigDecimal total = row.get("total", BigDecimal.class);
            return Map.entry(period != null ? YearMonth.from(period) : null, total);
        })
                .all()
                .collectList()
                .map(entries -> {
                    Map<YearMonth, BigDecimal> totals = new HashMap<>();
                    for (Map.Entry<YearMonth, BigDecimal> entry : entries) {
                        if (entry.getKey() != null) {
                            totals.put(entry.getKey(), entry.getValue());
                        }
                    }
                    List<DashboardMetricPoint> points = new java.util.ArrayList<>();
                    YearMonth start = YearMonth.now().minusMonths(11);
                    for (int i = 0; i < 12; i++) {
                        YearMonth current = start.plusMonths(i);
                        BigDecimal total = totals.getOrDefault(current, BigDecimal.ZERO);
                        String label = current.getMonth().getDisplayName(TextStyle.SHORT, Locale.ENGLISH);
                        points.add(new DashboardMetricPoint(label, toHundredThousands(total)));
                    }
                    return points;
                });
    }

    private Mono<List<DashboardMetricPoint>> listDailyRevenue(String organizationId, String agencyId) {
        LocalDate today = LocalDate.now();
        LocalDate startDate = today.minusDays(6);
        LocalDateTime start = startDate.atStartOfDay();
        LocalDateTime end = today.plusDays(1).atStartOfDay();
        String scopedWhere = "WHERE a.organization_id = :orgId";
        if (StringUtils.hasText(agencyId)) {
            scopedWhere = scopedWhere + " AND a.id = :agencyId";
        }
        String sql = "SELECT date_trunc('day', m.create_on) AS period, "
                + "COALESCE(SUM(CASE "
                + "WHEN m.sense IN ('entree', 'in') THEN m.amount "
                + "WHEN m.sense IN ('sortie', 'out') THEN -m.amount "
                + "ELSE 0 END), 0) AS total "
                + "FROM cash_register_movement m "
                + "JOIN cash_register_session s ON s.id = m.session_id "
                + "JOIN cash_register r ON r.id = s.cash_register_id "
                + "JOIN agency a ON a.id = r.agency_id "
                + scopedWhere + " AND m.is_deleted = false "
                + "AND m.create_on >= :start AND m.create_on < :end "
                + "GROUP BY period ORDER BY period";
        DatabaseClient.GenericExecuteSpec spec = entityTemplate.getDatabaseClient()
                .sql(sql)
                .bind("orgId", organizationId)
                .bind("start", start)
                .bind("end", end);
        if (StringUtils.hasText(agencyId)) {
            spec = spec.bind("agencyId", agencyId);
        }
        return spec.map((row, meta) -> {
            LocalDateTime period = row.get("period", LocalDateTime.class);
            BigDecimal total = row.get("total", BigDecimal.class);
            return Map.entry(period != null ? period.toLocalDate() : null, total);
        })
                .all()
                .collectList()
                .map(entries -> {
                    Map<LocalDate, BigDecimal> totals = new HashMap<>();
                    for (Map.Entry<LocalDate, BigDecimal> entry : entries) {
                        if (entry.getKey() != null) {
                            totals.put(entry.getKey(), entry.getValue());
                        }
                    }
                    List<DashboardMetricPoint> points = new java.util.ArrayList<>();
                    for (int i = 0; i < 7; i++) {
                        LocalDate current = startDate.plusDays(i);
                        BigDecimal total = totals.getOrDefault(current, BigDecimal.ZERO);
                        String label = DAILY_LABEL_FORMAT.format(current).toLowerCase(Locale.FRENCH);
                        points.add(new DashboardMetricPoint(label, toHundredThousands(total)));
                    }
                    return points;
                });
    }

    private Mono<List<DashboardMetricPoint>> listHourlyRevenue(
            String organizationId,
            String agencyId,
            LocalDateTime start,
            LocalDateTime end
    ) {
        String scopedWhere = "WHERE a.organization_id = :orgId";
        if (StringUtils.hasText(agencyId)) {
            scopedWhere = scopedWhere + " AND a.id = :agencyId";
        }
        String sql = "SELECT EXTRACT(HOUR FROM m.create_on) AS hour, "
                + "COALESCE(SUM(CASE "
                + "WHEN m.sense IN ('entree', 'in') THEN m.amount "
                + "WHEN m.sense IN ('sortie', 'out') THEN -m.amount "
                + "ELSE 0 END), 0) AS total "
                + "FROM cash_register_movement m "
                + "JOIN cash_register_session s ON s.id = m.session_id "
                + "JOIN cash_register r ON r.id = s.cash_register_id "
                + "JOIN agency a ON a.id = r.agency_id "
                + scopedWhere + " AND m.is_deleted = false "
                + "AND m.create_on >= :start AND m.create_on < :end "
                + "GROUP BY hour ORDER BY hour";
        DatabaseClient.GenericExecuteSpec spec = entityTemplate.getDatabaseClient()
                .sql(sql)
                .bind("orgId", organizationId)
                .bind("start", start)
                .bind("end", end);
        if (StringUtils.hasText(agencyId)) {
            spec = spec.bind("agencyId", agencyId);
        }
        return spec.map((row, meta) -> {
            Integer hour = row.get("hour", Integer.class);
            BigDecimal total = row.get("total", BigDecimal.class);
            return Map.entry(hour, total);
        })
                .all()
                .collectList()
                .map(entries -> {
                    Map<Integer, BigDecimal> totals = new HashMap<>();
                    for (Map.Entry<Integer, BigDecimal> entry : entries) {
                        if (entry.getKey() != null) {
                            totals.put(entry.getKey(), entry.getValue());
                        }
                    }
                    List<DashboardMetricPoint> points = new java.util.ArrayList<>();
                    for (int hour = 0; hour < 24; hour++) {
                        BigDecimal total = totals.getOrDefault(hour, BigDecimal.ZERO);
                        points.add(new DashboardMetricPoint(String.format("%02d", hour), toHundredThousands(total)));
                    }
                    return points;
                });
    }

    private BigDecimal toHundredThousands(BigDecimal value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        return value.divide(HUNDRED_THOUSAND, 2, RoundingMode.HALF_UP);
    }

    private boolean isCashier(String role) {
        return StringUtils.hasText(role) && role.toLowerCase(Locale.ROOT).contains("cashier");
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }
}
