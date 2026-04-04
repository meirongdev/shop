package ${package}.controller;

import ${package}.service.TemplateDomainService;
import ${package}.service.TemplateDomainService.TemplateDomainResponse;
import dev.meirong.shop.common.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/domain/v1")
public class DomainPingController {

    private final TemplateDomainService service;

    public DomainPingController(TemplateDomainService service) {
        this.service = service;
    }

    @Operation(summary = "Template domain ping endpoint")
    @GetMapping("/ping")
    public ApiResponse<TemplateDomainResponse> ping() {
        return ApiResponse.success(service.buildResponse());
    }
}
