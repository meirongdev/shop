package dev.meirong.shop.contracts.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;
import java.time.Instant;

@JsonIgnoreProperties(ignoreUnknown = true)
public record WalletTransactionEventData(String transactionId,
                                         String buyerId,
                                         String email,
                                         String type,
                                         BigDecimal amount,
                                         BigDecimal balance,
                                         String currency,
                                         String status,
                                         Instant occurredAt) {
}
