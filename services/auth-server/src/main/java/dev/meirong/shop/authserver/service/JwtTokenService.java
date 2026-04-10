package dev.meirong.shop.authserver.service;

import dev.meirong.shop.authserver.config.AuthProperties;
import dev.meirong.shop.authserver.domain.UserAccountEntity;
import dev.meirong.shop.contracts.auth.AuthApi;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.nimbusds.jose.proc.SecurityContext;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Service;

@Service
public class JwtTokenService {

    private final AuthProperties properties;
    private final JwtEncoder jwtEncoder;

    public JwtTokenService(AuthProperties properties) {
        this.properties = properties;
        SecretKey secretKey = new SecretKeySpec(properties.secret().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        this.jwtEncoder = new NimbusJwtEncoder(new ImmutableSecret<SecurityContext>(secretKey));
    }

    public AuthApi.TokenResponse issueToken(DemoUserDirectory.UserProfile profile) {
        return issueTokenInternal(profile.principalId(), profile.username(),
                profile.displayName(), profile.roles(), profile.portal(), false);
    }

    public AuthApi.TokenResponse issueToken(UserAccountEntity account) {
        return issueTokenInternal(account.getPrincipalId(), account.getUsername(),
                account.getDisplayName(), account.getRoleList(), account.getPortal(), false);
    }

    public AuthApi.TokenResponse issueToken(UserAccountEntity account, boolean newUser) {
        return issueTokenInternal(account.getPrincipalId(), account.getUsername(),
                account.getDisplayName(), account.getRoleList(), account.getPortal(), newUser);
    }

    private AuthApi.TokenResponse issueTokenInternal(String principalId, String username,
                                                     String displayName, List<String> roles, String portal,
                                                     boolean newUser) {
        Instant issuedAt = Instant.now();
        Instant expiresAt = issuedAt.plus(properties.tokenTtl());
        JwtClaimsSet claimsSet = JwtClaimsSet.builder()
                .issuer(properties.issuer())
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .subject(principalId)
                .claim("username", username)
                .claim("displayName", displayName)
                .claim("principalId", principalId)
                .claim("roles", roles)
                .claim("portal", portal)
                .build();
        JwsHeader jwsHeader = JwsHeader.with(MacAlgorithm.HS256).type("JWT").build();
        String token = jwtEncoder.encode(JwtEncoderParameters.from(jwsHeader, claimsSet)).getTokenValue();
        return new AuthApi.TokenResponse(
                token,
                "Bearer",
                expiresAt,
                username,
                displayName,
                principalId,
                List.copyOf(roles),
                portal,
                newUser
        );
    }

    public JwtDecoder jwtDecoder() {
        SecretKey secretKey = new SecretKeySpec(properties.secret().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        return NimbusJwtDecoder.withSecretKey(secretKey).macAlgorithm(MacAlgorithm.HS256).build();
    }
}
