package com.middleware.alert.console.service;

import com.middleware.alert.config.AlertConfigRefreshResult;
import com.middleware.alert.console.model.RefreshResult;
import com.middleware.alert.console.model.RefreshSummary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 本地刷新 + 按 console.instances 逐个实例刷新。
 *
 * <p>只访问配置表中声明的地址，不接受请求参数传入任意 URL。</p>
 */
public class AlertInstanceRefresher {

    private static final Logger log = LoggerFactory.getLogger(AlertInstanceRefresher.class);

    private final AlertConsoleService service;
    private final RestTemplate restTemplate;

    public AlertInstanceRefresher(AlertConsoleService service, RestTemplate restTemplate) {
        this.service = service;
        this.restTemplate = restTemplate;
    }

    /**
     * @param appName         目标服务
     * @param currentInstance 当前请求所在实例，格式 host:port
     * @param currentAppName  当前实例所属应用
     */
    public RefreshSummary refresh(String appName, String currentInstance, String currentAppName) {
        RefreshSummary summary = new RefreshSummary();
        summary.setAppName(appName);

        List<RefreshResult> results = new ArrayList<RefreshResult>();
        boolean targetIsCurrent = currentAppName != null && currentAppName.equals(appName);

        if (targetIsCurrent) {
            long start = System.currentTimeMillis();
            AlertConfigRefreshResult local = service.refreshLocal();
            long localCost = System.currentTimeMillis() - start;
            if (local.isSuccess()) {
                results.add(RefreshResult.success(currentInstance, localCost, local.getMessage()));
            } else {
                results.add(RefreshResult.failure(currentInstance, localCost, local.getMessage()));
            }
        }

        List<String> instances = service.getInstances(appName);
        if (instances.isEmpty() && !targetIsCurrent) {
            results.add(RefreshResult.failure(appName, 0L,
                    "目标服务未配置 console.instances，无法远程刷新，只能重启"));
        }

        for (String instance : instances) {
            if (targetIsCurrent && currentInstance != null && currentInstance.equals(instance)) {
                continue;
            }
            results.add(refreshRemote(instance));
        }

        int success = 0;
        for (RefreshResult result : results) {
            if (result.isSuccess()) {
                success++;
            }
        }
        summary.setResults(results);
        summary.setTotal(results.size());
        summary.setSuccess(success);
        summary.setFailed(results.size() - success);
        return summary;
    }

    @SuppressWarnings("unchecked")
    private RefreshResult refreshRemote(String instance) {
        long start = System.currentTimeMillis();
        String url = "http://" + instance + "/alert/refresh/local";
        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(url, null, Map.class);
            long cost = System.currentTimeMillis() - start;
            Map body = response.getBody();
            boolean ok = response.getStatusCode().is2xxSuccessful()
                    && body != null
                    && Boolean.TRUE.equals(body.get("success"));
            String message = body == null ? "" : String.valueOf(body.get("message"));
            return ok
                    ? RefreshResult.success(instance, cost, message)
                    : RefreshResult.failure(instance, cost, message);
        } catch (Exception e) {
            long cost = System.currentTimeMillis() - start;
            log.warn("alert console refresh instance failed, instance={}, error={}",
                    instance, e.getMessage());
            return RefreshResult.failure(instance, cost, e.getMessage());
        }
    }
}
