package dev.meirong.shop.buyerbff.service;

import dev.meirong.shop.buyerbff.config.BuyerClientProperties;
import dev.meirong.shop.common.api.ApiResponse;
import dev.meirong.shop.common.error.BusinessException;
import dev.meirong.shop.common.error.CommonErrorCode;
import dev.meirong.shop.common.http.TrustedHeaderNames;
import dev.meirong.shop.contracts.api.MarketplaceApi;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import org.springframework.context.annotation.Profile;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Service
@Profile("load-test")
public class LoadTestExperimentService {

    private static final String EXPERIMENT_HEADER = "X-H2c-Experiment";

    private final RestClient restClient;
    private final BuyerClientProperties properties;

    public LoadTestExperimentService(RestClient.Builder builder, BuyerClientProperties properties) {
        this.restClient = builder.build();
        this.properties = properties;
    }

    public MarketplaceBurstResponse runMarketplaceBurst(int fanout, int headerBytes) {
        if (fanout < 1 || fanout > 32) {
            throw new BusinessException(CommonErrorCode.VALIDATION_ERROR, "fanout must be between 1 and 32");
        }
        if (headerBytes < 0 || headerBytes > 4096) {
            throw new BusinessException(CommonErrorCode.VALIDATION_ERROR, "headerBytes must be between 0 and 4096");
        }
        String experimentHeaderValue = headerBytes == 0 ? null : "h".repeat(headerBytes);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var futures = java.util.stream.IntStream.range(0, fanout)
                    .mapToObj(ignored -> executor.submit(() -> listCategories(experimentHeaderValue)))
                    .toList();
            int totalCategories = 0;
            for (var future : futures) {
                totalCategories += future.get().size();
            }
            return new MarketplaceBurstResponse(fanout, headerBytes, futures.size(), totalCategories);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new BusinessException(CommonErrorCode.INTERNAL_ERROR, "Marketplace burst interrupted", exception);
        } catch (ExecutionException exception) {
            Throwable cause = exception.getCause() == null ? exception : exception.getCause();
            throw new BusinessException(
                    CommonErrorCode.DOWNSTREAM_ERROR,
                    "Marketplace burst failed: " + cause.getClass().getSimpleName() + " - " + cause.getMessage(),
                    cause
            );
        }
    }

    private java.util.List<MarketplaceApi.CategoryResponse> listCategories(String experimentHeaderValue) {
        try {
            var request = restClient.post()
                    .uri(properties.marketplaceServiceUrl() + MarketplaceApi.CATEGORY_LIST)
                    .header(TrustedHeaderNames.INTERNAL_TOKEN, properties.internalToken())
                    .body(Map.of());
            if (experimentHeaderValue != null) {
                request.header(EXPERIMENT_HEADER, experimentHeaderValue);
            }
            ApiResponse<java.util.List<MarketplaceApi.CategoryResponse>> response = request.retrieve()
                    .body(new ParameterizedTypeReference<ApiResponse<java.util.List<MarketplaceApi.CategoryResponse>>>() {});
            if (response == null || response.data() == null) {
                throw new BusinessException(CommonErrorCode.DOWNSTREAM_ERROR,
                        "Empty downstream response from " + MarketplaceApi.CATEGORY_LIST);
            }
            return response.data();
        } catch (RestClientResponseException exception) {
            throw new BusinessException(CommonErrorCode.DOWNSTREAM_ERROR, "Marketplace category burst failed", exception);
        }
    }

    public record MarketplaceBurstResponse(int fanout, int headerBytes, int downstreamCalls, int totalCategories) {
    }
}
