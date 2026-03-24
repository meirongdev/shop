package dev.meirong.shop.marketplace.service;

import dev.meirong.shop.contracts.api.MarketplaceApi;
import dev.meirong.shop.marketplace.domain.ProductVariantEntity;
import dev.meirong.shop.marketplace.domain.ProductVariantRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
public class ProductVariantService {

    private final ProductVariantRepository variantRepository;

    public ProductVariantService(ProductVariantRepository variantRepository) {
        this.variantRepository = variantRepository;
    }

    @Transactional
    public MarketplaceApi.VariantResponse createVariant(String productId, MarketplaceApi.CreateVariantRequest request) {
        ProductVariantEntity variant = new ProductVariantEntity(
                productId, request.variantName(), request.attributes(),
                request.priceAdjust() != null ? request.priceAdjust() : BigDecimal.ZERO,
                request.inventory(), request.skuSuffix());
        variantRepository.save(variant);
        return toResponse(variant);
    }

    public List<MarketplaceApi.VariantResponse> getVariants(String productId) {
        return variantRepository.findByProductIdOrderByDisplayOrderAsc(productId).stream()
                .map(this::toResponse)
                .toList();
    }

    private MarketplaceApi.VariantResponse toResponse(ProductVariantEntity e) {
        return new MarketplaceApi.VariantResponse(
                e.getId(), e.getProductId(), e.getVariantName(), e.getAttributes(),
                e.getPriceAdjust(), e.getInventory(), e.getSkuSuffix(), e.getDisplayOrder());
    }
}
