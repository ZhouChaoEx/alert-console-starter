package com.middleware.alert.console.it;

import com.middleware.alert.config.AlertJdbcAccess;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;

import javax.sql.DataSource;
import java.sql.Timestamp;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
        classes = ConsoleTestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
                "spring.application.name=console-it",
                "alert.enabled=true",
                "spring.autoconfigure.exclude="
                        + "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,"
                        + "org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration,"
                        + "org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration",
                "alert.datasource.url=jdbc:h2:mem:consoleit;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
                "alert.datasource.driver-class-name=org.h2.Driver",
                "alert.datasource.username=sa",
                "alert.datasource.password=",
                "alert.datasource.self-check=false",
                "alert.config.enabled=true",
                "alert.config.self-check=false",
                "alert.console.enabled=true",
                "alert.batch.interval-ms=50"
        }
)
@AutoConfigureMockMvc
@Sql(scripts = "/console-schema-h2.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@Import(AlertConsoleIntegrationTest.TestDataSourceConfig.class)
class AlertConsoleIntegrationTest {

    private static final String APP = "console-it";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    @Qualifier("alertJdbcTemplate")
    private AlertJdbcAccess alertJdbcAccess;

    private JdbcTemplate jdbcTemplate() {
        return alertJdbcAccess.jdbcTemplate();
    }

    @Test
    void pageRouteForwardsToIndex() throws Exception {
        mockMvc.perform(get("/alert-console/"))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("/alert-console/index.html"));
    }

    @Test
    void createAppAndQueryConfig() throws Exception {
        mockMvc.perform(get("/alert-console/api/apps"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0]").value(APP));

        mockMvc.perform(post("/alert-console/api/apps")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"appName\":\"" + APP + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.initialized").value(true))
                .andExpect(jsonPath("$.data.items.length()").value(35));

        mockMvc.perform(get("/alert-console/api/config").param("appName", APP))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.appName").value(APP))
                .andExpect(jsonPath("$.data.currentApp").value(true))
                .andExpect(jsonPath("$.data.version").value(1))
                .andExpect(jsonPath("$.data.items.length()").value(35));
    }

    @Test
    void saveConfigUpdatesValueAndRejectsStaleVersion() throws Exception {
        mockMvc.perform(post("/alert-console/api/apps")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"appName\":\"" + APP + "\"}"))
                .andExpect(status().isOk());

        String update = "{\"appName\":\"" + APP + "\",\"version\":1,"
                + "\"items\":[{\"key\":\"slow-request.threshold-ms\",\"value\":\"2000\",\"version\":1}]}";

        mockMvc.perform(put("/alert-console/api/config")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(update))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.version").value(2))
                .andExpect(jsonPath("$.data.items[15].key").value("slow-request.threshold-ms"))
                .andExpect(jsonPath("$.data.items[15].value").value("2000"))
                .andExpect(jsonPath("$.data.items[15].version").value(2));

        // 使用旧版本再次保存，应触发乐观锁冲突
        mockMvc.perform(put("/alert-console/api/config")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(update))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("409"));
    }

    @Test
    void invalidValueIsRejected() throws Exception {
        mockMvc.perform(post("/alert-console/api/apps")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"appName\":\"" + APP + "\"}"))
                .andExpect(status().isOk());

        String update = "{\"appName\":\"" + APP + "\",\"version\":1,"
                + "\"items\":[{\"key\":\"error.npe.sample-rate\",\"value\":\"2.0\",\"version\":1}]}";

        mockMvc.perform(put("/alert-console/api/config")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(update))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("400"));
    }

    @Test
    void refreshLocalAndAllInstances() throws Exception {
        mockMvc.perform(post("/alert-console/api/apps")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"appName\":\"" + APP + "\"}"))
                .andExpect(status().isOk());

        // 刷新端点已下沉到 alert-starter：POST /alert/refresh/local
        mockMvc.perform(post("/alert/refresh/local"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        // console.instances 默认为空，只刷新当前实例
        mockMvc.perform(post("/alert-console/api/refresh").param("appName", APP))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.success").value(1))
                .andExpect(jsonPath("$.data.failed").value(0));
    }

    @Test
    void queriesServicesAndAlertsAcrossApps() throws Exception {
        mockMvc.perform(post("/alert-console/api/apps")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"appName\":\"" + APP + "\"}"))
                .andExpect(status().isOk());

        Timestamp now = new Timestamp(System.currentTimeMillis());
        long bizId = insertAlert(APP, "BIZ_EXCEPTION", "ERROR",
                "[E100] 订单失败", "/order", 120L,
                new Timestamp(now.getTime() - 60_000L));
        long slowId = insertAlert(APP, "SLOW_REQUEST", "WARN",
                "慢请求: GET /order 1500ms", "/order", 1500L,
                new Timestamp(now.getTime() - 30_000L));
        insertAlert("other-app", "NULL_POINTER", "ERROR",
                "NPE", "/other", 10L, new Timestamp(now.getTime() - 20_000L));

        mockMvc.perform(get("/alert-console/api/services"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].appName").value(APP))
                .andExpect(jsonPath("$.data[0].currentApp").value(true))
                .andExpect(jsonPath("$.data[0].alertCount").value(2))
                .andExpect(jsonPath("$.data[1].appName").value("other-app"));

        mockMvc.perform(get("/alert-console/api/alerts")
                        .param("appNames", APP)
                        .param("range", "24h")
                        .param("page", "1")
                        .param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(2))
                .andExpect(jsonPath("$.data.totalPages").value(2))
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].id").value(slowId));

        mockMvc.perform(get("/alert-console/api/alerts")
                        .param("appNames", APP)
                        .param("type", "BIZ_EXCEPTION"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].id").value(bizId));

        mockMvc.perform(get("/alert-console/api/alerts/" + slowId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.appName").value(APP))
                .andExpect(jsonPath("$.data.title").value("慢请求: GET /order 1500ms"))
                .andExpect(jsonPath("$.data.costMs").value(1500));
    }

    @Test
    void dashboardAggregates() throws Exception {
        Timestamp now = new Timestamp(System.currentTimeMillis());
        insertAlert(APP, "BIZ_EXCEPTION", "CRITICAL", "业务异常", "/biz", 100L,
                new Timestamp(now.getTime() - 3000L));
        insertAlert(APP, "SLOW_REQUEST", "ERROR", "慢请求 A", "/slow", 3000L,
                new Timestamp(now.getTime() - 2000L));
        insertAlert(APP, "SLOW_REQUEST", "WARN", "慢请求 B", "/slow", 2000L,
                new Timestamp(now.getTime() - 1000L));

        mockMvc.perform(get("/alert-console/api/dashboard").param("range", "24h"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.summary.total").value(3))
                .andExpect(jsonPath("$.data.summary.critical").value(1))
                .andExpect(jsonPath("$.data.summary.error").value(1))
                .andExpect(jsonPath("$.data.summary.warn").value(1))
                .andExpect(jsonPath("$.data.summary.affectedServices").value(1))
                .andExpect(jsonPath("$.data.types.length()").value(2))
                .andExpect(jsonPath("$.data.services[0].appName").value(APP))
                .andExpect(jsonPath("$.data.latest.length()").value(3))
                .andExpect(jsonPath("$.data.slowRequests.length()").value(1))
                .andExpect(jsonPath("$.data.slowRequests[0].uri").value("/slow"))
                .andExpect(jsonPath("$.data.slowRequests[0].maxCostMs").value(3000))
                .andExpect(jsonPath("$.data.trend.length()").value(24));
    }

    private long insertAlert(String app, String type, String level, String title,
                             String uri, long cost, Timestamp alertTime) {
        jdbcTemplate().update(
                "INSERT INTO alert_info (app_name, alert_type, alert_level, title, request_uri, "
                        + "cost_ms, trace_id, fingerprint, repeat_count, first_time, alert_time) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                app, type, level, title, uri, cost, "trace-" + app,
                app + "|" + type + "|" + uri + "|" + alertTime.getTime(), 1,
                alertTime, alertTime);
        Long id = jdbcTemplate().queryForObject("SELECT MAX(id) FROM alert_info", Long.class);
        return id == null ? 0L : id;
    }

    /**
     * {@code @Sql} 需要容器里有一个 DataSource；告警数据源不再暴露成该类型，这里补一个业务库。
     */
    @TestConfiguration
    static class TestDataSourceConfig {

        @Bean(name = "dataSource")
        public DataSource dataSource() {
            HikariDataSource dataSource = new HikariDataSource();
            dataSource.setJdbcUrl("jdbc:h2:mem:consoleit;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
            dataSource.setUsername("sa");
            dataSource.setPassword("");
            return dataSource;
        }
    }
}
