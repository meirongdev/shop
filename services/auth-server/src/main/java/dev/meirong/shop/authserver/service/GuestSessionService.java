package dev.meirong.shop.authserver.service;

import dev.meirong.shop.common.error.BusinessException;
import dev.meirong.shop.common.error.CommonErrorCode;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class GuestSessionService {

    public DemoUserDirectory.UserProfile issueGuestProfile(String portal) {
        if (!"buyer".equalsIgnoreCase(portal)) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN, "Guest access is only available for buyer portal");
        }
        String suffix = UUID.randomUUID().toString().replace("-", "");
        return new DemoUserDirectory.UserProfile(
                "guest." + suffix.substring(0, 12),
                "Guest Buyer",
                "guest-buyer-" + suffix,
                List.of("ROLE_BUYER_GUEST"),
                "buyer"
        );
    }
}
