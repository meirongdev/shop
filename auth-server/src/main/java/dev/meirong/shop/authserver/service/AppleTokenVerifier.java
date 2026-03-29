package dev.meirong.shop.authserver.service;

import dev.meirong.shop.authserver.config.AuthProperties;
import dev.meirong.shop.common.error.BusinessException;
import dev.meirong.shop.common.error.CommonErrorCode;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;

@Component
public class AppleTokenVerifier {

    private static final String APPLE_JWKS_URI = "https://appleid.apple.com/auth/keys";
    private static final String APPLE_ISSUER = "https://appleid.apple.com";

    private final AuthProperties properties;
    private final JwtDecoder appleJwtDecoder;

    @Autowired
    public AppleTokenVerifier(AuthProperties properties) {
        this(properties, NimbusJwtDecoder.withJwkSetUri(APPLE_JWKS_URI).build());
    }

    AppleTokenVerifier(AuthProperties properties, JwtDecoder appleJwtDecoder) {
        this.properties = properties;
        this.appleJwtDecoder = appleJwtDecoder;
    }

    public AppleUserInfo verify(String idToken, String nonce) {
        Jwt jwt;
        try {
            jwt = appleJwtDecoder.decode(idToken);
        } catch (JwtException exception) {
            throw new BusinessException(CommonErrorCode.UNAUTHORIZED, "Invalid Apple ID token");
        }
        String issuer = jwt.getIssuer() != null ? jwt.getIssuer().toString() : "";
        if (!APPLE_ISSUER.equals(issuer)) {
            throw new BusinessException(CommonErrorCode.UNAUTHORIZED, "Invalid Apple token issuer");
        }
        List<String> audience = jwt.getAudience();
        if (audience == null || !audience.contains(properties.appleClientId())) {
            throw new BusinessException(CommonErrorCode.UNAUTHORIZED, "Apple token audience mismatch");
        }
        String tokenNonce = jwt.getClaimAsString("nonce");
        if (nonce != null && !nonce.equals(tokenNonce)) {
            throw new BusinessException(CommonErrorCode.UNAUTHORIZED, "Apple token nonce mismatch");
        }
        Boolean emailVerified = jwt.getClaim("email_verified");
        if (emailVerified != null && !emailVerified) {
            throw new BusinessException(CommonErrorCode.UNAUTHORIZED, "Apple email not verified");
        }
        return new AppleUserInfo(
                jwt.getSubject(),
                jwt.getClaimAsString("email"),
                jwt.getClaimAsString("email"),
                tokenNonce);
    }

    public record AppleUserInfo(String sub, String email, String displayName, String nonce) {
    }
}
