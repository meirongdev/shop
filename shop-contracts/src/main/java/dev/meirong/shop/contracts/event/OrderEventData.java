package dev.meirong.shop.contracts.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record OrderEventData(String orderId,
                             String orderNo,
                             String buyerId,
                             String sellerId,
                             String status,
                             BigDecimal totalAmount,
                             List<OrderItemSummary> items) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record OrderItemSummary(String productId,
                                   String productName,
                                   int quantity,
                                   BigDecimal lineTotal) {
    }
}
