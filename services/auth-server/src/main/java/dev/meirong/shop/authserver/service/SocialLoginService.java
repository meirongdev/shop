package dev.meirong.shop.authserver.service;

import dev.meirong.shop.authserver.config.AuthProperties;
import dev.meirong.shop.authserver.domain.SocialAccountEntity;
import dev.meirong.shop.authserver.domain.SocialAccountRepository;
import dev.meirong.shop.authserver.domain.UserAccountEntity;
import dev.meirong.shop.authserver.domain.UserAccountRepository;
import dev.meirong.shop.common.error.BusinessException;
import dev.meirong.shop.common.error.CommonErrorCode;
import dev.meirong.shop.contracts.api.AuthApi;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SocialLoginService {

    private static final Logger log = LoggerFactory.getLogger(SocialLoginService.class);

    private final AuthProperties authProperties;
    private final UserAccountRepository userAccountRepository;
    private final SocialAccountRepository socialAccountRepository;
    private final JwtTokenService jwtTokenService;
    private final BuyerAccountProvisioningService buyerAccountProvisioningService;
    private final GoogleTokenVerifier googleTokenVerifier;
    private final AppleTokenVerifier appleTokenVerifier;

    public SocialLoginService(AuthProperties authProperties,
                              UserAccountRepository userAccountRepository,
                              SocialAccountRepository socialAccountRepository,
                              JwtTokenService jwtTokenService,
                              BuyerAccountProvisioningService buyerAccountProvisioningService,
                              GoogleTokenVerifier googleTokenVerifier,
                              AppleTokenVerifier appleTokenVerifier) {
        this.authProperties = authProperties;
        this.userAccountRepository = userAccountRepository;
        this.socialAccountRepository = socialAccountRepository;
        this.jwtTokenService = jwtTokenService;
        this.buyerAccountProvisioningService = buyerAccountProvisioningService;
        this.googleTokenVerifier = googleTokenVerifier;
        this.appleTokenVerifier = appleTokenVerifier;
    }

    @Transactional
    public AuthApi.TokenResponse loginWithGoogle(String idToken, String portal) {
        GoogleTokenVerifier.GoogleUserInfo userInfo = googleTokenVerifier.verify(idToken);

        SocialAccountEntity socialAccount = socialAccountRepository
                .findByProviderAndProviderUserId("google", userInfo.sub())
                .orElse(null);

        UserAccountEntity userAccount;
        boolean newUser = false;
        if (socialAccount != null) {
            userAccount = userAccountRepository.findById(socialAccount.getUserAccountId())
                    .orElseThrow(() -> new BusinessException(CommonErrorCode.INTERNAL_ERROR, "Linked user account not found"));
        } else {
            // Auto-register: find existing user by email or create new
            userAccount = userAccountRepository.findByEmail(userInfo.email()).orElse(null);
            if (userAccount == null) {
                userAccount = buyerAccountProvisioningService.provisionSocialBuyer(userInfo.email(), userInfo.name(), "google");
                newUser = true;
                log.info("Created new user via Google OAuth2: principalId={} email={}", userAccount.getPrincipalId(), userInfo.email());
            }
            SocialAccountEntity newSocial = SocialAccountEntity.create(
                    userAccount.getId(), "google", userInfo.sub(),
                    userInfo.email(), userInfo.name(), userInfo.picture());
            socialAccountRepository.save(newSocial);
            log.info("Linked Google account to user: principalId={} googleSub={}", userAccount.getPrincipalId(), userInfo.sub());
        }

        return jwtTokenService.issueToken(userAccount, newUser);
    }

    @Transactional
    public AuthApi.TokenResponse loginWithApple(String idToken, String nonce, String portal) {
        AppleTokenVerifier.AppleUserInfo userInfo = appleTokenVerifier.verify(idToken, nonce);
        SocialAccountEntity socialAccount = socialAccountRepository
                .findByProviderAndProviderUserId("apple", userInfo.sub())
                .orElse(null);

        UserAccountEntity userAccount;
        boolean newUser = false;
        if (socialAccount != null) {
            userAccount = userAccountRepository.findById(socialAccount.getUserAccountId())
                    .orElseThrow(() -> new BusinessException(CommonErrorCode.INTERNAL_ERROR, "Linked user account not found"));
        } else {
            userAccount = userInfo.email() == null ? null : userAccountRepository.findByEmail(userInfo.email()).orElse(null);
            if (userAccount == null) {
                userAccount = buyerAccountProvisioningService.provisionSocialBuyer(
                        userInfo.email() != null ? userInfo.email() : "apple." + userInfo.sub() + "@apple.local",
                        userInfo.displayName() != null ? userInfo.displayName() : "Apple User",
                        "apple");
                newUser = true;
            }
            socialAccountRepository.save(SocialAccountEntity.create(
                    userAccount.getId(), "apple", userInfo.sub(), userInfo.email(), userInfo.displayName(), null));
        }
        return jwtTokenService.issueToken(userAccount, newUser);
    }

    @Transactional
    public void bindSocialAccount(String principalId, String provider, String idToken) {
        UserAccountEntity userAccount = userAccountRepository.findByPrincipalId(principalId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND, "User account not found"));

        Map.Entry<String, String> providerInfo = verifyProviderToken(provider, idToken);
        String providerUserId = providerInfo.getKey();
        String email = providerInfo.getValue();

        socialAccountRepository.findByProviderAndProviderUserId(provider, providerUserId)
                .ifPresent(existing -> {
                    throw new BusinessException(CommonErrorCode.VALIDATION_ERROR,
                            "This " + provider + " account is already linked to another user");
                });

        SocialAccountEntity socialAccount = SocialAccountEntity.create(
                userAccount.getId(), provider, providerUserId, email, null, null);
        socialAccountRepository.save(socialAccount);
        log.info("Bound {} account to user: principalId={}", provider, principalId);
    }

    @Transactional(readOnly = true)
    public List<AuthApi.SocialAccountInfo> listLinkedAccounts(String principalId) {
        UserAccountEntity userAccount = userAccountRepository.findByPrincipalId(principalId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND, "User account not found"));

        return socialAccountRepository.findByUserAccountId(userAccount.getId())
                .stream()
                .map(sa -> new AuthApi.SocialAccountInfo(sa.getProvider(), sa.getProviderUserId(), sa.getEmail(), sa.getName()))
                .toList();
    }

    private Map.Entry<String, String> verifyProviderToken(String provider, String idToken) {
        if ("google".equalsIgnoreCase(provider)) {
            GoogleTokenVerifier.GoogleUserInfo info = googleTokenVerifier.verify(idToken);
            return Map.entry(info.sub(), info.email());
        }
        if ("apple".equalsIgnoreCase(provider)) {
            AppleTokenVerifier.AppleUserInfo info = appleTokenVerifier.verify(idToken, null);
            return Map.entry(info.sub(), info.email());
        }
        throw new BusinessException(CommonErrorCode.VALIDATION_ERROR, "Unsupported provider: " + provider);
    }
}
