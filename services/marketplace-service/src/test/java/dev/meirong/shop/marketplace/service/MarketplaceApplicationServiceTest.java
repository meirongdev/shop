package dev.meirong.shop.marketplace.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.meirong.shop.common.error.BusinessException;
import dev.meirong.shop.contracts.api.MarketplaceApi;
import dev.meirong.shop.marketplace.domain.MarketplaceOutboxEventEntity;
import dev.meirong.shop.marketplace.domain.MarketplaceOutboxEventRepository;
import dev.meirong.shop.marketplace.domain.MarketplaceProductEntity;
import dev.meirong.shop.marketplace.domain.MarketplaceProductRepository;
import dev.meirong.shop.marketplace.domain.ProductCategoryRepository;
import io.micrometer.core.instrument.MeterRegistry;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import java.util.concurrent.TimeUnit;

@ExtendWith(MockitoExtension.class)
class MarketplaceApplicationServiceTest {

    @Mock
    private MarketplaceProductRepository repository;

    @Mock
    private ProductCategoryRepository categoryRepository;

    @Mock
    private MarketplaceOutboxEventRepository outboxRepository;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private RedissonClient redissonClient;

    @Mock
    private RLock inventoryLock;

    @Mock
    private MeterRegistry meterRegistry;

    private MarketplaceApplicationService service;

    @BeforeEach
    void setUp() throws Exception {
        service = new MarketplaceApplicationService(
                repository,
                categoryRepository,
                outboxRepository,
                objectMapper,
                redissonClient,
                "marketplace.product.events.v1",
                meterRegistry
        );
        lenient().when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        lenient().when(outboxRepository.save(any(MarketplaceOutboxEventEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(redissonClient.getLock(anyString())).thenReturn(inventoryLock);
        lenient().when(inventoryLock.tryLock(0, 30, TimeUnit.SECONDS)).thenReturn(true);
    }

    @Test
    void listProducts_publishedOnly_returnsPublishedProducts() {
        MarketplaceProductEntity published = new MarketplaceProductEntity(
                "seller-1", "SKU-001", "Widget", "A fine widget",
                new BigDecimal("9.99"), 100, true
        );

        when(repository.findByPublishedTrueOrderByNameAsc()).thenReturn(List.of(published));

        MarketplaceApi.ProductsView result = service.listProducts(new MarketplaceApi.ListProductsRequest(true));

        assertThat(result.products()).hasSize(1);
        MarketplaceApi.ProductResponse product = result.products().get(0);
        assertThat(product.name()).isEqualTo("Widget");
        assertThat(product.published()).isTrue();
        assertThat(product.sellerId()).isEqualTo("seller-1");
        verify(repository).findByPublishedTrueOrderByNameAsc();
    }

    @Test
    void createProduct_savesAndReturnsProduct() {
        MarketplaceApi.UpsertProductRequest request = new MarketplaceApi.UpsertProductRequest(
                null, "seller-1", "SKU-002", "Gadget", "A cool gadget",
                new BigDecimal("19.99"), 50, true
        );

        when(repository.save(any(MarketplaceProductEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        MarketplaceApi.ProductResponse result = service.createProduct(request);

        assertThat(result.sellerId()).isEqualTo("seller-1");
        assertThat(result.sku()).isEqualTo("SKU-002");
        assertThat(result.name()).isEqualTo("Gadget");
        assertThat(result.description()).isEqualTo("A cool gadget");
        assertThat(result.price()).isEqualByComparingTo(new BigDecimal("19.99"));
        assertThat(result.inventory()).isEqualTo(50);
        assertThat(result.published()).isTrue();
        assertThat(result.id()).isNotNull();
        verify(repository).save(any(MarketplaceProductEntity.class));
        var outboxCaptor = ArgumentCaptor.forClass(MarketplaceOutboxEventEntity.class);
        verify(outboxRepository).save(outboxCaptor.capture());
        assertThat(outboxCaptor.getValue().getEventType()).isEqualTo(MarketplaceEventType.PRODUCT_CREATED.name());
    }

    @Test
    void updateProduct_notFound_throwsBusinessException() {
        String missingId = UUID.randomUUID().toString();
        MarketplaceApi.UpsertProductRequest request = new MarketplaceApi.UpsertProductRequest(
                missingId, "seller-1", "SKU-003", "Missing", "Does not exist",
                new BigDecimal("5.00"), 0, false
        );

        when(repository.findById(missingId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateProduct(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Product not found");
    }

    @Test
    void updateProduct_publishTransition_writesPublishedOutboxEvent() {
        String productId = UUID.randomUUID().toString();
        MarketplaceProductEntity entity = new MarketplaceProductEntity(
                "seller-1", "SKU-004", "Draft Gadget", "Draft description",
                new BigDecimal("29.99"), 20, false
        );
        MarketplaceApi.UpsertProductRequest request = new MarketplaceApi.UpsertProductRequest(
                productId, "seller-1", "SKU-004", "Draft Gadget", "Draft description",
                new BigDecimal("29.99"), 20, true
        );

        when(repository.findById(productId)).thenReturn(Optional.of(entity));
        when(repository.save(any(MarketplaceProductEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.updateProduct(request);

        var outboxCaptor = ArgumentCaptor.forClass(MarketplaceOutboxEventEntity.class);
        verify(outboxRepository).save(outboxCaptor.capture());
        assertThat(outboxCaptor.getValue().getEventType()).isEqualTo(MarketplaceEventType.PRODUCT_PUBLISHED.name());
    }

    @Test
    void deductInventory_withLock_updatesInventoryAndUnlocks() throws Exception {
        String productId = UUID.randomUUID().toString();
        MarketplaceProductEntity entity = new MarketplaceProductEntity(
                "seller-1", "SKU-005", "Locked Stock", "Inventory lock test",
                new BigDecimal("15.99"), 5, true
        );
        when(repository.findById(productId)).thenReturn(Optional.of(entity));

        service.deductInventory(new MarketplaceApi.DeductInventoryRequest(productId, 2));

        verify(redissonClient).getLock("shop:marketplace:inventory:mutate:" + productId);
        verify(repository).save(entity);
        verify(inventoryLock).unlock();
        assertThat(entity.getInventory()).isEqualTo(3);
    }

    @Test
    void deductInventory_whenLockBusy_throwsAndSkipsRepository() throws Exception {
        String productId = UUID.randomUUID().toString();
        when(inventoryLock.tryLock(0, 30, TimeUnit.SECONDS)).thenReturn(false);

        assertThatThrownBy(() -> service.deductInventory(new MarketplaceApi.DeductInventoryRequest(productId, 1)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Inventory is busy");
    }
}
