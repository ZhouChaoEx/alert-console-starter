package com.middleware.alert.console.web;

import com.middleware.alert.console.model.AlertConfigView;
import com.middleware.alert.console.model.AlertPageView;
import com.middleware.alert.console.model.AlertQuery;
import com.middleware.alert.console.model.AlertRecordView;
import com.middleware.alert.console.model.ApiResponse;
import com.middleware.alert.console.model.CreateAppRequest;
import com.middleware.alert.console.model.DashboardView;
import com.middleware.alert.console.model.EffectiveView;
import com.middleware.alert.console.model.RefreshSummary;
import com.middleware.alert.console.model.SaveConfigRequest;
import com.middleware.alert.console.model.ServiceView;
import com.middleware.alert.console.service.AlertConsoleException;
import com.middleware.alert.console.service.AlertConsoleService;
import com.middleware.alert.console.service.AlertEffectiveService;
import com.middleware.alert.console.service.AlertInstanceRefresher;
import com.middleware.alert.console.service.AlertQueryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 管理页面 API。复用业务应用端口，路径前缀 /alert-console。
 *
 * <p>异常处理只作用于本 Controller，不注册全局 Advice，避免影响宿主应用。</p>
 */
@RestController
@RequestMapping("/alert-console")
public class AlertConsoleController {

    private static final Logger log = LoggerFactory.getLogger(AlertConsoleController.class);

    private final AlertConsoleService service;
    private final AlertInstanceRefresher refresher;
    private final AlertQueryService queryService;
    private final AlertEffectiveService effectiveService;

    public AlertConsoleController(AlertConsoleService service,
                                  AlertInstanceRefresher refresher,
                                  AlertQueryService queryService,
                                  AlertEffectiveService effectiveService) {
        this.service = service;
        this.refresher = refresher;
        this.queryService = queryService;
        this.effectiveService = effectiveService;
    }

    @GetMapping("/api/apps")
    public ApiResponse<List<String>> apps() {
        return ApiResponse.ok(service.listApps());
    }

    @GetMapping("/api/services")
    public ApiResponse<List<ServiceView>> services() {
        return ApiResponse.ok(queryService.listServices(service.currentAppName()));
    }

    @GetMapping("/api/config")
    public ApiResponse<AlertConfigView> config(
            @RequestParam(value = "appName", required = false) String appName) {
        return ApiResponse.ok(service.getConfig(appName));
    }

    @PostMapping("/api/apps")
    public ApiResponse<AlertConfigView> createApp(@RequestBody CreateAppRequest request) {
        return ApiResponse.ok(service.createApp(request == null ? null : request.getAppName()));
    }

    @PutMapping("/api/config")
    public ApiResponse<AlertConfigView> save(@RequestBody SaveConfigRequest request) {
        return ApiResponse.ok(service.save(request));
    }

    @GetMapping("/api/alerts")
    public ApiResponse<AlertPageView> alerts(
            @RequestParam(value = "appNames", required = false) String appNames,
            @RequestParam(value = "type", required = false) String type,
            @RequestParam(value = "level", required = false) String level,
            @RequestParam(value = "range", required = false, defaultValue = "24h") String range,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "page", required = false, defaultValue = "1") int page,
            @RequestParam(value = "size", required = false, defaultValue = "50") int size) {
        AlertQuery query = new AlertQuery();
        query.setAppNames(splitAppNames(appNames));
        query.setAlertType(type);
        query.setAlertLevel(level);
        query.setRange(range);
        query.setKeyword(keyword);
        query.setPage(page);
        query.setSize(size);
        return ApiResponse.ok(queryService.queryAlerts(query));
    }

    @GetMapping("/api/alerts/{id}")
    public ApiResponse<AlertRecordView> alert(@PathVariable("id") long id) {
        AlertRecordView view = queryService.getAlert(id);
        if (view == null) {
            throw new AlertConsoleException("404", "告警不存在: " + id);
        }
        return ApiResponse.ok(view);
    }

    @GetMapping("/api/dashboard")
    public ApiResponse<DashboardView> dashboard(
            @RequestParam(value = "range", required = false, defaultValue = "24h") String range) {
        return ApiResponse.ok(queryService.dashboard(range));
    }

    @PostMapping("/api/refresh")
    public ApiResponse<RefreshSummary> refresh(@RequestParam("appName") String appName,
                                               HttpServletRequest request) {
        String current = request.getLocalAddr() + ":" + request.getLocalPort();
        return ApiResponse.ok(refresher.refresh(appName, current, service.currentAppName()));
    }

    /**
     * 查看某个实例的配置生效值：本机直接读快照，其他实例远程拉取（仅限 console.instances 登记的地址）。
     */
    @GetMapping("/api/config/effective")
    public ApiResponse<EffectiveView> effective(
            @RequestParam("appName") String appName,
            @RequestParam(value = "instance", required = false) String instance,
            HttpServletRequest request) {
        String current = request.getLocalAddr() + ":" + request.getLocalPort();
        return ApiResponse.ok(effectiveService.effective(appName, instance, current));
    }

    @GetMapping("/api/health")
    public ApiResponse<Map<String, Object>> health() {
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("appName", service.currentAppName());
        data.put("time", System.currentTimeMillis());
        return ApiResponse.ok(data);
    }

    @ExceptionHandler(AlertConsoleException.class)
    public ApiResponse<Void> handleConsoleException(AlertConsoleException e) {
        return ApiResponse.fail(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ApiResponse<Void> handleIllegalArgument(IllegalArgumentException e) {
        return ApiResponse.fail("400", e.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ApiResponse<Void> handleOther(Exception e) {
        log.warn("alert console request failed", e);
        return ApiResponse.fail("500", e.getMessage());
    }

    private static List<String> splitAppNames(String appNames) {
        List<String> result = new ArrayList<String>();
        if (appNames == null || appNames.trim().isEmpty()) {
            return result;
        }
        for (String part : appNames.split(",")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                result.add(trimmed);
            }
        }
        return result;
    }
}
