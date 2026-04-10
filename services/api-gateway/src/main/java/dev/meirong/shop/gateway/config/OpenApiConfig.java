package dev.meirong.shop.gateway.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration(proxyBeanMethods = false)
public class OpenApiConfig {

    @Bean
    public OpenAPI gatewayOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Shop Platform API Portal")
                        .description("微服务聚合 API 门户，通过网关统一访问各服务 OpenAPI 文档。")
                        .version("v1")
                        .contact(new Contact().name("Meirong Dev Team")));
    }

    /**
     * Spring Cloud Gateway MVC 模式下，需手动注册 Swagger UI 静态资源处理器，
     * 避免请求被 Gateway 路由拦截导致 404。
     */
    @Bean
    public WebMvcConfigurer swaggerUiResourceHandler() {
        return new WebMvcConfigurer() {
            @Override
            public void addResourceHandlers(ResourceHandlerRegistry registry) {
                registry.addResourceHandler("/swagger-ui/**")
                        .addResourceLocations("classpath:/META-INF/resources/webjars/swagger-ui/");
                registry.addResourceHandler("/webjars/**")
                        .addResourceLocations("classpath:/META-INF/resources/webjars/");
            }
        };
    }
}
