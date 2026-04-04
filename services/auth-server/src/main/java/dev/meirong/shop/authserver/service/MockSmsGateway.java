package dev.meirong.shop.authserver.service;

import dev.meirong.shop.authserver.config.AuthSmsProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "shop.auth-sms", name = "gateway", havingValue = "mock", matchIfMissing = true)
public class MockSmsGateway implements SmsGateway {

    private static final Logger log = LoggerFactory.getLogger(MockSmsGateway.class);

    public MockSmsGateway(AuthSmsProperties properties) {
        log.info("Using mock SMS gateway: {}", properties.gateway());
    }

    @Override
    public void send(String to, String message) {
        log.info("Mock SMS to {} => {}", to, message);
    }
}
