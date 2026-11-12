package com.middleware.alert.console.model;

/**
 * 单个实例刷新结果。
 */
public class RefreshResult {

    private String instance;
    private boolean success;
    private long costMs;
    private String message;

    public static RefreshResult success(String instance, long costMs, String message) {
        RefreshResult result = new RefreshResult();
        result.instance = instance;
        result.success = true;
        result.costMs = costMs;
        result.message = message;
        return result;
    }

    public static RefreshResult failure(String instance, long costMs, String message) {
        RefreshResult result = new RefreshResult();
        result.instance = instance;
        result.success = false;
        result.costMs = costMs;
        result.message = message;
        return result;
    }

    public String getInstance() {
        return instance;
    }

    public void setInstance(String instance) {
        this.instance = instance;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public long getCostMs() {
        return costMs;
    }

    public void setCostMs(long costMs) {
        this.costMs = costMs;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
