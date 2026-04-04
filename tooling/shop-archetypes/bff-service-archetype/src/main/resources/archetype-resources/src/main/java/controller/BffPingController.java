package ${package}.controller;

import ${package}.service.TemplateAggregationService;
import ${package}.service.TemplateAggregationService.TemplateBffResponse;
import dev.meirong.shop.common.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/bff/v1")
public class BffPingController {

    private final TemplateAggregationService service;

    public BffPingController(TemplateAggregationService service) {
        this.service = service;
    }

    @Operation(summary = "Template BFF ping endpoint")
    @GetMapping("/ping")
    public ApiResponse<TemplateBffResponse> ping() {
        return ApiResponse.success(service.buildResponse());
    }
}
