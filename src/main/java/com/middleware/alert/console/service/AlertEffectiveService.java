package com.middleware.alert.console.service;

import com.middleware.alert.config.AlertConfigLoader;
import com.middleware.alert.config.AlertConfigProvider;
import com.middleware.alert.console.model.EffectiveView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * 读取某个实例的配置生效值。
 *
 * <ul>
 *   <li>本机（instance 为空或等于当前实例地址）：直接读内存快照；</li>
 *   <li>其他实例：调用对方的 {@code GET /alert/effective}（由 alert-starter 提供），
 *       超时 1s/3s，失败时返回 reachable=false 并带原因。</li>
 * </ul>
 *
 * <p>只访问 {@code console.instances} 中登记的地址，不接受任意 URL。</p>
 */
public class AlertEffectiveService {

    private static final Logger log = LoggerFactory.getLogger(AlertEffectiveService.class);
    private static final DateTimeFormatter DATE_TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final AlertConsoleService consoleService;
    private final AlertConfigProvider configProvider;
    private final ObjectProvider<AlertConfigLoader> loaderProvider;
    private final RestTemplate restTemplate;

    public AlertEffectiveService(AlertConsoleService consoleService,
                                 AlertConfigProvider configProvider,
                                 ObjectProvider<AlertConfigLoader> loaderProvider,
                                 RestTemplate restTemplate) {
        this.consoleService = consoleService;
        this.configProvider = configProvider;
        this.loaderProvider = loaderProvider;
        this.restTemplate = restTemplate;
    }

    public EffectiveView effective(String appName, String instance, String currentInstance) {
        String target = normalizeApp(appName);
        EffectiveView view = new EffectiveView();
        view.setAppName(target);
        view.setInstance(instance);

        boolean local = isBlank(instance) || instance.trim().equals(currentInstance);
        if (local) {
            if (!target.equals(consoleService.currentAppName())) {
                view.setReachable(false);
                view.setMessage("本应用不是当前服务，无法读取本机生效值");
                return view;
            }
            AlertConfigLoader loader = loaderProvider.getIfAvailable();
            view.setReachable(true);
            view.setVersion(loader == null ? 0L : loader.getLoadedVersion());
            view.setLoadedAt(format(loader == null ? 0L : loader.getLoadedAt()));
            view.setEffective(configProvider.get().asMap());
            return view;
        }

        try {
            ResponseEntity<Map> response = restTemplate.getForEntity(
                    "http://" + instance.trim() + "/alert/effective", Map.class);
            Map body = response.getBody();
            if (body == null || !Boolean.TRUE.equals(body.get("success"))) {
                view.setReachable(false);
                view.setMessage(body == null ? "空响应" : String.valueOf(body.get("message")));
                return view;
            }
            view.setReachable(true);
            view.setVersion(toLong(body.get("version")));
            view.setLoadedAt(format(toLong(body.get("loadedAt"))));
            Object effective = body.get("effective");
            if (effective instanceof Map) {
                //noinspection unchecked
                view.setEffective((Map<String, Object>) effective);
            }
            return view;
        } catch (Exception e) {
            log.warn("alert console effective fetch failed, instance={}: {}",
                    instance, e.getMessage());
            view.setReachable(false);
            view.setMessage(e.getMessage());
            return view;
        }
    }

    private static String normalizeApp(String appName) {
        if (appName == null || appName.trim().isEmpty()) {
            throw new IllegalArgumentException("appName 不能为空");
        }
        return appName.trim();
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static long toLong(Object value) {
        return value instanceof Number ? ((Number) value).longValue() : 0L;
    }

    private static String format(long millis) {
        if (millis <= 0L) {
            return null;
        }
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneId.systemDefault())
                .format(DATE_TIME);
    }
}
