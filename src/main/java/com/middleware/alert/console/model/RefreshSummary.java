package com.middleware.alert.console.model;

import java.util.ArrayList;
import java.util.List;

/**
 * 多实例刷新汇总。
 */
public class RefreshSummary {

    private String appName;
    private int total;
    private int success;
    private int failed;
    private List<RefreshResult> results = new ArrayList<RefreshResult>();

    public String getAppName() {
        return appName;
    }

    public void setAppName(String appName) {
        this.appName = appName;
    }

    public int getTotal() {
        return total;
    }

    public void setTotal(int total) {
        this.total = total;
    }

    public int getSuccess() {
        return success;
    }

    public void setSuccess(int success) {
        this.success = success;
    }

    public int getFailed() {
        return failed;
    }

    public void setFailed(int failed) {
        this.failed = failed;
    }

    public List<RefreshResult> getResults() {
        return results;
    }

    public void setResults(List<RefreshResult> results) {
        this.results = results;
    }
}
