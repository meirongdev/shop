package dev.meirong.shop.promotion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.meirong.shop.common.error.BusinessException;
import dev.meirong.shop.promotion.domain.CouponInstanceEntity;
import dev.meirong.shop.promotion.domain.CouponInstanceRepository;
import dev.meirong.shop.promotion.domain.CouponTemplateEntity;
import dev.meirong.shop.promotion.domain.CouponTemplateRepository;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

@ExtendWith(MockitoExtension.class)
class CouponTemplateServiceTest {

    @Mock
    private CouponTemplateRepository templateRepository;

    @Mock
    private CouponInstanceRepository instanceRepository;

    @Mock
    private RedissonClient redissonClient;

    @Mock
    private RLock issueLock;

    private CouponTemplateService service;

    @BeforeEach
    void setUp() throws Exception {
        service = new CouponTemplateService(templateRepository, instanceRepository, redissonClient);
        when(redissonClient.getLock(anyString())).thenReturn(issueLock);
        when(issueLock.tryLock(0, 30, TimeUnit.SECONDS)).thenReturn(true);
    }

    @Test
    void issueToBuyer_withLock_enforcesChecksAndSaves() {
        CouponTemplateEntity template = new CouponTemplateEntity(
                "seller-1", "WELCOME10", "Welcome", "FIXED_AMOUNT",
                BigDecimal.TEN, BigDecimal.ZERO, null, 100, 1, 7);
        when(templateRepository.findById("tpl-1")).thenReturn(Optional.of(template));
        when(instanceRepository.countByTemplateIdAndBuyerId("tpl-1", "buyer-1")).thenReturn(0L);
        when(instanceRepository.countByTemplateId("tpl-1")).thenReturn(0L);
        when(instanceRepository.save(any(CouponInstanceEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CouponInstanceEntity issued = service.issueToBuyer("tpl-1", "buyer-1");

        assertThat(issued.getTemplateId()).isEqualTo("tpl-1");
        assertThat(issued.getBuyerId()).isEqualTo("buyer-1");
        verify(issueLock).unlock();
    }

    @Test
    void issueToBuyer_whenLockBusy_throws() throws Exception {
        when(issueLock.tryLock(0, 30, TimeUnit.SECONDS)).thenReturn(false);

        assertThatThrownBy(() -> service.issueToBuyer("tpl-1", "buyer-1"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Coupon template is busy");
    }
}
