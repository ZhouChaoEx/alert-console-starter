package com.middleware.alert.console.service;

import com.middleware.alert.console.model.AlertPageView;
import com.middleware.alert.console.model.AlertQuery;
import com.middleware.alert.console.model.AlertRecordView;
import com.middleware.alert.console.model.DashboardView;
import com.middleware.alert.console.model.ServiceView;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * 服务列表、告警查询和大屏聚合。
 */
public class AlertQueryService {

    private static final DateTimeFormatter DATE_TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter HOUR_MINUTE =
            DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter MONTH_DAY =
            DateTimeFormatter.ofPattern("MM-dd");

    private static final int DEFAULT_SIZE = 50;
    private static final int MAX_SIZE = 200;

    private final JdbcTemplate jdbcTemplate;

    public AlertQueryService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<ServiceView> listServices(String currentAppName) {
        List<String> appNames = jdbcTemplate.queryForList(
                "SELECT app_name FROM alert_config "
                        + "UNION SELECT app_name FROM alert_info ORDER BY app_name",
                String.class);

        List<ServiceView> result = new ArrayList<ServiceView>();
        for (String appName : appNames) {
            ServiceView view = new ServiceView();
            view.setAppName(appName);
            view.setCurrentApp(appName.equals(currentAppName));

            Long configCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM alert_config WHERE app_name = ?", Long.class, appName);
            view.setInitialized(configCount != null && configCount > 0);
            if (view.isInitialized()) {
                Long version = jdbcTemplate.queryForObject(
                        "SELECT COALESCE(MAX(version), 0) FROM alert_config WHERE app_name = ?",
                        Long.class, appName);
                view.setVersion(version == null ? 0L : version);
                List<String> values = jdbcTemplate.queryForList(
                        "SELECT config_value FROM alert_config "
                                + "WHERE app_name = ? AND config_key = 'console.instances'",
                        String.class, appName);
                view.setInstances(parseInstances(values.isEmpty() ? null : values.get(0)));
            }
            view.setInstanceCount(view.getInstances().size());

            Long alertCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM alert_info WHERE app_name = ?", Long.class, appName);
            view.setAlertCount(alertCount == null ? 0L : alertCount);
            Timestamp last = jdbcTemplate.queryForObject(
                    "SELECT MAX(alert_time) FROM alert_info WHERE app_name = ?",
                    Timestamp.class, appName);
            view.setLastAlertTime(format(last));
            view.setStatus(view.isInitialized() && !view.getInstances().isEmpty() ? "ok" : "warn");
            result.add(view);
        }
        Collections.sort(result, new Comparator<ServiceView>() {
            @Override
            public int compare(ServiceView left, ServiceView right) {
                int byCount = Long.compare(right.getAlertCount(), left.getAlertCount());
                return byCount != 0 ? byCount : left.getAppName().compareTo(right.getAppName());
            }
        });
        return result;
    }

    public AlertPageView queryAlerts(AlertQuery query) {
        Range range = resolveRange(query.getRange());
        int page = Math.max(1, query.getPage());
        int size = query.getSize() <= 0 ? DEFAULT_SIZE : Math.min(query.getSize(), MAX_SIZE);
        Where where = buildWhere(range, query);

        Long totalValue = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM alert_info " + where.sql,
                Long.class, where.args.toArray());
        long total = totalValue == null ? 0L : totalValue;

        Map<String, Object> counts = jdbcTemplate.queryForMap(
                "SELECT "
                        + "SUM(CASE WHEN alert_level = 'CRITICAL' THEN 1 ELSE 0 END) AS critical, "
                        + "SUM(CASE WHEN alert_level = 'ERROR' THEN 1 ELSE 0 END) AS error, "
                        + "SUM(CASE WHEN alert_level = 'WARN' THEN 1 ELSE 0 END) AS warn "
                        + "FROM alert_info " + where.sql,
                where.args.toArray());

        List<Object> args = new ArrayList<Object>(where.args);
        args.add(size);
        args.add((page - 1) * size);
        List<AlertRecordView> items = jdbcTemplate.query(
                "SELECT id, app_name, alert_type, alert_level, title, request_uri, "
                        + "http_method, http_status, cost_ms, trace_id, repeat_count, "
                        + "alert_time, create_time FROM alert_info " + where.sql
                        + " ORDER BY alert_time DESC, id DESC LIMIT ? OFFSET ?",
                new ListRowMapper(), args.toArray());

