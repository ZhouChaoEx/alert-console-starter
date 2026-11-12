package com.middleware.alert.console.model;

/**
 * 单个配置项的展示模型。
 */
public class AlertConfigItemView {

    private String key;
    private String value;
    private String type;
    private String group;
    private String description;
    private boolean editable;
    private long version;
    private Object effectiveValue;
    private boolean requiresRestart;
    private boolean missing;

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getGroup() {
        return group;
    }

    public void setGroup(String group) {
        this.group = group;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public boolean isEditable() {
        return editable;
    }

    public void setEditable(boolean editable) {
        this.editable = editable;
    }

    public long getVersion() {
        return version;
    }

    public void setVersion(long version) {
        this.version = version;
    }

    public Object getEffectiveValue() {
        return effectiveValue;
    }

    public void setEffectiveValue(Object effectiveValue) {
        this.effectiveValue = effectiveValue;
    }

    public boolean isRequiresRestart() {
        return requiresRestart;
    }

    public void setRequiresRestart(boolean requiresRestart) {
        this.requiresRestart = requiresRestart;
    }

    public boolean isMissing() {
        return missing;
    }

    public void setMissing(boolean missing) {
        this.missing = missing;
    }
}
