package dev.meirong.shop.marketplace.service;

import dev.meirong.shop.common.metrics.MetricsHelper;
import dev.meirong.shop.contracts.api.MarketplaceApi;
import dev.meirong.shop.marketplace.domain.*;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Service
public class ProductReviewService {

    private final ProductReviewRepository reviewRepository;
    private final MarketplaceProductRepository productRepository;
    private final MetricsHelper metrics;

    public ProductReviewService(ProductReviewRepository reviewRepository,
                                 MarketplaceProductRepository productRepository,
                                 MeterRegistry meterRegistry) {
        this.reviewRepository = reviewRepository;
        this.productRepository = productRepository;
        this.metrics = new MetricsHelper("marketplace-service", meterRegistry);
    }

    @Transactional
    public MarketplaceApi.ReviewResponse createReview(String buyerId, MarketplaceApi.CreateReviewRequest request) {
        if (request.rating() < 1 || request.rating() > 5) {
            throw new IllegalArgumentException("Rating must be between 1 and 5");
        }

        // Check for duplicate review
        if (request.orderId() != null) {
            List<ProductReviewEntity> existing = reviewRepository
                    .findByProductIdAndBuyerIdAndOrderId(request.productId(), buyerId, request.orderId());
            if (!existing.isEmpty()) {
                throw new IllegalStateException("Review already exists for this order");
            }
        }

        String imagesJson = request.images() != null && !request.images().isEmpty()
                ? "[" + String.join(",", request.images().stream().map(i -> "\"" + i + "\"").toList()) + "]"
                : null;

        ProductReviewEntity review = new ProductReviewEntity(
                request.productId(), buyerId, request.orderId(),
                request.rating(), request.content(), imagesJson);
        reviewRepository.save(review);

        // Update product review stats
        updateProductReviewStats(request.productId());

        metrics.increment("shop_product_review_submitted_total",
                "rating", String.valueOf(request.rating()));

        return toResponse(review);
    }

    public MarketplaceApi.ReviewsPageResponse getProductReviews(String productId, int page, int size) {
        Page<ProductReviewEntity> results = reviewRepository
                .findByProductIdAndStatusOrderByCreatedAtDesc(
                        productId, "APPROVED", PageRequest.of(page, size));

        Double avgRating = reviewRepository.findAverageRatingByProductId(productId);
        long count = reviewRepository.countApprovedByProductId(productId);

        List<MarketplaceApi.ReviewResponse> reviews = results.getContent().stream()
                .map(this::toResponse)
                .toList();

        return new MarketplaceApi.ReviewsPageResponse(
                reviews, results.getTotalElements(), results.getNumber(), results.getSize(),
                avgRating != null ? BigDecimal.valueOf(avgRating).setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO,
                count);
    }

    private void updateProductReviewStats(String productId) {
        MarketplaceProductEntity product = productRepository.findById(productId).orElse(null);
        if (product == null) return;

        Double avg = reviewRepository.findAverageRatingByProductId(productId);
        long count = reviewRepository.countApprovedByProductId(productId);
        product.updateReviewStats((int) count,
                avg != null ? BigDecimal.valueOf(avg).setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO);
        productRepository.save(product);
    }

    private MarketplaceApi.ReviewResponse toResponse(ProductReviewEntity e) {
        List<String> images = Collections.emptyList();
        if (e.getImages() != null && !e.getImages().isBlank()) {
            images = Arrays.stream(e.getImages()
                    .replaceAll("[\\[\\]\"]", "").split(","))
                    .filter(s -> !s.isBlank())
                    .toList();
        }
        return new MarketplaceApi.ReviewResponse(
                e.getId(), e.getProductId(), e.getBuyerId(), e.getOrderId(),
                e.getRating(), e.getContent(), images, e.getStatus(), e.getCreatedAt());
    }
}
