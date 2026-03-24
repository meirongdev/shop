package dev.meirong.shop.buyerbff.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.meirong.shop.buyerbff.config.BuyerClientProperties;
import dev.meirong.shop.common.error.BusinessException;
import dev.meirong.shop.common.error.CommonErrorCode;
import dev.meirong.shop.contracts.api.OrderApi;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class GuestCartStore {

    private static final String GUEST_BUYER_PREFIX = "guest-buyer-";
    private static final String CART_KEY_PREFIX = "buyer:guest:cart:";
    private static final TypeReference<List<OrderApi.CartItemResponse>> CART_LIST_TYPE =
            new TypeReference<>() {};

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final Duration guestCartTtl;

    public GuestCartStore(StringRedisTemplate redisTemplate,
                          ObjectMapper objectMapper,
                          BuyerClientProperties properties) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.guestCartTtl = properties.guestCartTtl();
    }

    public boolean isGuestBuyer(String buyerId) {
        return buyerId != null && buyerId.startsWith(GUEST_BUYER_PREFIX);
    }

    public OrderApi.CartView listCart(String buyerId) {
        return toCartView(loadItems(buyerId));
    }

    public OrderApi.CartItemResponse addToCart(OrderApi.AddToCartRequest request) {
        List<OrderApi.CartItemResponse> items = loadItems(request.buyerId());
        int existingIndex = findItemIndex(items, request.productId());
        OrderApi.CartItemResponse response;
        if (existingIndex >= 0) {
            OrderApi.CartItemResponse existing = items.get(existingIndex);
            response = new OrderApi.CartItemResponse(
                    existing.id(),
                    existing.buyerId(),
                    existing.productId(),
                    existing.productName(),
                    existing.productPrice(),
                    existing.sellerId(),
                    existing.quantity() + request.quantity(),
                    existing.createdAt());
            items.set(existingIndex, response);
        } else {
            response = new OrderApi.CartItemResponse(
                    UUID.randomUUID(),
                    request.buyerId(),
                    request.productId(),
                    request.productName(),
                    request.productPrice(),
                    request.sellerId(),
                    request.quantity(),
                    Instant.now());
            items.add(response);
        }
        saveItems(request.buyerId(), items);
        return response;
    }

    public OrderApi.CartItemResponse updateCart(OrderApi.UpdateCartRequest request) {
        List<OrderApi.CartItemResponse> items = loadItems(request.buyerId());
        int existingIndex = findItemIndex(items, request.productId());
        if (existingIndex < 0) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "Cart item not found");
        }
        OrderApi.CartItemResponse existing = items.get(existingIndex);
        if (request.quantity() <= 0) {
            items.remove(existingIndex);
            saveItems(request.buyerId(), items);
            return existing;
        }

        OrderApi.CartItemResponse updated = new OrderApi.CartItemResponse(
                existing.id(),
                existing.buyerId(),
                existing.productId(),
                existing.productName(),
                existing.productPrice(),
                existing.sellerId(),
                request.quantity(),
                existing.createdAt());
        items.set(existingIndex, updated);
        saveItems(request.buyerId(), items);
        return updated;
    }

    public void removeFromCart(OrderApi.RemoveFromCartRequest request) {
        List<OrderApi.CartItemResponse> items = loadItems(request.buyerId());
        items.removeIf(item -> item.productId().equals(request.productId()));
        saveItems(request.buyerId(), items);
    }

    public void clearCart(String buyerId) {
        requireGuestBuyer(buyerId);
        redisTemplate.delete(cartKey(buyerId));
    }

    private OrderApi.CartView toCartView(List<OrderApi.CartItemResponse> items) {
        BigDecimal subtotal = items.stream()
                .map(item -> item.productPrice().multiply(BigDecimal.valueOf(item.quantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new OrderApi.CartView(List.copyOf(items), subtotal);
    }

    private List<OrderApi.CartItemResponse> loadItems(String buyerId) {
        requireGuestBuyer(buyerId);
        String payload = redisTemplate.opsForValue().get(cartKey(buyerId));
        if (payload == null || payload.isBlank()) {
            return new ArrayList<>();
        }
        try {
            List<OrderApi.CartItemResponse> items = objectMapper.readValue(payload, CART_LIST_TYPE);
            redisTemplate.expire(cartKey(buyerId), guestCartTtl);
            return new ArrayList<>(items);
        } catch (IOException exception) {
            throw new BusinessException(CommonErrorCode.INTERNAL_ERROR,
                    "Failed to read guest cart for " + buyerId, exception);
        }
    }

    private void saveItems(String buyerId, List<OrderApi.CartItemResponse> items) {
        requireGuestBuyer(buyerId);
        if (items.isEmpty()) {
            redisTemplate.delete(cartKey(buyerId));
            return;
        }
        try {
            redisTemplate.opsForValue().set(
                    cartKey(buyerId),
                    objectMapper.writeValueAsString(items),
                    guestCartTtl);
        } catch (IOException exception) {
            throw new BusinessException(CommonErrorCode.INTERNAL_ERROR,
                    "Failed to store guest cart for " + buyerId, exception);
        }
    }

    private int findItemIndex(List<OrderApi.CartItemResponse> items, String productId) {
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).productId().equals(productId)) {
                return i;
            }
        }
        return -1;
    }

    private void requireGuestBuyer(String buyerId) {
        if (!isGuestBuyer(buyerId)) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN,
                    "Guest cart only supports guest buyer identities");
        }
    }

    private String cartKey(String buyerId) {
        return CART_KEY_PREFIX + buyerId;
    }
}
