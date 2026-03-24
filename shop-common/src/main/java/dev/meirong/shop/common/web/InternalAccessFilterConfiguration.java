package dev.meirong.shop.common.web;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(InternalSecurityProperties.class)
@ConditionalOnProperty(prefix = "shop.security.internal", name = "enabled", havingValue = "true")
public class InternalAccessFilterConfiguration {

    @Bean
    FilterRegistrationBean<InternalAccessFilter> internalAccessFilterRegistration(InternalSecurityProperties properties) {
        FilterRegistrationBean<InternalAccessFilter> registrationBean = new FilterRegistrationBean<>();
        registrationBean.setFilter(new InternalAccessFilter(properties));
        registrationBean.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        return registrationBean;
    }
}
