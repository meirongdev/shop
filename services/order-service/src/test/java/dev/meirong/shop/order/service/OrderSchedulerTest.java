package dev.meirong.shop.order.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

@ExtendWith(MockitoExtension.class)
class OrderSchedulerTest {

    @Mock
    private OrderApplicationService orderService;

    @Mock
    private RedissonClient redissonClient;

    @Mock
    private RLock cancelLock;

    @Mock
    private RLock autoCompleteLock;

    private OrderScheduler scheduler;

    @BeforeEach
    void setUp() throws Exception {
        scheduler = new OrderScheduler(orderService, redissonClient);
        lenient().when(redissonClient.getLock("shop:order:scheduler:cancel-expired")).thenReturn(cancelLock);
        lenient().when(redissonClient.getLock("shop:order:scheduler:auto-complete")).thenReturn(autoCompleteLock);
        lenient().when(cancelLock.tryLock(0, 300, TimeUnit.SECONDS)).thenReturn(true);
        lenient().when(autoCompleteLock.tryLock(0, 300, TimeUnit.SECONDS)).thenReturn(true);
    }

    @Test
    void cancelExpiredOrders_withLock_delegatesAndUnlocks() {
        Instant before = Instant.now();

        scheduler.cancelExpiredOrders();

        ArgumentCaptor<Instant> thresholdCaptor = ArgumentCaptor.forClass(Instant.class);
        verify(orderService).cancelExpiredOrders(thresholdCaptor.capture());
        verify(cancelLock).unlock();
        assertThat(thresholdCaptor.getValue()).isBetween(
                before.minusSeconds(31 * 60),
                before.minusSeconds(29 * 60));
    }

    @Test
    void cancelExpiredOrders_whenLockBusy_skipsExecution() throws Exception {
        when(cancelLock.tryLock(0, 300, TimeUnit.SECONDS)).thenReturn(false);

        scheduler.cancelExpiredOrders();

        verify(orderService, never()).cancelExpiredOrders(any());
        verify(cancelLock, never()).unlock();
    }

    @Test
    void autoCompleteDeliveredOrders_withLock_delegatesAndUnlocks() {
        scheduler.autoCompleteDeliveredOrders();

        verify(orderService).autoCompleteDeliveredOrders(any());
        verify(autoCompleteLock).unlock();
    }
}
