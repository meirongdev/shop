package dev.meirong.shop.order.service;

import dev.meirong.shop.contracts.api.OrderApi;
import dev.meirong.shop.order.domain.CartItemEntity;
import dev.meirong.shop.order.domain.CartItemRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CartService {

    private final CartItemRepository repository;

    public CartService(CartItemRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public OrderApi.CartView listCart(String buyerId) {
        List<CartItemEntity> items = repository.findByBuyerIdOrderByCreatedAtAsc(buyerId);
        BigDecimal subtotal = items.stream()
                .map(item -> item.getProductPrice().multiply(BigDecimal.valueOf(item.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new OrderApi.CartView(items.stream().map(this::toResponse).toList(), subtotal);
    }

    @Transactional
    public OrderApi.CartItemResponse addToCart(OrderApi.AddToCartRequest request) {
        CartItemEntity existing = repository.findByBuyerIdAndProductId(request.buyerId(), request.productId()).orElse(null);
        if (existing != null) {
            existing.addQuantity(request.quantity());
            return toResponse(repository.save(existing));
        }
        CartItemEntity entity = new CartItemEntity(
                request.buyerId(), request.productId(), request.productName(),
                request.productPrice(), request.sellerId(), request.quantity());
        return toResponse(repository.save(entity));
    }

    @Transactional
    public OrderApi.CartItemResponse updateCart(OrderApi.UpdateCartRequest request) {
        CartItemEntity entity = repository.findByBuyerIdAndProductId(request.buyerId(), request.productId())
                .orElseThrow(() -> new dev.meirong.shop.common.error.BusinessException(
                        dev.meirong.shop.common.error.CommonErrorCode.NOT_FOUND, "Cart item not found"));
        if (request.quantity() <= 0) {
            repository.delete(entity);
            return toResponse(entity);
        }
        entity.updateQuantity(request.quantity());
        return toResponse(repository.save(entity));
    }

    @Transactional
    public void removeFromCart(OrderApi.RemoveFromCartRequest request) {
        repository.findByBuyerIdAndProductId(request.buyerId(), request.productId())
                .ifPresent(repository::delete);
    }

    @Transactional
    public void clearCart(String buyerId) {
        repository.deleteByBuyerId(buyerId);
    }

    private OrderApi.CartItemResponse toResponse(CartItemEntity entity) {
        return new OrderApi.CartItemResponse(
                UUID.fromString(entity.getId()),
                entity.getBuyerId(),
                entity.getProductId(),
                entity.getProductName(),
                entity.getProductPrice(),
                entity.getSellerId(),
                entity.getQuantity(),
                entity.getCreatedAt());
    }
}
