package dev.meirong.shop.authserver.service;

import dev.meirong.shop.authserver.config.AuthProperties;
import dev.meirong.shop.common.error.BusinessException;
import dev.meirong.shop.common.error.CommonErrorCode;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;

/**
 * Verifies Google OAuth2 ID tokens using Google's public JWKS endpoint.
 * In production, the token is verified against Google's RSA public keys.
 * The audience claim must match the configured Google client ID.
 */
@Component
public class GoogleTokenVerifier {

    private static final Logger log = LoggerFactory.getLogger(GoogleTokenVerifier.class);
    private static final String GOOGLE_JWKS_URI = "https://www.googleapis.com/oauth2/v3/certs";
    private static final List<String> GOOGLE_ISSUERS = List.of("accounts.google.com", "https://accounts.google.com");

    private final AuthProperties authProperties;
    private final JwtDecoder googleJwtDecoder;

    @Autowired
    public GoogleTokenVerifier(AuthProperties authProperties) {
        this.authProperties = authProperties;
        this.googleJwtDecoder = NimbusJwtDecoder.withJwkSetUri(GOOGLE_JWKS_URI).build();
    }

    // Constructor for testing with custom decoder
    GoogleTokenVerifier(AuthProperties authProperties, JwtDecoder googleJwtDecoder) {
        this.authProperties = authProperties;
        this.googleJwtDecoder = googleJwtDecoder;
    }

    public GoogleUserInfo verify(String idToken) {
        Jwt jwt;
        try {
            jwt = googleJwtDecoder.decode(idToken);
        } catch (JwtException e) {
            log.warn("Google ID token verification failed: {}", e.getMessage());
            throw new BusinessException(CommonErrorCode.UNAUTHORIZED, "Invalid Google ID token");
        }

        // Validate issuer
        String issuer = jwt.getIssuer() != null ? jwt.getIssuer().toString() : "";
        if (!GOOGLE_ISSUERS.contains(issuer)) {
            throw new BusinessException(CommonErrorCode.UNAUTHORIZED, "Invalid token issuer");
        }

        // Validate audience matches our Google client ID
        List<String> audience = jwt.getAudience();
        if (audience == null || !audience.contains(authProperties.googleClientId())) {
            throw new BusinessException(CommonErrorCode.UNAUTHORIZED, "Token audience mismatch");
        }

        String sub = jwt.getSubject();
        String email = jwt.getClaimAsString("email");
        Boolean emailVerified = jwt.getClaim("email_verified");
        String name = jwt.getClaimAsString("name");
        String picture = jwt.getClaimAsString("picture");

        if (sub == null || email == null) {
            throw new BusinessException(CommonErrorCode.UNAUTHORIZED, "Incomplete Google user info");
        }

        if (emailVerified != null && !emailVerified) {
            throw new BusinessException(CommonErrorCode.UNAUTHORIZED, "Google email not verified");
        }

        return new GoogleUserInfo(sub, email, name, picture);
    }

    public record GoogleUserInfo(String sub, String email, String name, String picture) {
    }
}
