package dev.meirong.shop.authserver.service;

import dev.meirong.shop.common.error.BusinessException;
import dev.meirong.shop.common.error.CommonErrorCode;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class DemoUserDirectory {

    private final Map<String, UserProfile> profiles = Map.of(
            "buyer.demo", new UserProfile("buyer.demo", "Buyer Demo", "player-1001", List.of("ROLE_BUYER"), "buyer"),
            "buyer.vip", new UserProfile("buyer.vip", "Buyer VIP", "player-1002", List.of("ROLE_BUYER"), "buyer"),
            "seller.demo", new UserProfile("seller.demo", "Seller Demo", "seller-2001", List.of("ROLE_SELLER"), "seller")
    );

    public UserProfile requireProfile(String username) {
        UserProfile profile = profiles.get(username);
        if (profile == null) {
            throw new BusinessException(CommonErrorCode.UNAUTHORIZED, "Unknown user");
        }
        return profile;
    }

    public UserProfile requirePortalAccess(String username, String portal) {
        UserProfile profile = requireProfile(username);
        if (!profile.portal().equalsIgnoreCase(portal)) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN, "User cannot access portal: " + portal);
        }
        return profile;
    }

    public Map<String, UserProfile> profiles() {
        return profiles;
    }

    public record UserProfile(String username,
                              String displayName,
                              String principalId,
                              List<String> roles,
                              String portal) {
    }
}
