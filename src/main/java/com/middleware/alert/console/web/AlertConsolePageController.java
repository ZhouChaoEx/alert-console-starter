package com.middleware.alert.console.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * 管理页面入口。
 *
 * <p>必须使用 {@link Controller} 而不是 {@code @RestController}：
 * {@code @RestController} 会把返回的 "forward:..." 当成普通字符串写入响应体，
 * 不会触发 Servlet forward。</p>
 */
@Controller
@RequestMapping("/alert-console")
public class AlertConsolePageController {

    @GetMapping({"", "/"})
    public String index() {
        return "forward:/alert-console/index.html";
    }
}
