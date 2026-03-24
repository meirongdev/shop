package ${package}.config;

import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GatewayRouteConfig {

    @Bean
    RouteLocator templateRoutes(RouteLocatorBuilder builder) {
        return builder.routes()
                .route("template-gateway-ping", route -> route
                        .path("/gateway/v1/route-ping")
                        .uri("forward:/internal/gateway/ping"))
                .build();
    }
}
