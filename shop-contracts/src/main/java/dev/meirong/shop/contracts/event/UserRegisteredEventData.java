package dev.meirong.shop.contracts.event;

public record UserRegisteredEventData(String playerId,
                                      String username,
                                      String email) {
}
