package dev.meirong.shop.authserver.service;

import dev.meirong.shop.authserver.config.AuthProperties;
import dev.meirong.shop.authserver.domain.SocialAccountEntity;
import dev.meirong.shop.authserver.domain.SocialAccountRepository;
import dev.meirong.shop.authserver.domain.UserAccountEntity;
import dev.meirong.shop.authserver.domain.UserAccountRepository;
import dev.meirong.shop.common.error.BusinessException;
import dev.meirong.shop.contracts.auth.AuthApi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SocialLoginServiceTest {

    @Mock private UserAccountRepository userAccountRepository;
    @Mock private SocialAccountRepository socialAccountRepository;
    @Mock private GoogleTokenVerifier googleTokenVerifier;
    @Mock private BuyerAccountProvisioningService buyerAccountProvisioningService;
    @Mock private AppleTokenVerifier appleTokenVerifier;

    private SocialLoginService socialLoginService;

    @BeforeEach
    void setUp() {
        AuthProperties props = new AuthProperties("test-issuer",
                "change-this-to-a-32-byte-demo-secret",
                Duration.ofHours(1),
                "test-google-client-id",
                "test-apple-client-id",
                "http://profile-service:8080",
                "buyer.registered.v1");
        JwtTokenService jwtTokenService = new JwtTokenService(props);
        socialLoginService = new SocialLoginService(props, userAccountRepository,
                socialAccountRepository, jwtTokenService, buyerAccountProvisioningService, googleTokenVerifier, appleTokenVerifier);
    }

    @Test
    void loginWithGoogle_newUser_createsAccountAndReturnsToken() {
        GoogleTokenVerifier.GoogleUserInfo googleInfo =
                new GoogleTokenVerifier.GoogleUserInfo("google-sub-123", "alice@gmail.com", "Alice", "https://pic.url");
        when(googleTokenVerifier.verify("fake-id-token")).thenReturn(googleInfo);
        when(socialAccountRepository.findByProviderAndProviderUserId("google", "google-sub-123"))
                .thenReturn(Optional.empty());
        when(userAccountRepository.findByEmail("alice@gmail.com")).thenReturn(Optional.empty());
        when(buyerAccountProvisioningService.provisionSocialBuyer("alice@gmail.com", "Alice", "google"))
                .thenReturn(UserAccountEntity.createForSocialLogin("alice@gmail.com", "Alice", "google"));

        AuthApi.TokenResponse response = socialLoginService.loginWithGoogle("fake-id-token", "buyer");

        assertThat(response.accessToken()).isNotBlank();
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.portal()).isEqualTo("buyer");
        assertThat(response.roles()).contains("ROLE_BUYER");

        verify(buyerAccountProvisioningService).provisionSocialBuyer("alice@gmail.com", "Alice", "google");
        verify(socialAccountRepository).save(any(SocialAccountEntity.class));
    }

    @Test
    void loginWithGoogle_existingGoogleAccount_returnsTokenWithoutCreating() {
        GoogleTokenVerifier.GoogleUserInfo googleInfo =
                new GoogleTokenVerifier.GoogleUserInfo("google-sub-456", "bob@gmail.com", "Bob", null);
        when(googleTokenVerifier.verify("id-token")).thenReturn(googleInfo);

        UserAccountEntity existingUser = UserAccountEntity.createForSocialLogin("bob@gmail.com", "Bob", "google");
        SocialAccountEntity existingSocial = SocialAccountEntity.create(1L, "google", "google-sub-456", "bob@gmail.com", "Bob", null);
        when(socialAccountRepository.findByProviderAndProviderUserId("google", "google-sub-456"))
                .thenReturn(Optional.of(existingSocial));
        when(userAccountRepository.findById(1L)).thenReturn(Optional.of(existingUser));

        AuthApi.TokenResponse response = socialLoginService.loginWithGoogle("id-token", "buyer");

        assertThat(response.accessToken()).isNotBlank();
        assertThat(response.displayName()).isEqualTo("Bob");
    }

    @Test
    void loginWithGoogle_existingEmailUser_linksAndReturnsToken() {
        GoogleTokenVerifier.GoogleUserInfo googleInfo =
                new GoogleTokenVerifier.GoogleUserInfo("google-sub-789", "existing@gmail.com", "Existing", null);
        when(googleTokenVerifier.verify("token")).thenReturn(googleInfo);
        when(socialAccountRepository.findByProviderAndProviderUserId("google", "google-sub-789"))
                .thenReturn(Optional.empty());

        UserAccountEntity existingUser = UserAccountEntity.createForSocialLogin("existing@gmail.com", "Existing User", "manual");
        when(userAccountRepository.findByEmail("existing@gmail.com")).thenReturn(Optional.of(existingUser));

        AuthApi.TokenResponse response = socialLoginService.loginWithGoogle("token", "buyer");

        assertThat(response.accessToken()).isNotBlank();
        verify(socialAccountRepository).save(any(SocialAccountEntity.class));
    }

    @Test
    void bindSocialAccount_alreadyLinked_throwsError() {
        UserAccountEntity user = UserAccountEntity.createForSocialLogin("user@test.com", "User", "manual");
        when(userAccountRepository.findByPrincipalId("player-001")).thenReturn(Optional.of(user));

        GoogleTokenVerifier.GoogleUserInfo googleInfo =
                new GoogleTokenVerifier.GoogleUserInfo("google-sub-existing", "other@gmail.com", "Other", null);
        when(googleTokenVerifier.verify("token")).thenReturn(googleInfo);
        when(socialAccountRepository.findByProviderAndProviderUserId("google", "google-sub-existing"))
                .thenReturn(Optional.of(SocialAccountEntity.create(999L, "google", "google-sub-existing", "other@gmail.com", null, null)));

        assertThatThrownBy(() -> socialLoginService.bindSocialAccount("player-001", "google", "token"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("already linked");
    }

    @Test
    void listLinkedAccounts_returnsAllProviders() {
        UserAccountEntity user = UserAccountEntity.createForSocialLogin("user@test.com", "User", "manual");
        when(userAccountRepository.findByPrincipalId("player-001")).thenReturn(Optional.of(user));

        List<SocialAccountEntity> socials = List.of(
                SocialAccountEntity.create(1L, "google", "g-123", "user@gmail.com", "User G", null)
        );
        when(socialAccountRepository.findByUserAccountId(any())).thenReturn(socials);

        List<AuthApi.SocialAccountInfo> result = socialLoginService.listLinkedAccounts("player-001");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).provider()).isEqualTo("google");
        assertThat(result.get(0).email()).isEqualTo("user@gmail.com");
    }
}