        AlertPageView pageView = new AlertPageView();
        pageView.setItems(items);
        pageView.setTotal(total);
        pageView.setPage(page);
        pageView.setSize(size);
        pageView.setTotalPages((int) ((total + size - 1) / size));
        pageView.setCritical(number(counts, "critical"));
        pageView.setError(number(counts, "error"));
        pageView.setWarn(number(counts, "warn"));
        return pageView;
    }

    public AlertRecordView getAlert(long id) {
        List<AlertRecordView> rows = jdbcTemplate.query(
                "SELECT * FROM alert_info WHERE id = ?", new DetailRowMapper(), id);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public DashboardView dashboard(String rangeValue) {
        Range range = resolveRange(rangeValue);
        DashboardView view = new DashboardView();
        view.setRange(range.name);
        view.setGeneratedAt(format(new Timestamp(System.currentTimeMillis())));

        Map<String, Object> summary = jdbcTemplate.queryForMap(
                "SELECT COUNT(*) AS total, "
                        + "SUM(CASE WHEN alert_level = 'CRITICAL' THEN 1 ELSE 0 END) AS critical, "
                        + "SUM(CASE WHEN alert_level = 'ERROR' THEN 1 ELSE 0 END) AS error, "
                        + "SUM(CASE WHEN alert_level = 'WARN' THEN 1 ELSE 0 END) AS warn, "
                        + "COUNT(DISTINCT app_name) AS affected, "
                        + "AVG(cost_ms) AS avg_cost "
                        + "FROM alert_info WHERE alert_time >= ? AND alert_time < ?",
                range.start, range.end);
        DashboardView.Summary summaryView = view.getSummary();
        summaryView.setTotal(number(summary, "total"));
        summaryView.setCritical(number(summary, "critical"));
        summaryView.setError(number(summary, "error"));
        summaryView.setWarn(number(summary, "warn"));
        summaryView.setAffectedServices(number(summary, "affected"));
        summaryView.setAvgCostMs(number(summary, "avg_cost"));

        Timestamp lastHourStart = new Timestamp(range.end.getTime() - 3600_000L);
        Long lastHour = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM alert_info WHERE alert_time >= ? AND alert_time < ?",
                Long.class, lastHourStart, range.end);
        summaryView.setLastHour(lastHour == null ? 0L : lastHour);

        view.setTrend(buildTrend(range));
        view.setTypes(buildTypes(range));
        view.setServices(buildServices(range));
        view.setLatest(buildLatest(range));
        view.setSlowRequests(buildSlowRequests(range));

        view.getResources().setAvailable(false);
        view.getResources().setMessage("连接池/线程池/堆内存探针尚未实现（M2/M3）");
        return view;
    }

    private List<DashboardView.TrendPoint> buildTrend(Range range) {
        int bucketCount;
        long bucketMillis;
        DateTimeFormatter formatter;
        switch (range.name) {
            case "1h":
                bucketCount = 12;
                bucketMillis = 5 * 60 * 1000L;
                formatter = HOUR_MINUTE;
                break;
            case "6h":
                bucketCount = 12;
                bucketMillis = 30 * 60 * 1000L;
                formatter = HOUR_MINUTE;
                break;
            case "7d":
                bucketCount = 7;
                bucketMillis = 24 * 3600 * 1000L;
                formatter = MONTH_DAY;
                break;
            default:
                bucketCount = 24;
                bucketMillis = 3600 * 1000L;
                formatter = HOUR_MINUTE;
        }

        List<DashboardView.TrendPoint> points = new ArrayList<DashboardView.TrendPoint>();
        for (int i = 0; i < bucketCount; i++) {
            DashboardView.TrendPoint point = new DashboardView.TrendPoint();
            point.setBucket(formatMillis(range.start.getTime() + i * bucketMillis, formatter));
            points.add(point);
        }

        List<TrendRow> rows = jdbcTemplate.query(
                "SELECT alert_time, alert_level FROM alert_info "
                        + "WHERE alert_time >= ? AND alert_time < ?",
                (rs, rowNum) -> new TrendRow(rs.getTimestamp("alert_time"),
                        rs.getString("alert_level")),
                range.start, range.end);

        for (TrendRow row : rows) {
            long index = (row.alertTime.getTime() - range.start.getTime()) / bucketMillis;
            if (index < 0) {
                index = 0;
            }
            if (index >= bucketCount) {
                index = bucketCount - 1;
            }
            DashboardView.TrendPoint point = points.get((int) index);
            if ("CRITICAL".equals(row.alertLevel)) {
                point.setCritical(point.getCritical() + 1);
            } else if ("ERROR".equals(row.alertLevel)) {
                point.setError(point.getError() + 1);
            } else if ("WARN".equals(row.alertLevel)) {
                point.setWarn(point.getWarn() + 1);
            }
            point.setTotal(point.getTotal() + 1);
        }
        return points;
    }

    private List<DashboardView.TypeCount> buildTypes(Range range) {
        return jdbcTemplate.query(
                "SELECT alert_type, COUNT(*) AS cnt FROM alert_info "
                        + "WHERE alert_time >= ? AND alert_time < ? "
                        + "GROUP BY alert_type ORDER BY cnt DESC",
                (rs, rowNum) -> {
                    DashboardView.TypeCount item = new DashboardView.TypeCount();
                    item.setType(rs.getString("alert_type"));
                    item.setCount(rs.getLong("cnt"));
                    return item;
                }, range.start, range.end);
    }

    private List<DashboardView.ServiceCount> buildServices(Range range) {
        return jdbcTemplate.query(
                "SELECT app_name, COUNT(*) AS total, "
                        + "SUM(CASE WHEN alert_level = 'CRITICAL' THEN 1 ELSE 0 END) AS critical "
                        + "FROM alert_info WHERE alert_time >= ? AND alert_time < ? "
                        + "GROUP BY app_name ORDER BY total DESC LIMIT 10",
                (rs, rowNum) -> {
                    DashboardView.ServiceCount item = new DashboardView.ServiceCount();
                    item.setAppName(rs.getString("app_name"));
                    item.setTotal(rs.getLong("total"));
                    item.setCritical(rs.getLong("critical"));
                    return item;
                }, range.start, range.end);
    }

    private List<AlertRecordView> buildLatest(Range range) {
        return jdbcTemplate.query(
                "SELECT id, app_name, alert_type, alert_level, title, request_uri, "
                        + "http_method, http_status, cost_ms, trace_id, repeat_count, "
                        + "alert_time, create_time FROM alert_info "
                        + "WHERE alert_time >= ? AND alert_time < ? "
                        + "ORDER BY alert_time DESC, id DESC LIMIT 20",
                new ListRowMapper(), range.start, range.end);
    }

    private List<DashboardView.SlowUri> buildSlowRequests(Range range) {
        return jdbcTemplate.query(
                "SELECT request_uri, COUNT(*) AS cnt, MAX(cost_ms) AS max_cost "
                        + "FROM alert_info WHERE alert_type = 'SLOW_REQUEST' "
                        + "AND alert_time >= ? AND alert_time < ? "
                        + "AND request_uri IS NOT NULL "
                        + "GROUP BY request_uri ORDER BY max_cost DESC LIMIT 10",
                (rs, rowNum) -> {
                    DashboardView.SlowUri item = new DashboardView.SlowUri();
                    item.setUri(rs.getString("request_uri"));
                    item.setCount(rs.getLong("cnt"));
                    item.setMaxCostMs(rs.getLong("max_cost"));
                    return item;
                }, range.start, range.end);
    }

    private Where buildWhere(Range range, AlertQuery query) {
        StringBuilder sql = new StringBuilder(" WHERE alert_time >= ? AND alert_time < ?");
        List<Object> args = new ArrayList<Object>();
        args.add(range.start);
        args.add(range.end);

        if (query.getAppNames() != null && !query.getAppNames().isEmpty()) {
            sql.append(" AND app_name IN (");
            for (int i = 0; i < query.getAppNames().size(); i++) {
                if (i > 0) {
                    sql.append(',');
                }
                sql.append('?');
            }
            sql.append(')');
            args.addAll(query.getAppNames());
        }
        if (hasText(query.getAlertType())) {
            sql.append(" AND alert_type = ?");
            args.add(query.getAlertType().trim());
        }
        if (hasText(query.getAlertLevel())) {
            sql.append(" AND alert_level = ?");
            args.add(query.getAlertLevel().trim());
        }
        if (hasText(query.getKeyword())) {
            sql.append(" AND (LOWER(title) LIKE ? OR LOWER(request_uri) LIKE ? "
                    + "OR LOWER(trace_id) LIKE ?)");
            String keyword = "%" + query.getKeyword().trim().toLowerCase() + "%";
            args.add(keyword);
            args.add(keyword);
            args.add(keyword);
        }
        return new Where(sql.toString(), args);
    }

    private Range resolveRange(String rangeValue) {
        String name = rangeValue == null ? "24h" : rangeValue.trim();
        long millis;
        switch (name) {
            case "1h":
                millis = 3600_000L;
                break;
            case "6h":
                millis = 6 * 3600_000L;
                break;
            case "7d":
                millis = 7 * 24 * 3600_000L;
                break;
            default:
                name = "24h";
                millis = 24 * 3600_000L;
        }
        long end = System.currentTimeMillis();
        return new Range(name, new Timestamp(end - millis), new Timestamp(end));
    }

    private static List<String> parseInstances(String value) {
        if (value == null || value.trim().isEmpty()) {
            return Collections.emptyList();
        }
        List<String> instances = new ArrayList<String>();
        for (String part : value.split(",")) {
            String instance = part.trim();
            if (!instance.isEmpty()) {
                instances.add(instance);
            }
        }
        return instances;
    }

    private static String format(Timestamp timestamp) {
        if (timestamp == null) {
            return null;
        }
        return DATE_TIME.format(timestamp.toInstant().atZone(ZoneId.systemDefault()));
    }

    private static String formatMillis(long millis, DateTimeFormatter formatter) {
        return formatter.format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()));
    }

    private static long number(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) {
            value = map.get(key.toUpperCase());
        }
        if (value == null) {
            return 0L;
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        return Long.parseLong(String.valueOf(value));
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private static Integer nullableInt(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }

    private static Long nullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private static final class Range {
        private final String name;
        private final Timestamp start;
        private final Timestamp end;

        private Range(String name, Timestamp start, Timestamp end) {
            this.name = name;
            this.start = start;
            this.end = end;
        }
    }

    private static final class Where {
        private final String sql;
        private final List<Object> args;

        private Where(String sql, List<Object> args) {
            this.sql = sql;
            this.args = args;
        }
    }

    private static final class TrendRow {
        private final Timestamp alertTime;
        private final String alertLevel;

        private TrendRow(Timestamp alertTime, String alertLevel) {
            this.alertTime = alertTime;
            this.alertLevel = alertLevel;
        }
    }

    private static final class ListRowMapper
            implements org.springframework.jdbc.core.RowMapper<AlertRecordView> {
        @Override
        public AlertRecordView mapRow(ResultSet rs, int rowNum) throws SQLException {
            AlertRecordView view = new AlertRecordView();
            view.setId(rs.getLong("id"));
            view.setAppName(rs.getString("app_name"));
            view.setAlertType(rs.getString("alert_type"));
            view.setAlertLevel(rs.getString("alert_level"));
            view.setTitle(rs.getString("title"));
            view.setRequestUri(rs.getString("request_uri"));
            view.setHttpMethod(rs.getString("http_method"));
            view.setHttpStatus(nullableInt(rs, "http_status"));
            view.setCostMs(nullableLong(rs, "cost_ms"));
            view.setTraceId(rs.getString("trace_id"));
            view.setRepeatCount(rs.getInt("repeat_count"));
            view.setAlertTime(format(rs.getTimestamp("alert_time")));
            view.setCreateTime(format(rs.getTimestamp("create_time")));
            return view;
        }
    }

    private static final class DetailRowMapper
            implements org.springframework.jdbc.core.RowMapper<AlertRecordView> {
        @Override
        public AlertRecordView mapRow(ResultSet rs, int rowNum) throws SQLException {
            AlertRecordView view = new AlertRecordView();
            view.setId(rs.getLong("id"));
            view.setAppName(rs.getString("app_name"));
            view.setAlertType(rs.getString("alert_type"));
            view.setAlertLevel(rs.getString("alert_level"));
            view.setTitle(rs.getString("title"));
            view.setContent(rs.getString("content"));
            view.setExceptionType(rs.getString("exception_type"));
            view.setStackTrace(rs.getString("stack_trace"));
            view.setRequestUri(rs.getString("request_uri"));
            view.setTargetUrl(rs.getString("target_url"));
            view.setHttpMethod(rs.getString("http_method"));
            view.setHttpStatus(nullableInt(rs, "http_status"));
            view.setCostMs(nullableLong(rs, "cost_ms"));
            view.setSqlText(rs.getString("sql_text"));
            view.setMapperId(rs.getString("mapper_id"));
            view.setPoolName(rs.getString("pool_name"));
            view.setThreadName(rs.getString("thread_name"));
            view.setTraceId(rs.getString("trace_id"));
            view.setFingerprint(rs.getString("fingerprint"));
            view.setRepeatCount(rs.getInt("repeat_count"));
            view.setFirstTime(format(rs.getTimestamp("first_time")));
            view.setAlertTime(format(rs.getTimestamp("alert_time")));
            view.setCreateTime(format(rs.getTimestamp("create_time")));
            view.setExtra(rs.getString("extra"));
            return view;
        }
    }
}
