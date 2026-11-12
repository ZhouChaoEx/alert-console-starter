package com.middleware.alert.console.service;

import com.middleware.alert.config.AlertConfigLoader;
import com.middleware.alert.config.AlertConfigProvider;
import com.middleware.alert.config.AlertConfigRefreshResult;
import com.middleware.alert.console.model.AlertConfigItemView;
import com.middleware.alert.console.model.AlertConfigView;
import com.middleware.alert.console.model.SaveConfigRequest;
import com.middleware.alert.core.AlertContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 配置查询、保存、新增应用与本地刷新。
 */
public class AlertConsoleService {

    private static final Logger log = LoggerFactory.getLogger(AlertConsoleService.class);

    private static final Pattern APP_NAME = Pattern.compile("[A-Za-z0-9._-]{1,128}");

    private static final String SELECT_ROWS =
            "SELECT config_key, config_value, value_type, config_group, description, editable, version "
                    + "FROM alert_config WHERE app_name = ? ORDER BY config_group, config_key";

    private final JdbcTemplate jdbcTemplate;
    private final AlertConfigProvider configProvider;
    private final ObjectProvider<AlertConfigLoader> configLoaderProvider;
    private final AlertContext alertContext;

    public AlertConsoleService(JdbcTemplate jdbcTemplate,
                               AlertConfigProvider configProvider,
                               ObjectProvider<AlertConfigLoader> configLoaderProvider,
                               AlertContext alertContext) {
        this.jdbcTemplate = jdbcTemplate;
        this.configProvider = configProvider;
        this.configLoaderProvider = configLoaderProvider;
        this.alertContext = alertContext;
    }

    public List<String> listApps() {
        List<String> apps = jdbcTemplate.queryForList(
                "SELECT DISTINCT app_name FROM alert_config ORDER BY app_name", String.class);
        List<String> result = new ArrayList<String>(apps);
        String current = currentAppName();
        if (!result.contains(current)) {
            result.add(0, current);
        }
        return result;
    }

    public AlertConfigView getConfig(String requestedApp) {
        String appName = normalizeApp(requestedApp);
        List<ConfigRow> rows = jdbcTemplate.query(SELECT_ROWS, new ConfigRowMapper(), appName);

        Map<String, ConfigRow> byKey = new LinkedHashMap<String, ConfigRow>();
        long version = 0L;
        for (ConfigRow row : rows) {
            byKey.put(row.key, row);
            version = Math.max(version, row.version);
        }

        boolean currentApp = appName.equals(currentAppName());
        Map<String, Object> effective = currentApp
                ? configProvider.get().asMap()
                : Collections.<String, Object>emptyMap();

        List<AlertConfigItemView> items = new ArrayList<AlertConfigItemView>();
        for (AlertConfigTemplate.Entry entry : AlertConfigTemplate.ENTRIES) {
            ConfigRow row = byKey.remove(entry.getKey());
            items.add(toItem(entry, row, effective));
        }
        for (ConfigRow row : byKey.values()) {
            items.add(toItem(null, row, effective));
        }

        AlertConfigView view = new AlertConfigView();
        view.setAppName(appName);
        view.setInitialized(!rows.isEmpty());
        view.setVersion(version);
        view.setCurrentApp(currentApp);
        view.setItems(items);
        view.setInstances(parseInstances(valueOf(items, "console.instances")));
        return view;
    }

    public AlertConfigView createApp(String requestedApp) {
        String appName = normalizeApp(requestedApp);
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM alert_config WHERE app_name = ?", Integer.class, appName);
        if (count != null && count > 0) {
            throw new AlertConsoleException("409", "应用已存在: " + appName);
        }

        List<Object[]> args = new ArrayList<Object[]>();
        for (AlertConfigTemplate.Entry entry : AlertConfigTemplate.ENTRIES) {
            args.add(new Object[]{
                    appName, entry.getKey(), entry.getDefaultValue(), entry.getType(),
                    entry.getGroup(), entry.getDescription(), Boolean.TRUE, 1L, updateBy()
            });
        }
        jdbcTemplate.batchUpdate(
                "INSERT INTO alert_config (app_name, config_key, config_value, value_type, "
                        + "config_group, description, editable, version, update_by) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)", args);
        log.info("alert console created app config, app={}, items={}", appName, args.size());
        return getConfig(appName);
    }

