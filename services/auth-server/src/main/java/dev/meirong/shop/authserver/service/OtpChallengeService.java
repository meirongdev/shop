package dev.meirong.shop.authserver.service;

import dev.meirong.shop.authserver.config.AuthOtpProperties;
import dev.meirong.shop.authserver.domain.UserAccountEntity;
import dev.meirong.shop.authserver.domain.UserAccountRepository;
import dev.meirong.shop.common.error.BusinessException;
import dev.meirong.shop.common.error.CommonErrorCode;
import dev.meirong.shop.contracts.auth.AuthApi;
import java.security.SecureRandom;
import java.util.concurrent.TimeUnit;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class OtpChallengeService {

    private final SecureRandom secureRandom = new SecureRandom();
    private final StringRedisTemplate redisTemplate;
    private final AuthOtpProperties properties;
    private final SmsGateway smsGateway;
    private final UserAccountRepository userAccountRepository;
    private final BuyerAccountProvisioningService buyerAccountProvisioningService;
    private final JwtTokenService jwtTokenService;

    public OtpChallengeService(StringRedisTemplate redisTemplate,
                               AuthOtpProperties properties,
                               SmsGateway smsGateway,
                               UserAccountRepository userAccountRepository,
                               BuyerAccountProvisioningService buyerAccountProvisioningService,
                               JwtTokenService jwtTokenService) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
        this.smsGateway = smsGateway;
        this.userAccountRepository = userAccountRepository;
        this.buyerAccountProvisioningService = buyerAccountProvisioningService;
        this.jwtTokenService = jwtTokenService;
    }

    public AuthApi.OtpSendResponse sendOtp(AuthApi.OtpSendRequest request) {
        String phone = request.phoneNumber();
        if (Boolean.TRUE.equals(redisTemplate.hasKey(cooldownKey(phone)))) {
            throw new BusinessException(CommonErrorCode.VALIDATION_ERROR, "OTP cooldown active");
        }
        Long dailyCount = redisTemplate.opsForValue().increment(dailyKey(phone));
        if (dailyCount != null && dailyCount == 1L) {
            redisTemplate.expire(dailyKey(phone), 1, TimeUnit.DAYS);
        }
        if (dailyCount != null && dailyCount > properties.dailyLimit()) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN, "OTP daily limit exceeded");
        }
        String otp = String.format("%06d", secureRandom.nextInt(1_000_000));
        redisTemplate.opsForValue().set(codeKey(phone), otp, properties.codeTtl());
        redisTemplate.opsForValue().set(cooldownKey(phone), "1", properties.cooldownTtl());
        smsGateway.send(phone, localizedMessage(otp, request.locale()));
        return new AuthApi.OtpSendResponse((int) properties.codeTtl().toSeconds(), (int) properties.cooldownTtl().toSeconds());
    }

    public AuthApi.TokenResponse verifyOtp(AuthApi.OtpVerifyRequest request) {
        String phone = request.phoneNumber();
        if (Boolean.TRUE.equals(redisTemplate.hasKey(lockoutKey(phone)))) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN, "OTP verification locked");
        }
        String expectedCode = redisTemplate.opsForValue().get(codeKey(phone));
        if (expectedCode == null) {
            throw new BusinessException(CommonErrorCode.VALIDATION_ERROR, "OTP expired");
        }
        if (!expectedCode.equals(request.otp())) {
            Long attempts = redisTemplate.opsForValue().increment(attemptKey(phone));
            if (attempts != null && attempts == 1L) {
                redisTemplate.expire(attemptKey(phone), properties.lockoutTtl());
            }
            if (attempts != null && attempts >= properties.maxAttempts()) {
                redisTemplate.opsForValue().set(lockoutKey(phone), "1", properties.lockoutTtl());
                throw new BusinessException(CommonErrorCode.FORBIDDEN, "OTP verification locked");
            }
            throw new BusinessException(CommonErrorCode.VALIDATION_ERROR, "OTP invalid");
        }
        redisTemplate.delete(codeKey(phone));
        redisTemplate.delete(attemptKey(phone));
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
