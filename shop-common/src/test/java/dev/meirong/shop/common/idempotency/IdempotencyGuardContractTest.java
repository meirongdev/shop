package dev.meirong.shop.common.idempotency;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class IdempotencyGuardContractTest {

    @Test
    void interfaceExists() {
        IdempotencyGuard guard = new IdempotencyGuard() {
            @Override
            public <T> T executeOnce(String key, java.util.function.Supplier<T> action,
                                     java.util.function.Supplier<T> fallback) {
                return action.get();
            }
        };
        assertThat(guard.executeOnce("k", () -> "result", () -> "fallback")).isEqualTo("result");
    }
}
