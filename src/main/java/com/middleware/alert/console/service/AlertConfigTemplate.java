package com.middleware.alert.console.service;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 新应用初始化模板，与 db/{kingbase,mysql}/alert_config.sql 的 35 项默认配置保持一致。
 */
public final class AlertConfigTemplate {

    public static final List<Entry> ENTRIES = Collections.unmodifiableList(Arrays.asList(
            new Entry("runtime.enabled", "true", "BOOL", "CONSOLE", "运行时总开关", false),
            new Entry("write-mode", "ASYNC", "STRING", "WRITE", "写入模式：ASYNC/SYNC", false),
            new Entry("queue.capacity", "10000", "INT", "WRITE", "有界队列容量（需重启生效）", true),
            new Entry("batch.size", "50", "INT", "WRITE", "单批最大条数", false),
            new Entry("batch.interval-ms", "1000", "LONG", "WRITE", "批量等待间隔（毫秒）", false),
            new Entry("stack-trace.max-length", "8192", "INT", "ERROR", "堆栈最大字符数", false),
            new Entry("dedup.window-seconds", "300", "INT", "DEDUP", "指纹去重窗口（秒）", false),
            new Entry("dedup.max-entries", "10000", "INT", "DEDUP", "去重指纹条目上限", false),
            new Entry("rate-limit.per-minute", "20", "INT", "RATE_LIMIT", "每个告警类型每分钟上限", false),
            new Entry("rate-limit.sample-factor", "10", "INT", "RATE_LIMIT", "超限后每 N 条放行 1 条", false),
            new Entry("biz.enabled", "true", "BOOL", "BIZ", "是否上报 AlertableException", false),
            new Entry("error.npe.enabled", "true", "BOOL", "ERROR", "是否上报 NullPointerException", false),
            new Entry("error.npe.sample-rate", "1.0", "DOUBLE", "ERROR", "NPE 采样率 0~1", false),
            new Entry("error.stackoverflow.enabled", "true", "BOOL", "ERROR", "是否上报 StackOverflowError", false),
            new Entry("slow-request.enabled", "true", "BOOL", "SLOW_REQUEST", "是否采集慢请求", false),
            new Entry("slow-request.threshold-ms", "1000", "LONG", "SLOW_REQUEST", "慢请求阈值（毫秒）", false),
            new Entry("slow-request.ignore-paths",
                    "/actuator/**,/health,/favicon.ico,/swagger-ui/**,/v2/api-docs,/webjars/**",
                    "LIST", "SLOW_REQUEST", "慢请求忽略路径", false),
            new Entry("async.enabled", "false", "BOOL", "ASYNC",
                    "是否采集 @Async 未捕获异常（默认关闭）", false),
            new Entry("async.args-types", "true", "BOOL", "ASYNC",
                    "是否记录异步方法参数类型列表（不记值）", false),
            new Entry("slow-sql.enabled", "false", "BOOL", "SLOW_SQL",
                    "是否采集慢 SQL（默认关闭）", false),
            new Entry("slow-sql.threshold-ms", "500", "LONG", "SLOW_SQL",
                    "慢 SQL 阈值（毫秒）", false),
            new Entry("slow-sql.max-sql-length", "2048", "INT", "SLOW_SQL",
                    "SQL 文本最大字符数", false),
            new Entry("slow-sql.exclude-mapper-prefixes", "", "LIST", "SLOW_SQL",
                    "排除的 mapper 前缀，逗号分隔", false),
            new Entry("slow-sql.aggregate-window-seconds", "60", "INT", "SLOW_SQL",
                    "同指纹聚合窗口（秒），0 表示不聚合", false),
            new Entry("memory.enabled", "false", "BOOL", "MEMORY",
                    "是否采集内存/OOM 前兆（默认关闭）", false),
            new Entry("memory.interval-ms", "60000", "LONG", "MEMORY",
                    "内存采样间隔（毫秒）", false),
            new Entry("memory.old-gen-ratio", "0.85", "DOUBLE", "MEMORY",
                    "老年代 GC 后使用率阈值 0~1", false),
            new Entry("memory.consecutive-samples", "3", "INT", "MEMORY",
                    "连续采样次数：堆使用率持续上升且未发生 GC 时触发", false),
            new Entry("http.outbound.enabled", "false", "BOOL", "HTTP_OUTBOUND",
                    "是否采集出站 HTTP 异常（默认关闭）", false),
            new Entry("http.outbound.threshold-ms", "3000", "LONG", "HTTP_OUTBOUND",
                    "出站调用慢阈值（毫秒）", false),
            new Entry("http.outbound.slow-enabled", "true", "BOOL", "HTTP_OUTBOUND",
                    "是否采集成功但超过阈值的慢调用", false),
            new Entry("http.outbound.statuses", "502,503,504", "LIST", "HTTP_OUTBOUND",
                    "视为异常的下游状态码，逗号分隔", false),
            new Entry("http.outbound.exclude-hosts", "", "LIST", "HTTP_OUTBOUND",
                    "排除的主机，支持通配（如 *.internal.com）", false),
            new Entry("http.outbound.exclude-paths", "", "LIST", "HTTP_OUTBOUND",
                    "排除的路径，支持 Ant 风格（如 /actuator/**）", false),
            new Entry("console.instances", "", "LIST", "CONSOLE",
                    "管理实例列表，格式 ip:port,ip:port；为空时只刷新当前实例", false)
    ));

    private static final Map<String, Entry> INDEX;

    static {
        Map<String, Entry> index = new LinkedHashMap<String, Entry>();
        for (Entry entry : ENTRIES) {
            index.put(entry.key, entry);
        }
        INDEX = Collections.unmodifiableMap(index);
    }

    private AlertConfigTemplate() {
    }

    public static Entry find(String key) {
        return INDEX.get(key);
    }

    public static boolean contains(String key) {
        return INDEX.containsKey(key);
    }

    public static final class Entry {

        private final String key;
        private final String defaultValue;
        private final String type;
        private final String group;
        private final String description;
        private final boolean requiresRestart;

        private Entry(String key, String defaultValue, String type,
                      String group, String description, boolean requiresRestart) {
            this.key = key;
            this.defaultValue = defaultValue;
            this.type = type;
            this.group = group;
            this.description = description;
            this.requiresRestart = requiresRestart;
        }

        public String getKey() {
            return key;
        }

        public String getDefaultValue() {
            return defaultValue;
        }

        public String getType() {
            return type;
        }

        public String getGroup() {
            return group;
        }

        public String getDescription() {
            return description;
        }

        public boolean isRequiresRestart() {
            return requiresRestart;
        }
    }
}
