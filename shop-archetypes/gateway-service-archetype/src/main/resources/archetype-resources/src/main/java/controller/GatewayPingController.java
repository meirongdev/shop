package ${package}.controller;

import ${package}.service.GatewayTemplateService;
import ${package}.service.GatewayTemplateService.GatewayStatusResponse;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/gateway")
public class GatewayPingController {

    private final GatewayTemplateService service;

    public GatewayPingController(GatewayTemplateService service) {
        this.service = service;
    }

    @Operation(summary = "Template gateway ping endpoint")
    @GetMapping("/ping")
    public GatewayStatusResponse ping() {
        return service.buildResponse();
    }
}
