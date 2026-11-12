package com.middleware.alert.console.service;

/**
 * 管理页面业务异常。
 */
public class AlertConsoleException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String code;

    public AlertConsoleException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
