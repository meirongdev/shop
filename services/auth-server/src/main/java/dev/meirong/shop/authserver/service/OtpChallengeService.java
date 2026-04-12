package dev.meirong.shop.authserver.service;

import dev.meirong.shop.authserver.config.AuthOtpProperties;
import dev.meirong.shop.authserver.domain.UserAccountEntity;
import dev.meirong.shop.authserver.domain.UserAccountRepository;
import dev.meirong.shop.common.error.BusinessException;
import dev.meirong.shop.common.error.CommonErrorCode;
import dev.meirong.shop.contracts.auth.AuthApi;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.redisson.api.RAtomicLong;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

@Service
public class OtpChallengeService {

    private final SecureRandom secureRandom = new SecureRandom();
    private final RedissonClient redissonClient;
    private final AuthOtpProperties properties;
    private final SmsGateway smsGateway;
    private final UserAccountRepository userAccountRepository;
    private final BuyerAccountProvisioningService buyerAccountProvisioningService;
    private final JwtTokenService jwtTokenService;

    public OtpChallengeService(RedissonClient redissonClient,
                               AuthOtpProperties properties,
                               SmsGateway smsGateway,
                               UserAccountRepository userAccountRepository,
                               BuyerAccountProvisioningService buyerAccountProvisioningService,
                               JwtTokenService jwtTokenService) {
        this.redissonClient = redissonClient;
        this.properties = properties;
        this.smsGateway = smsGateway;
        this.userAccountRepository = userAccountRepository;
        this.buyerAccountProvisioningService = buyerAccountProvisioningService;
        this.jwtTokenService = jwtTokenService;
    }

    public AuthApi.OtpSendResponse sendOtp(AuthApi.OtpSendRequest request) {
        String phone = request.phoneNumber();
        if (redissonClient.<String>getBucket(cooldownKey(phone)).isExists()) {
            throw new BusinessException(CommonErrorCode.VALIDATION_ERROR, "OTP cooldown active");
        }
        RAtomicLong dailyCounter = redissonClient.getAtomicLong(dailyKey(phone));
        long dailyCount = dailyCounter.incrementAndGet();
        if (dailyCount == 1L) {
            dailyCounter.expire(Duration.ofDays(1));
        }
        if (dailyCount > properties.dailyLimit()) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN, "OTP daily limit exceeded");
        }
        String otp = String.format("%06d", secureRandom.nextInt(1_000_000));
        redissonClient.<String>getBucket(codeKey(phone)).set(otp, properties.codeTtl().toMillis(), TimeUnit.MILLISECONDS);
        redissonClient.<String>getBucket(cooldownKey(phone)).set("1", properties.cooldownTtl().toMillis(), TimeUnit.MILLISECONDS);
        smsGateway.send(phone, localizedMessage(otp, request.locale()));
        return new AuthApi.OtpSendResponse((int) properties.codeTtl().toSeconds(), (int) properties.cooldownTtl().toSeconds());
    }

    public AuthApi.TokenResponse verifyOtp(AuthApi.OtpVerifyRequest request) {
        String phone = request.phoneNumber();
        if (redissonClient.<String>getBucket(lockoutKey(phone)).isExists()) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN, "OTP verification locked");
        }
        String expectedCode = redissonClient.<String>getBucket(codeKey(phone)).get();
        if (expectedCode == null) {
            throw new BusinessException(CommonErrorCode.VALIDATION_ERROR, "OTP expired");
        }
        if (!expectedCode.equals(request.otp())) {
            RAtomicLong attemptCounter = redissonClient.getAtomicLong(attemptKey(phone));
            long attempts = attemptCounter.incrementAndGet();
            if (attempts == 1L) {
                attemptCounter.expire(properties.lockoutTtl());
            }
            if (attempts >= properties.maxAttempts()) {
                redissonClient.<String>getBucket(lockoutKey(phone)).set("1", properties.lockoutTtl().toMillis(), TimeUnit.MILLISECONDS);
                throw new BusinessException(CommonErrorCode.FORBIDDEN, "OTP verification locked");
            }
            throw new BusinessException(CommonErrorCode.VALIDATION_ERROR, "OTP invalid");
        }
        redissonClient.<String>getBucket(codeKey(phone)).delete();
        redissonClient.getAtomicLong(attemptKey(phone)).delete();
        UserAccountEntity account = userAccountRepository.findByPhoneNumber(phone).orElse(null);
        boolean newUser = false;
        if (account == null) {
            account = buyerAccountProvisioningService.provisionPhoneBuyer(phone);
            account.setPhoneNumber(phone);
            newUser = true;
        }
        return jwtTokenService.issueToken(account, newUser);
    }

    private String localizedMessage(String otp, String locale) {
        if ("zh-CN".equalsIgnoreCase(locale)) {
            return "您的 Meirong Shop 验证码：" + otp + "，5 分钟内有效，请勿泄露。";
        }
        return "Your Meirong Shop verification code: " + otp + ". Valid for 5 minutes.";
    }

    private String codeKey(String phone) { return "otp:" + phone + ":code"; }
    private String cooldownKey(String phone) { return "otp:" + phone + ":cooldown"; }
    private String attemptKey(String phone) { return "otp:" + phone + ":attempt"; }
    private String lockoutKey(String phone) { return "otp:" + phone + ":lockout"; }
    private String dailyKey(String phone) { return "otp:" + phone + ":daily"; }
}
