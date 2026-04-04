package ${package}.controller;

import ${package}.service.TemplateAuthService;
import ${package}.service.TemplateAuthService.TemplateAuthResponse;
import dev.meirong.shop.common.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth/v1")
public class AuthPingController {

    private final TemplateAuthService service;

    public AuthPingController(TemplateAuthService service) {
        this.service = service;
    }

    @Operation(summary = "Template auth ping endpoint")
    @GetMapping("/ping")
    public ApiResponse<TemplateAuthResponse> ping() {
        return ApiResponse.success(service.buildResponse());
    }
}
