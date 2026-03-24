package dev.meirong.shop.contracts.event;

import java.math.BigDecimal;
import java.util.List;

public record OrderEventData(String orderId,
                             String orderNo,
                             String buyerId,
                             String sellerId,
                             String status,
                             BigDecimal totalAmount,
                             List<OrderItemSummary> items) {

    public record OrderItemSummary(String productId,
                                   String productName,
                                   int quantity,
                                   BigDecimal lineTotal) {
    }
}
