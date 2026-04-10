package dev.meirong.shop.authserver.service;

import dev.meirong.shop.authserver.domain.UserAccountRepository;
import dev.meirong.shop.contracts.auth.AuthApi;
import org.springframework.stereotype.Service;

@Service
public class AuthenticationApplicationService {

    private final UserAccountRepository userAccountRepository;
    private final DemoUserDirectory demoUserDirectory;
    private final JwtTokenService jwtTokenService;

    public AuthenticationApplicationService(UserAccountRepository userAccountRepository,
                                            DemoUserDirectory demoUserDirectory,
                                            JwtTokenService jwtTokenService) {
        this.userAccountRepository = userAccountRepository;
        this.demoUserDirectory = demoUserDirectory;
        this.jwtTokenService = jwtTokenService;
    }

    public AuthApi.TokenResponse issueLoginToken(String username, String portal) {
        return userAccountRepository.findByUsername(username)
                .map(jwtTokenService::issueToken)
                .orElseGet(() -> jwtTokenService.issueToken(
                        demoUserDirectory.requirePortalAccess(username, portal)));
    }
}
