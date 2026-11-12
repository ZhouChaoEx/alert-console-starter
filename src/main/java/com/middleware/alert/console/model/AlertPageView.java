package com.middleware.alert.console.model;

import java.util.ArrayList;
import java.util.List;

/**
 * 告警分页结果。
 */
public class AlertPageView {

    private List<AlertRecordView> items = new ArrayList<AlertRecordView>();
    private long total;
    private int page;
    private int size;
    private int totalPages;
    private long critical;
    private long error;
    private long warn;

    public List<AlertRecordView> getItems() {
        return items;
    }

    public void setItems(List<AlertRecordView> items) {
        this.items = items;
    }

    public long getTotal() {
        return total;
    }

    public void setTotal(long total) {
        this.total = total;
    }

    public int getPage() {
        return page;
    }

    public void setPage(int page) {
        this.page = page;
    }

    public int getSize() {
        return size;
    }

    public void setSize(int size) {
        this.size = size;
    }

    public int getTotalPages() {
        return totalPages;
    }

    public void setTotalPages(int totalPages) {
        this.totalPages = totalPages;
    }

    public long getCritical() {
        return critical;
    }

    public void setCritical(long critical) {
        this.critical = critical;
    }

    public long getError() {
        return error;
    }

    public void setError(long error) {
        this.error = error;
    }

    public long getWarn() {
        return warn;
    }

    public void setWarn(long warn) {
        this.warn = warn;
    }
}
