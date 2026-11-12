package com.middleware.alert.console.model;

import java.util.ArrayList;
import java.util.List;

/**
 * 某个应用的完整配置视图。
 */
public class AlertConfigView {

    private String appName;
    private boolean initialized;
    private long version;
    private boolean currentApp;
    private List<AlertConfigItemView> items = new ArrayList<AlertConfigItemView>();
    private List<String> instances = new ArrayList<String>();

    public String getAppName() {
        return appName;
    }

    public void setAppName(String appName) {
        this.appName = appName;
    }

    public boolean isInitialized() {
        return initialized;
    }

    public void setInitialized(boolean initialized) {
        this.initialized = initialized;
    }

    public long getVersion() {
        return version;
    }

    public void setVersion(long version) {
        this.version = version;
    }

    public boolean isCurrentApp() {
        return currentApp;
    }

    public void setCurrentApp(boolean currentApp) {
        this.currentApp = currentApp;
    }

    public List<AlertConfigItemView> getItems() {
        return items;
    }

    public void setItems(List<AlertConfigItemView> items) {
        this.items = items;
    }

    public List<String> getInstances() {
        return instances;
    }

    public void setInstances(List<String> instances) {
        this.instances = instances;
    }
}
