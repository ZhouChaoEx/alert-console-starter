package com.middleware.alert.console.service;

import com.middleware.alert.config.AlertConfigRefreshResult;
import com.middleware.alert.console.model.RefreshSummary;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AlertInstanceRefresherTest {

    private final AlertConsoleService service = mock(AlertConsoleService.class);
    private final RestTemplate restTemplate = mock(RestTemplate.class);
    private final AlertInstanceRefresher refresher =
            new AlertInstanceRefresher(service, restTemplate);

    @Test
    void otherAppOnlyRefreshesRemoteInstances() {
        when(service.getInstances("other-app"))
                .thenReturn(Collections.singletonList("10.0.0.1:10009"));
        when(restTemplate.postForEntity(anyString(), isNull(), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(okBody()));

        RefreshSummary summary = refresher.refresh(
                "other-app", "127.0.0.1:10080", "console-it");

        assertThat(summary.getTotal()).isEqualTo(1);
        assertThat(summary.getSuccess()).isEqualTo(1);
        verify(service, never()).refreshLocal();
        verify(restTemplate).postForEntity(
                eq("http://10.0.0.1:10009/alert/refresh/local"),
                isNull(), eq(Map.class));
    }

    @Test
    void currentAppRefreshesLocalAndRemote() {
        when(service.getInstances("console-it"))
                .thenReturn(Collections.singletonList("10.0.0.1:10009"));
        when(service.refreshLocal()).thenReturn(
                AlertConfigRefreshResult.success(1L, Collections.<String>emptyList()));
        when(restTemplate.postForEntity(anyString(), isNull(), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(okBody()));

        RefreshSummary summary = refresher.refresh(
                "console-it", "127.0.0.1:10080", "console-it");

        assertThat(summary.getTotal()).isEqualTo(2);
        assertThat(summary.getSuccess()).isEqualTo(2);
        verify(service).refreshLocal();
        verify(restTemplate).postForEntity(anyString(), isNull(), eq(Map.class));
    }

    @Test
    void otherAppWithoutInstancesReturnsFailure() {
        when(service.getInstances("other-app")).thenReturn(Collections.emptyList());

        RefreshSummary summary = refresher.refresh(
                "other-app", "127.0.0.1:10080", "console-it");

        assertThat(summary.getTotal()).isEqualTo(1);
        assertThat(summary.getSuccess()).isZero();
        assertThat(summary.getResults().get(0).getMessage()).contains("console.instances");
        verify(service, never()).refreshLocal();
    }

    private static Map<String, Object> okBody() {
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("success", true);
        body.put("message", "ok");
        return body;
    }
}
