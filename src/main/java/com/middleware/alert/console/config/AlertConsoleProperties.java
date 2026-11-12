package com.middleware.alert.console.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 管理页面开关，前缀 {@code alert.console}。
 */
@ConfigurationProperties(prefix = "alert.console")
public class AlertConsoleProperties {

    /** 是否注册管理页面与 API，默认关闭 */
    private boolean enabled = false;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
