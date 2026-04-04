package dev.meirong.shop.marketplace.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.meirong.shop.common.error.BusinessException;
import dev.meirong.shop.common.error.CommonErrorCode;
import dev.meirong.shop.common.metrics.MetricsHelper;
import dev.meirong.shop.contracts.api.MarketplaceApi;
import dev.meirong.shop.contracts.api.MarketplaceInternalApi;
import dev.meirong.shop.contracts.event.EventEnvelope;
import dev.meirong.shop.contracts.event.MarketplaceProductEventData;
import dev.meirong.shop.marketplace.domain.MarketplaceOutboxEventEntity;
import dev.meirong.shop.marketplace.domain.MarketplaceOutboxEventRepository;
import dev.meirong.shop.marketplace.domain.MarketplaceProductEntity;
import dev.meirong.shop.marketplace.domain.MarketplaceProductRepository;
import dev.meirong.shop.marketplace.domain.ProductCategoryEntity;
import dev.meirong.shop.marketplace.domain.ProductCategoryRepository;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MarketplaceApplicationService {

    private static final long INVENTORY_LOCK_LEASE_SECONDS = 30;

    private final MarketplaceProductRepository repository;
    private final ProductCategoryRepository categoryRepository;
    private final MarketplaceOutboxEventRepository outboxRepository;
    private final ObjectMapper objectMapper;
    private final RedissonClient redissonClient;
    private final String productTopic;
    private final MetricsHelper metrics;

    public MarketplaceApplicationService(MarketplaceProductRepository repository,
                                         ProductCategoryRepository categoryRepository,
                                         MarketplaceOutboxEventRepository outboxRepository,
                                         ObjectMapper objectMapper,
                                         RedissonClient redissonClient,
                                         @Value("${shop.marketplace.outbox.topic:marketplace.product.events.v1}") String productTopic,
                                         MeterRegistry meterRegistry) {
        this.repository = repository;
        this.categoryRepository = categoryRepository;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
        this.redissonClient = redissonClient;
        this.productTopic = productTopic;
        this.metrics = new MetricsHelper("marketplace-service", meterRegistry);
    }

    @Transactional(readOnly = true)
    public MarketplaceApi.ProductsView listProducts(MarketplaceApi.ListProductsRequest request) {
        List<MarketplaceProductEntity> entities = request.publishedOnly()
                ? repository.findByPublishedTrueOrderByNameAsc()
                : repository.findAll();
        return new MarketplaceApi.ProductsView(entities.stream().map(this::toResponse).toList());
    }

    @Transactional(readOnly = true)
    public MarketplaceInternalApi.PagedProductsResponse listAllProductsPaged(int page, int size) {
        var pageable = PageRequest.of(page, size, Sort.by("createdAt").ascending());
        Page<MarketplaceProductEntity> result = repository.findAll(pageable);
        List<MarketplaceApi.ProductResponse> products = result.getContent().stream()
                .map(this::toResponse)
                .toList();
        return new MarketplaceInternalApi.PagedProductsResponse(
                products, result.getNumber(), result.getTotalPages(), result.getTotalElements());
    }

    @Transactional
    public MarketplaceApi.ProductResponse createProduct(MarketplaceApi.UpsertProductRequest request) {
        MarketplaceProductEntity entity = new MarketplaceProductEntity(
                request.sellerId(),
                request.sku(),
                request.name(),
                request.description(),
                request.price(),
                request.inventory(),
                request.published()
        );
        MarketplaceProductEntity saved = repository.save(entity);
        writeOutboxEvent(saved, MarketplaceEventType.PRODUCT_CREATED);
        metrics.increment("shop_product_created_total");
        return toResponse(saved);
    }

    @Transactional
    public MarketplaceApi.ProductResponse updateProduct(MarketplaceApi.UpsertProductRequest request) {
        MarketplaceProductEntity entity = repository.findById(request.productId())
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND, "Product not found: " + request.productId()));
        boolean wasBefore = entity.isPublished();
        entity.update(
                request.sellerId(),
                request.sku(),
                request.name(),
                request.description(),
                request.price(),
                request.inventory(),
                request.published()
        );
        MarketplaceProductEntity saved = repository.save(entity);
        MarketplaceEventType eventType = resolveUpdateEventType(wasBefore, saved.isPublished());
        writeOutboxEvent(saved, eventType);
        metrics.increment("shop_product_updated_total", "seller_id", request.sellerId());
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public MarketplaceApi.ProductResponse getProduct(String productId) {
        MarketplaceProductEntity entity = repository.findById(productId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND, "Product not found: " + productId));
        return toResponse(entity);
    }

    @Transactional(readOnly = true)
    public MarketplaceApi.ProductsPageView searchProducts(MarketplaceApi.SearchProductsRequest request) {
        String query = (request.query() == null || request.query().isBlank()) ? null : request.query();
        String categoryId = (request.categoryId() == null || request.categoryId().isBlank()) ? null : request.categoryId();
        Page<MarketplaceProductEntity> page = repository.searchProducts(query, categoryId,
                PageRequest.of(request.page(), request.size()));
        return new MarketplaceApi.ProductsPageView(
                page.getContent().stream().map(this::toResponse).toList(),
                page.getTotalElements(), page.getNumber(), page.getSize());
    }

    @Transactional(readOnly = true)
    public List<MarketplaceApi.CategoryResponse> listCategories() {
        return categoryRepository.findAllByOrderByNameAsc().stream()
                .map(c -> new MarketplaceApi.CategoryResponse(UUID.fromString(c.getId()), c.getName(), c.getDescription()))
                .toList();
    }

    @Transactional
    public void deductInventory(MarketplaceApi.DeductInventoryRequest request) {
        executeWithInventoryLock(request.productId(), () -> {
            MarketplaceProductEntity entity = repository.findById(request.productId())
                    .orElseThrow(() -> new BusinessException(
                            CommonErrorCode.NOT_FOUND, "Product not found: " + request.productId()));
            if (!entity.deductInventory(request.quantity())) {
                throw new BusinessException(CommonErrorCode.INVENTORY_INSUFFICIENT,
                        "Insufficient inventory for product: " + entity.getName());
            }
            repository.save(entity);
            metrics.increment("shop_inventory_deducted_total", "product_id", request.productId());
        });
    }

    @Transactional
    public void restoreInventory(MarketplaceApi.RestoreInventoryRequest request) {
        executeWithInventoryLock(request.productId(), () -> {
            MarketplaceProductEntity entity = repository.findById(request.productId())
                    .orElseThrow(() -> new BusinessException(
                            CommonErrorCode.NOT_FOUND, "Product not found: " + request.productId()));
            entity.restoreInventory(request.quantity());
            repository.save(entity);
            metrics.increment("shop_inventory_restored_total", "product_id", request.productId());
        });
    }

    @Transactional(readOnly = true)
    public List<MarketplaceApi.ProductResponse> listProductsForSeller(String sellerId) {
        return repository.findBySellerIdOrderByNameAsc(sellerId).stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public long countProductsForSeller(String sellerId) {
        return repository.countBySellerId(sellerId);
    }

    private void executeWithInventoryLock(String productId, Runnable action) {
        RLock lock = redissonClient.getLock("shop:marketplace:inventory:mutate:" + productId);
        boolean acquired = false;
        try {
            acquired = lock.tryLock(0, INVENTORY_LOCK_LEASE_SECONDS, TimeUnit.SECONDS);
            if (!acquired) {
                throw new BusinessException(CommonErrorCode.TOO_MANY_REQUESTS,
                        "Inventory is busy for product: " + productId);
            }
            action.run();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new BusinessException(CommonErrorCode.INTERNAL_ERROR,
                    "Interrupted while mutating inventory for product: " + productId);
        } finally {
            if (acquired) {
                lock.unlock();
            }
        }
    }

    private MarketplaceEventType resolveUpdateEventType(boolean wasBefore, boolean isAfter) {
        if (!wasBefore && isAfter) return MarketplaceEventType.PRODUCT_PUBLISHED;
        if (wasBefore && !isAfter) return MarketplaceEventType.PRODUCT_UNPUBLISHED;
        return MarketplaceEventType.PRODUCT_UPDATED;
    }

    private void writeOutboxEvent(MarketplaceProductEntity product, MarketplaceEventType eventType) {
        String categoryName = null;
        if (product.getCategoryId() != null) {
            categoryName = categoryRepository.findById(product.getCategoryId())
                    .map(ProductCategoryEntity::getName).orElse(null);
        }
        var eventData = new MarketplaceProductEventData(
                product.getId(), product.getSellerId(), product.getSku(),
                product.getName(), product.getDescription(), product.getPrice(),
                product.getInventory(), product.isPublished(), product.getCategoryId(),
                categoryName, product.getImageUrl(),
                product.getStatus(), Instant.now()
        );
        var envelope = new EventEnvelope<>(
                UUID.randomUUID().toString(), "marketplace-service", eventType.name(),
                Instant.now(), eventData
        );
        try {
            var entity = new MarketplaceOutboxEventEntity(
                    product.getId(), productTopic,
                    eventType.name(), objectMapper.writeValueAsString(envelope)
            );
            outboxRepository.save(entity);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize outbox event", e);
        }
    }

    private MarketplaceApi.ProductResponse toResponse(MarketplaceProductEntity entity) {
        String categoryName = null;
        if (entity.getCategoryId() != null) {
            categoryName = categoryRepository.findById(entity.getCategoryId())
                    .map(ProductCategoryEntity::getName).orElse(null);
        }
        return new MarketplaceApi.ProductResponse(
                UUID.fromString(entity.getId()),
                entity.getSellerId(),
                entity.getSku(),
                entity.getName(),
                entity.getDescription(),
                entity.getPrice(),
                entity.getInventory(),
                entity.isPublished(),
                entity.getCategoryId(),
                categoryName,
                entity.getImageUrl(),
                entity.getStatus(),
                entity.getReviewCount(),
                entity.getAvgRating()
        );
    }
}
