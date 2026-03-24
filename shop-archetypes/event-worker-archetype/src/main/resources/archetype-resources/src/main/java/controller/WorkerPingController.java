package ${package}.controller;

import ${package}.service.TemplateWorkerService;
import ${package}.service.TemplateWorkerService.WorkerStatusResponse;
import dev.meirong.shop.common.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/worker/v1")
public class WorkerPingController {

    private final TemplateWorkerService service;

    public WorkerPingController(TemplateWorkerService service) {
        this.service = service;
    }

    @Operation(summary = "Template worker ping endpoint")
    @GetMapping("/ping")
    public ApiResponse<WorkerStatusResponse> ping() {
        return ApiResponse.success(service.buildResponse());
    }
}
