package dev.meirong.shop.common.idempotency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class DbIdempotencyGuardTest {

    @Mock
    private IdempotencyRepository repository;

    private DbIdempotencyGuard guard;

    @BeforeEach
    void setUp() {
        guard = new DbIdempotencyGuard(repository);
    }

    @Test
    void existingKey_returnsFallback() {
        when(repository.existsByKey("key-1")).thenReturn(true);

        String result = guard.executeOnce("key-1", () -> "action", () -> "fallback");

        assertThat(result).isEqualTo("fallback");
    }

    @Test
    void newKey_executesAction() {
        when(repository.existsByKey("key-2")).thenReturn(false);

        String result = guard.executeOnce("key-2", () -> "action", () -> "fallback");

        assertThat(result).isEqualTo("action");
    }

    @Test
    void concurrentDuplicate_usesFallback() {
        when(repository.existsByKey("key-3")).thenReturn(false);

        String result = guard.executeOnce("key-3",
                () -> {
                    throw new DataIntegrityViolationException("duplicate");
                },
                () -> "fallback");

        assertThat(result).isEqualTo("fallback");
    }

    @Test
    void blankKey_rejected() {
        assertThatThrownBy(() -> guard.executeOnce(" ", () -> "action", () -> "fallback"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("idempotency key");
    }
}