    public AlertConfigView save(SaveConfigRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("请求不能为空");
        }
        String appName = normalizeApp(request.getAppName());
        if (request.getItems() == null || request.getItems().isEmpty()) {
            throw new IllegalArgumentException("没有可保存的配置");
        }
        for (SaveConfigRequest.Item item : request.getItems()) {
            if (item == null || item.getKey() == null || item.getKey().trim().isEmpty()) {
                continue;
            }
            String key = item.getKey().trim();
            AlertConfigTemplate.Entry template = AlertConfigTemplate.find(key);
            String value = item.getValue() == null ? "" : item.getValue();
            validateValue(key, template, value);
            updateOne(appName, key, value, item.getVersion(), template);
        }
        log.info("alert console saved app config, app={}, items={}", appName, request.getItems().size());
        return getConfig(appName);
    }

    public AlertConfigRefreshResult refreshLocal() {
        AlertConfigLoader loader = configLoaderProvider.getIfAvailable();
        if (loader == null) {
            return AlertConfigRefreshResult.skipped("alert.config.enabled=false");
        }
        return loader.refresh();
    }

    public List<String> getInstances(String requestedApp) {
        String appName = normalizeApp(requestedApp);
        List<String> values = jdbcTemplate.queryForList(
                "SELECT config_value FROM alert_config WHERE app_name = ? AND config_key = 'console.instances'",
                String.class, appName);
        if (values.isEmpty()) {
            return Collections.emptyList();
        }
        return parseInstances(values.get(0));
    }

    public String currentAppName() {
        return alertContext.getAppName();
    }

    private void updateOne(String appName, String key, String value, Long version,
                           AlertConfigTemplate.Entry template) {
        int updated;
        if (version != null && version > 0) {
            updated = jdbcTemplate.update(
                    "UPDATE alert_config SET config_value = ?, version = version + 1, "
                            + "update_by = ?, update_time = CURRENT_TIMESTAMP "
                            + "WHERE app_name = ? AND config_key = ? AND version = ?",
                    value, updateBy(), appName, key, version);
            if (updated == 0) {
                Integer exists = jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM alert_config WHERE app_name = ? AND config_key = ?",
                        Integer.class, appName, key);
                if (exists != null && exists > 0) {
                    throw new AlertConsoleException("409", "配置已被其他实例修改，请刷新后重试: " + key);
                }
                insertOne(appName, key, value, template);
            }
        } else {
            updated = jdbcTemplate.update(
                    "UPDATE alert_config SET config_value = ?, version = version + 1, "
                            + "update_by = ?, update_time = CURRENT_TIMESTAMP "
                            + "WHERE app_name = ? AND config_key = ?",
                    value, updateBy(), appName, key);
            if (updated == 0) {
                insertOne(appName, key, value, template);
            }
        }
    }

    private void insertOne(String appName, String key, String value,
                           AlertConfigTemplate.Entry template) {
        if (template == null) {
            throw new AlertConsoleException("400", "未知配置项: " + key);
        }
        try {
            jdbcTemplate.update(
                    "INSERT INTO alert_config (app_name, config_key, config_value, value_type, "
                            + "config_group, description, editable, version, update_by) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    appName, key, value, template.getType(), template.getGroup(),
                    template.getDescription(), Boolean.TRUE, 1L, updateBy());
        } catch (Exception e) {
            throw new AlertConsoleException("409", "配置已被其他实例创建，请刷新后重试: " + key);
        }
    }

    private AlertConfigItemView toItem(AlertConfigTemplate.Entry template, ConfigRow row,
                                       Map<String, Object> effective) {
        AlertConfigItemView item = new AlertConfigItemView();
        String key = template != null ? template.getKey() : row.key;
        item.setKey(key);
        if (row != null) {
            item.setValue(row.value);
            item.setType(row.type);
            item.setGroup(row.group);
            item.setDescription(row.description);
            item.setEditable(row.editable);
            item.setVersion(row.version);
            item.setMissing(false);
        } else {
            item.setValue(template.getDefaultValue());
            item.setType(template.getType());
            item.setGroup(template.getGroup());
            item.setDescription(template.getDescription());
            item.setEditable(true);
            item.setVersion(0L);
            item.setMissing(true);
        }
        item.setRequiresRestart(template != null && template.isRequiresRestart());
        item.setEffectiveValue(effective.get(key));
        return item;
    }

    private void validateValue(String key, AlertConfigTemplate.Entry template, String value) {
        String type = template != null ? template.getType() : "STRING";
        switch (key) {
            case "runtime.enabled":
            case "biz.enabled":
            case "error.npe.enabled":
            case "error.stackoverflow.enabled":
            case "slow-request.enabled":
                parseBool(value);
                break;
            case "write-mode":
                if (!"ASYNC".equalsIgnoreCase(value.trim()) && !"SYNC".equalsIgnoreCase(value.trim())) {
                    throw new IllegalArgumentException("write-mode 只能是 ASYNC 或 SYNC");
                }
                break;
            case "queue.capacity":
            case "batch.size":
            case "stack-trace.max-length":
            case "dedup.window-seconds":
            case "dedup.max-entries":
            case "rate-limit.sample-factor":
                positiveInt(value);
                break;
            case "rate-limit.per-minute":
                nonNegativeInt(value);
                break;
            case "batch.interval-ms":
            case "slow-request.threshold-ms":
                positiveLong(value);
                break;
            case "error.npe.sample-rate":
                double rate = Double.parseDouble(value.trim());
                if (rate < 0D || rate > 1D) {
                    throw new IllegalArgumentException("采样率必须在 0~1 之间");
                }
                break;
            case "console.instances":
                parseInstances(value);
                break;
            case "slow-request.ignore-paths":
                break;
            default:
                if ("INT".equals(type)) {
                    Integer.parseInt(value.trim());
                } else if ("LONG".equals(type)) {
                    Long.parseLong(value.trim());
                } else if ("BOOL".equals(type)) {
                    parseBool(value);
                } else if ("DOUBLE".equals(type)) {
                    Double.parseDouble(value.trim());
                }
        }
    }

    private String normalizeApp(String requestedApp) {
        String appName = requestedApp;
        if (appName == null || appName.trim().isEmpty()) {
            appName = currentAppName();
        }
        appName = appName.trim();
        if (!APP_NAME.matcher(appName).matches()) {
            throw new IllegalArgumentException("应用名只允许字母、数字、点、下划线、短横线: " + appName);
        }
        return appName;
    }

    public List<String> parseInstances(String value) {
        if (value == null || value.trim().isEmpty()) {
            return Collections.emptyList();
        }
        List<String> instances = new ArrayList<String>();
        for (String part : value.split(",")) {
            String instance = part.trim();
            if (instance.isEmpty()) {
                continue;
            }
            validateInstance(instance);
            instances.add(instance);
        }
        return instances;
    }

    private void validateInstance(String instance) {
        int index = instance.lastIndexOf(':');
        if (index <= 0 || index == instance.length() - 1) {
            throw new IllegalArgumentException("实例格式应为 host:port: " + instance);
        }
        String host = instance.substring(0, index).trim();
        String portText = instance.substring(index + 1).trim();
        if (host.isEmpty()) {
            throw new IllegalArgumentException("实例 host 不能为空: " + instance);
        }
        int port;
        try {
            port = Integer.parseInt(portText);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("实例端口不是数字: " + instance);
        }
        if (port <= 0 || port > 65535) {
            throw new IllegalArgumentException("实例端口必须在 1~65535: " + instance);
        }
    }

    private String updateBy() {
        String ip = alertContext.getInstanceIp();
        return ip == null || "unknown".equals(ip) ? "console" : ip;
    }

    private static String valueOf(List<AlertConfigItemView> items, String key) {
        for (AlertConfigItemView item : items) {
            if (key.equals(item.getKey())) {
                return item.getValue();
            }
        }
        return null;
    }

    private static boolean parseBool(String value) {
        String text = value == null ? "" : value.trim();
        if ("true".equalsIgnoreCase(text) || "false".equalsIgnoreCase(text)) {
            return Boolean.parseBoolean(text);
        }
        throw new IllegalArgumentException("布尔值只能是 true/false: " + value);
    }

    private static int positiveInt(String value) {
        int parsed = Integer.parseInt(value.trim());
        if (parsed <= 0) {
            throw new IllegalArgumentException("必须大于 0: " + value);
        }
        return parsed;
    }

    private static int nonNegativeInt(String value) {
        int parsed = Integer.parseInt(value.trim());
        if (parsed < 0) {
            throw new IllegalArgumentException("不能小于 0: " + value);
        }
        return parsed;
    }

    private static long positiveLong(String value) {
        long parsed = Long.parseLong(value.trim());
        if (parsed <= 0L) {
            throw new IllegalArgumentException("必须大于 0: " + value);
        }
        return parsed;
    }

    private static final class ConfigRow {
        private final String key;
        private final String value;
        private final String type;
        private final String group;
        private final String description;
        private final boolean editable;
        private final long version;

        private ConfigRow(String key, String value, String type, String group,
                          String description, boolean editable, long version) {
            this.key = key;
            this.value = value;
            this.type = type;
            this.group = group;
            this.description = description;
            this.editable = editable;
            this.version = version;
        }
    }

    private static final class ConfigRowMapper implements RowMapper<ConfigRow> {
        @Override
        public ConfigRow mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new ConfigRow(
                    rs.getString("config_key"),
                    rs.getString("config_value"),
                    rs.getString("value_type"),
                    rs.getString("config_group"),
                    rs.getString("description"),
                    rs.getBoolean("editable"),
                    rs.getLong("version"));
        }
    }
}
