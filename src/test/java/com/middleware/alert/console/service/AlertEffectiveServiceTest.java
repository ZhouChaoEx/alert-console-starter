package com.middleware.alert.console.service;

import com.middleware.alert.config.AlertConfigLoader;
import com.middleware.alert.config.AlertConfigProvider;
import com.middleware.alert.config.AlertProperties;
import com.middleware.alert.config.AlertRuntimeConfig;
import com.middleware.alert.console.model.EffectiveView;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AlertEffectiveServiceTest {

    private final AlertConsoleService consoleService = mock(AlertConsoleService.class);
    private final AlertConfigProvider configProvider = new AlertConfigProvider(
            AlertRuntimeConfig.from(new AlertProperties()));
    private final ObjectProvider<AlertConfigLoader> loaderProvider = mock(ObjectProvider.class);
    private final RestTemplate restTemplate = mock(RestTemplate.class);

    @Test
    void localReadsSnapshotWhenInstanceBlank() {
        AlertConfigLoader loader = mock(AlertConfigLoader.class);
        when(loader.getLoadedVersion()).thenReturn(12L);
        when(loader.getLoadedAt()).thenReturn(1_700_000_000_000L);
        when(loaderProvider.getIfAvailable()).thenReturn(loader);
        when(consoleService.currentAppName()).thenReturn("console-it");

        EffectiveView view = service().effective("console-it", null, "127.0.0.1:10009");

        assertThat(view.isReachable()).isTrue();
        assertThat(view.getVersion()).isEqualTo(12L);
        assertThat(view.getLoadedAt()).isNotBlank();
        assertThat(view.getEffective()).containsKey("runtime.enabled");
        assertThat(view.getEffective()).containsKey("batch.size");
    }

    @Test
    void localReadsSnapshotWhenInstanceEqualsCurrent() {
        AlertConfigLoader loader = mock(AlertConfigLoader.class);
        when(loader.getLoadedVersion()).thenReturn(3L);
        when(loaderProvider.getIfAvailable()).thenReturn(loader);
        when(consoleService.currentAppName()).thenReturn("console-it");

        EffectiveView view = service().effective("console-it", "127.0.0.1:10009", "127.0.0.1:10009");

        assertThat(view.isReachable()).isTrue();
        assertThat(view.getVersion()).isEqualTo(3L);
    }

    @Test
    void localIsNotReachableWhenAppIsNotCurrent() {
        when(consoleService.currentAppName()).thenReturn("console-it");

        EffectiveView view = service().effective("other-app", null, "127.0.0.1:10009");

        assertThat(view.isReachable()).isFalse();
        assertThat(view.getMessage()).contains("不是当前服务");
    }

    @Test
    void remoteFetchesEffectiveFromInstance() {
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("success", true);
        body.put("version", 16);
        body.put("loadedAt", 1_700_000_100_000L);
        body.put("effective", Collections.singletonMap("batch.size", 80));
        when(restTemplate.getForEntity(anyString(), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(body));

        EffectiveView view = service().effective("console-it", "10.0.0.1:10009", "127.0.0.1:10009");

        assertThat(view.isReachable()).isTrue();
        assertThat(view.getVersion()).isEqualTo(16L);
        assertThat(view.getLoadedAt()).isNotBlank();
        assertThat(view.getEffective()).containsEntry("batch.size", 80);
    }

    @Test
    void remoteIsNotReachableWhenCallFails() {
        when(restTemplate.getForEntity(anyString(), eq(Map.class)))
                .thenThrow(new RuntimeException("connect timed out"));

        EffectiveView view = service().effective("console-it", "10.0.0.1:10009", "127.0.0.1:10009");

        assertThat(view.isReachable()).isFalse();
        assertThat(view.getMessage()).contains("timed out");
    }

    @Test
    void emptyAppNameIsRejected() {
        org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> service().effective("  ", null, "127.0.0.1:10009"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private AlertEffectiveService service() {
        return new AlertEffectiveService(consoleService, configProvider, loaderProvider, restTemplate);
    }
}
