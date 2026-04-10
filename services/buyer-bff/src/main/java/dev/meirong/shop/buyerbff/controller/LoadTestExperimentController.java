package dev.meirong.shop.buyerbff.controller;

import dev.meirong.shop.buyerbff.service.LoadTestExperimentService;
import dev.meirong.shop.common.api.ApiResponse;
import dev.meirong.shop.contracts.buyer.BuyerApi;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("load-test")
@RequestMapping(BuyerApi.BASE_PATH + "/experiments/h2c")
public class LoadTestExperimentController {

    private final LoadTestExperimentService service;

    public LoadTestExperimentController(LoadTestExperimentService service) {
        this.service = service;
    }

    @PostMapping("/marketplace-burst")
    public ApiResponse<LoadTestExperimentService.MarketplaceBurstResponse> marketplaceBurst(
            @Valid @RequestBody MarketplaceBurstRequest request) {
        return ApiResponse.success(service.runMarketplaceBurst(request.fanout(), request.headerBytes()));
    }

    public record MarketplaceBurstRequest(@Min(1) @Max(32) int fanout,
                                          @Min(0) @Max(4096) int headerBytes) {
    }
}
