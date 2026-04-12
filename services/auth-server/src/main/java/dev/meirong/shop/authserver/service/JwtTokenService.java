package dev.meirong.shop.authserver.service;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import dev.meirong.shop.authserver.config.AuthProperties;
import dev.meirong.shop.authserver.domain.UserAccountEntity;
import dev.meirong.shop.contracts.auth.AuthApi;
import java.io.IOException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.List;
import org.springframework.security.converter.RsaKeyConverters;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
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

    private static final String KEY_ID = "shop-auth-key";

    private final AuthProperties properties;
    private final JwtEncoder jwtEncoder;
    private final JwtDecoder jwtDecoder;
    private final JWKSet jwkSet;

    public JwtTokenService(AuthProperties properties) {
        this.properties = properties;
        try {
            RSAPrivateKey privateKey = RsaKeyConverters.pkcs8().convert(properties.rsaPrivateKey().getInputStream());
            RSAPublicKey publicKey = RsaKeyConverters.x509().convert(properties.rsaPublicKey().getInputStream());

            RSAKey rsaKey = new RSAKey.Builder(publicKey).privateKey(privateKey).keyID(KEY_ID).build();
            this.jwkSet = new JWKSet(rsaKey);
            this.jwtEncoder = new NimbusJwtEncoder(new ImmutableJWKSet<>(this.jwkSet));
            this.jwtDecoder = NimbusJwtDecoder.withPublicKey(publicKey).build();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load RSA keys for JWT signing", e);
        }
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
        JwsHeader jwsHeader = JwsHeader.with(SignatureAlgorithm.RS256).keyId(KEY_ID).type("JWT").build();
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
        return this.jwtDecoder;
    }

    public JWKSet getJwkSet() {
        return this.jwkSet;
    }
}
