package com.middleware.alert.console.model;

import java.util.ArrayList;
import java.util.List;

/**
 * 大屏聚合数据。
 */
public class DashboardView {

    private String range;
    private String generatedAt;
    private Summary summary = new Summary();
    private List<TrendPoint> trend = new ArrayList<TrendPoint>();
    private List<TypeCount> types = new ArrayList<TypeCount>();
    private List<ServiceCount> services = new ArrayList<ServiceCount>();
    private List<AlertRecordView> latest = new ArrayList<AlertRecordView>();
    private List<SlowUri> slowRequests = new ArrayList<SlowUri>();
    private Resources resources = new Resources();

    public String getRange() {
        return range;
    }

    public void setRange(String range) {
        this.range = range;
    }

    public String getGeneratedAt() {
        return generatedAt;
    }

    public void setGeneratedAt(String generatedAt) {
        this.generatedAt = generatedAt;
    }

    public Summary getSummary() {
        return summary;
    }

    public void setSummary(Summary summary) {
        this.summary = summary;
    }

    public List<TrendPoint> getTrend() {
        return trend;
    }

    public void setTrend(List<TrendPoint> trend) {
        this.trend = trend;
    }

    public List<TypeCount> getTypes() {
        return types;
    }

    public void setTypes(List<TypeCount> types) {
        this.types = types;
    }

    public List<ServiceCount> getServices() {
        return services;
    }

    public void setServices(List<ServiceCount> services) {
        this.services = services;
    }

    public List<AlertRecordView> getLatest() {
        return latest;
    }

    public void setLatest(List<AlertRecordView> latest) {
        this.latest = latest;
    }

    public List<SlowUri> getSlowRequests() {
        return slowRequests;
    }

    public void setSlowRequests(List<SlowUri> slowRequests) {
        this.slowRequests = slowRequests;
    }

    public Resources getResources() {
        return resources;
    }

    public void setResources(Resources resources) {
        this.resources = resources;
    }

    public static class Summary {
        private long total;
        private long critical;
        private long error;
        private long warn;
        private long affectedServices;
        private long lastHour;
        private long avgCostMs;

        public long getTotal() {
            return total;
        }

        public void setTotal(long total) {
            this.total = total;
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

        public long getAffectedServices() {
            return affectedServices;
        }

        public void setAffectedServices(long affectedServices) {
            this.affectedServices = affectedServices;
        }

        public long getLastHour() {
            return lastHour;
        }

        public void setLastHour(long lastHour) {
            this.lastHour = lastHour;
        }

        public long getAvgCostMs() {
            return avgCostMs;
        }

        public void setAvgCostMs(long avgCostMs) {
            this.avgCostMs = avgCostMs;
        }
    }

    public static class TrendPoint {
        private String bucket;
        private long critical;
        private long error;
        private long warn;
        private long total;

        public String getBucket() {
            return bucket;
        }

        public void setBucket(String bucket) {
            this.bucket = bucket;
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

        public long getTotal() {
            return total;
        }

        public void setTotal(long total) {
            this.total = total;
        }
    }

    public static class TypeCount {
        private String type;
        private long count;

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public long getCount() {
            return count;
        }

        public void setCount(long count) {
            this.count = count;
        }
    }

    public static class ServiceCount {
        private String appName;
        private long total;
        private long critical;

        public String getAppName() {
            return appName;
        }

        public void setAppName(String appName) {
            this.appName = appName;
        }

        public long getTotal() {
            return total;
        }

        public void setTotal(long total) {
            this.total = total;
        }

        public long getCritical() {
            return critical;
        }

        public void setCritical(long critical) {
            this.critical = critical;
        }
    }

    public static class SlowUri {
        private String uri;
        private long count;
        private long maxCostMs;

        public String getUri() {
            return uri;
        }

        public void setUri(String uri) {
            this.uri = uri;
        }

        public long getCount() {
            return count;
        }

        public void setCount(long count) {
            this.count = count;
        }

        public long getMaxCostMs() {
            return maxCostMs;
        }

        public void setMaxCostMs(long maxCostMs) {
            this.maxCostMs = maxCostMs;
        }
    }

    public static class Resources {
        private boolean available;
        private String message;

        public boolean isAvailable() {
            return available;
        }

        public void setAvailable(boolean available) {
            this.available = available;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }
    }
}
