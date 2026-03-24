package dev.meirong.shop.contracts.event;

import java.math.BigDecimal;
import java.time.Instant;

public record WalletTransactionEventData(String transactionId,
                                         String playerId,
                                         String email,
                                         String type,
                                         BigDecimal amount,
                                         BigDecimal balance,
                                         String currency,
                                         String status,
                                         Instant occurredAt) {
}
