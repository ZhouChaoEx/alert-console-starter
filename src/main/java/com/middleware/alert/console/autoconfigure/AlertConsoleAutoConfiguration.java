package com.middleware.alert.console.autoconfigure;

import com.middleware.alert.autoconfigure.AlertAutoConfiguration;
import com.middleware.alert.autoconfigure.AlertPersistenceAutoConfiguration;
import com.middleware.alert.config.AlertConfigLoader;
import com.middleware.alert.config.AlertConfigProvider;
import com.middleware.alert.config.AlertJdbcAccess;
import com.middleware.alert.console.config.AlertConsoleProperties;
import com.middleware.alert.console.service.AlertConsoleService;
import com.middleware.alert.console.service.AlertEffectiveService;
import com.middleware.alert.console.service.AlertInstanceRefresher;
import com.middleware.alert.console.service.AlertQueryService;
import com.middleware.alert.console.web.AlertConsoleController;
import com.middleware.alert.console.web.AlertConsolePageController;
import com.middleware.alert.core.AlertContext;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.web.servlet.DispatcherServletAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.DispatcherServlet;

/**
 * 管理页面自动配置。
 *
 * <p>默认关闭；只有宿主应用配置 alert.console.enabled=true 且具备 alertJdbcTemplate 时生效。</p>
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass({DispatcherServlet.class, JdbcTemplate.class})
@ConditionalOnBean(name = "alertJdbcTemplate")
@ConditionalOnProperty(prefix = "alert.console", name = "enabled", havingValue = "true")
@AutoConfigureAfter({AlertAutoConfiguration.class, AlertPersistenceAutoConfiguration.class,
        DispatcherServletAutoConfiguration.class})
@EnableConfigurationProperties(AlertConsoleProperties.class)
public class AlertConsoleAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public AlertConsoleService alertConsoleService(
            @Qualifier("alertJdbcTemplate") AlertJdbcAccess jdbcAccess,
            AlertConfigProvider alertConfigProvider,
            ObjectProvider<AlertConfigLoader> configLoaderProvider,
            AlertContext alertContext) {
        return new AlertConsoleService(jdbcAccess.jdbcTemplate(), alertConfigProvider,
                configLoaderProvider, alertContext);
    }

    @Bean(name = "alertConsoleRestTemplate")
    @ConditionalOnMissingBean(name = "alertConsoleRestTemplate")
    public RestTemplate alertConsoleRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(1000);
        factory.setReadTimeout(3000);
        return new RestTemplate(factory);
    }

    @Bean
    @ConditionalOnMissingBean
    public AlertInstanceRefresher alertInstanceRefresher(
            AlertConsoleService alertConsoleService,
            @Qualifier("alertConsoleRestTemplate") RestTemplate restTemplate) {
        return new AlertInstanceRefresher(alertConsoleService, restTemplate);
    }

    @Bean
    @ConditionalOnMissingBean
    public AlertQueryService alertQueryService(
            @Qualifier("alertJdbcTemplate") AlertJdbcAccess jdbcAccess) {
        return new AlertQueryService(jdbcAccess.jdbcTemplate());
    }

    @Bean
    @ConditionalOnMissingBean
    public AlertEffectiveService alertEffectiveService(
            AlertConsoleService alertConsoleService,
            AlertConfigProvider alertConfigProvider,
            ObjectProvider<AlertConfigLoader> configLoaderProvider,
            @Qualifier("alertConsoleRestTemplate") RestTemplate restTemplate) {
        return new AlertEffectiveService(alertConsoleService, alertConfigProvider,
                configLoaderProvider, restTemplate);
    }

    @Bean
    @ConditionalOnMissingBean
    public AlertConsoleController alertConsoleController(
            AlertConsoleService alertConsoleService,
            AlertInstanceRefresher alertInstanceRefresher,
            AlertQueryService alertQueryService,
            AlertEffectiveService alertEffectiveService) {
        return new AlertConsoleController(alertConsoleService, alertInstanceRefresher,
                alertQueryService, alertEffectiveService);
    }

    @Bean
    @ConditionalOnMissingBean
    public AlertConsolePageController alertConsolePageController() {
        return new AlertConsolePageController();
    }
}
