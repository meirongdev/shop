package dev.meirong.shop.contracts.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record BuyerRegisteredEventData(String buyerId,
                                       String username,
                                       String email) {
}
