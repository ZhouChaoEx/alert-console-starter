package com.middleware.alert.console.model;

import java.util.ArrayList;
import java.util.List;

/**
 * 服务列表项。
 */
public class ServiceView {

    private String appName;
    private int instanceCount;
    private List<String> instances = new ArrayList<String>();
    private long version;
    private boolean initialized;
    private long alertCount;
    private String lastAlertTime;
    private boolean currentApp;
    private String status;

    public String getAppName() {
        return appName;
    }

    public void setAppName(String appName) {
        this.appName = appName;
    }

    public int getInstanceCount() {
        return instanceCount;
    }

    public void setInstanceCount(int instanceCount) {
        this.instanceCount = instanceCount;
    }

    public List<String> getInstances() {
        return instances;
    }

    public void setInstances(List<String> instances) {
        this.instances = instances;
    }

    public long getVersion() {
        return version;
    }

    public void setVersion(long version) {
        this.version = version;
    }

    public boolean isInitialized() {
        return initialized;
    }

    public void setInitialized(boolean initialized) {
        this.initialized = initialized;
    }

    public long getAlertCount() {
        return alertCount;
    }

    public void setAlertCount(long alertCount) {
        this.alertCount = alertCount;
    }

    public String getLastAlertTime() {
        return lastAlertTime;
    }

    public void setLastAlertTime(String lastAlertTime) {
        this.lastAlertTime = lastAlertTime;
    }

    public boolean isCurrentApp() {
        return currentApp;
    }

    public void setCurrentApp(boolean currentApp) {
        this.currentApp = currentApp;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
