package dev.meirong.shop.common.web;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication.Type;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@AutoConfiguration
@ConditionalOnClass(WebMvcConfigurer.class)
@ConditionalOnWebApplication(type = Type.SERVLET)
public class ApiCompatibilityAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    ApiCompatibilityInterceptor apiCompatibilityInterceptor() {
        return new ApiCompatibilityInterceptor();
    }

    @Bean
    WebMvcConfigurer apiCompatibilityWebMvcConfigurer(ApiCompatibilityInterceptor apiCompatibilityInterceptor) {
        return new WebMvcConfigurer() {
            @Override
            public void addInterceptors(InterceptorRegistry registry) {
                registry.addInterceptor(apiCompatibilityInterceptor).order(Ordered.HIGHEST_PRECEDENCE + 20);
            }
        };
    }
}
