package dev.meirong.shop.gateway.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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
}
