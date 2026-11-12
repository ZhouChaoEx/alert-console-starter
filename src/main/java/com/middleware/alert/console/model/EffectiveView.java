package com.middleware.alert.console.model;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 某个实例的配置生效值。
 *
 * <p>用于"在当前服务下查看指定实例生效值"：本机直接读内存快照，其他实例通过
 * {@code GET /alert/effective} 远程拉取。</p>
 */
public class EffectiveView {

    private String appName;
    /** 查询的实例地址；空表示本机 */
    private String instance;
    /** 是否取到了数据（本机可读或远程可达） */
    private boolean reachable;
    /** 未取到时的原因 */
    private String message;
    /** 该实例最近一次加载的配置版本 */
    private long version;
    /** 该实例最近一次加载时间，yyyy-MM-dd HH:mm:ss */
    private String loadedAt;
    /** key -> 生效值 */
    private Map<String, Object> effective = new LinkedHashMap<String, Object>();

    public String getAppName() {
        return appName;
    }

    public void setAppName(String appName) {
        this.appName = appName;
    }

    public String getInstance() {
        return instance;
    }

    public void setInstance(String instance) {
        this.instance = instance;
    }

    public boolean isReachable() {
        return reachable;
    }

    public void setReachable(boolean reachable) {
        this.reachable = reachable;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public long getVersion() {
        return version;
    }

    public void setVersion(long version) {
        this.version = version;
    }

    public String getLoadedAt() {
        return loadedAt;
    }

    public void setLoadedAt(String loadedAt) {
        this.loadedAt = loadedAt;
    }

    public Map<String, Object> getEffective() {
        return effective;
    }

    public void setEffective(Map<String, Object> effective) {
        this.effective = effective == null
                ? new LinkedHashMap<String, Object>()
                : new LinkedHashMap<String, Object>(effective);
    }
}
