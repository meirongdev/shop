package dev.meirong.shop.authserver.service;

import dev.meirong.shop.authserver.domain.UserAccountEntity;
import dev.meirong.shop.authserver.domain.UserAccountRepository;
import dev.meirong.shop.common.error.BusinessException;
import dev.meirong.shop.common.error.CommonErrorCode;
import dev.meirong.shop.contracts.api.AuthApi;
import dev.meirong.shop.contracts.api.ProfileInternalApi;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BuyerAccountProvisioningService {

    private final UserAccountRepository userAccountRepository;
    private final PasswordEncoder passwordEncoder;
    private final ProfileRegistrationClient profileRegistrationClient;
    private final UserRegisteredEventPublisher userRegisteredEventPublisher;

    public BuyerAccountProvisioningService(UserAccountRepository userAccountRepository,
                                           PasswordEncoder passwordEncoder,
                                           ProfileRegistrationClient profileRegistrationClient,
                                           UserRegisteredEventPublisher userRegisteredEventPublisher) {
        this.userAccountRepository = userAccountRepository;
        this.passwordEncoder = passwordEncoder;
        this.profileRegistrationClient = profileRegistrationClient;
        this.userRegisteredEventPublisher = userRegisteredEventPublisher;
    }

    @Transactional
    public UserAccountEntity registerBuyer(AuthApi.BuyerRegisterRequest request) {
        userAccountRepository.findByUsername(request.username())
                .ifPresent(existing -> {
                    throw new BusinessException(CommonErrorCode.VALIDATION_ERROR, "Username already exists");
                });
        userAccountRepository.findByEmail(request.email())
                .ifPresent(existing -> {
                    throw new BusinessException(CommonErrorCode.VALIDATION_ERROR, "Email already exists");
                });

        UserAccountEntity account = userAccountRepository.save(UserAccountEntity.createForBuyerRegistration(
                request.username(),
                request.email(),
                request.username(),
                passwordEncoder.encode(request.password())));
        try {
            profileRegistrationClient.registerBuyer(new ProfileInternalApi.RegisterBuyerRequest(
                    account.getPrincipalId(),
                    account.getUsername(),
                    account.getDisplayName(),
                    account.getEmail(),
                    request.inviteCode()));
            userRegisteredEventPublisher.publish(account);
            return account;
        } catch (RuntimeException exception) {
            userAccountRepository.delete(account);
            throw exception;
        }
    }

    @Transactional
    public UserAccountEntity provisionSocialBuyer(String email, String displayName, String provider) {
        UserAccountEntity account = userAccountRepository.save(UserAccountEntity.createForSocialLogin(email, displayName, provider));
        try {
            profileRegistrationClient.registerBuyer(new ProfileInternalApi.RegisterBuyerRequest(
                    account.getPrincipalId(),
                    account.getUsername(),
                    account.getDisplayName(),
                    account.getEmail(),
                    null));
            userRegisteredEventPublisher.publish(account);
            return account;
        } catch (RuntimeException exception) {
            userAccountRepository.delete(account);
            throw exception;
        }
    }

    @Transactional
    public UserAccountEntity provisionPhoneBuyer(String phoneNumber) {
        String last4 = phoneNumber.length() > 4 ? phoneNumber.substring(phoneNumber.length() - 4) : phoneNumber;
        String username = "buyer." + last4 + "." + java.util.UUID.randomUUID().toString().substring(0, 4);
        UserAccountEntity account = UserAccountEntity.createForPhoneLogin(phoneNumber, username, "Buyer " + last4);
        account = userAccountRepository.save(account);
        try {
            profileRegistrationClient.registerBuyer(new ProfileInternalApi.RegisterBuyerRequest(
                    account.getPrincipalId(),
                    account.getUsername(),
                    account.getDisplayName(),
                    account.getEmail(),
                    null));
            userRegisteredEventPublisher.publish(account);
            return account;
        } catch (RuntimeException exception) {
            userAccountRepository.delete(account);
            throw exception;
        }
    }
}
