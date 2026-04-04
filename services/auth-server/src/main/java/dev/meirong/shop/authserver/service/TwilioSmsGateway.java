package dev.meirong.shop.authserver.service;

import dev.meirong.shop.authserver.config.AuthSmsProperties;
import dev.meirong.shop.common.error.BusinessException;
import dev.meirong.shop.common.error.CommonErrorCode;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;

@Component
@ConditionalOnProperty(prefix = "shop.auth-sms", name = "gateway", havingValue = "twilio")
public class TwilioSmsGateway implements SmsGateway {

    private final RestClient restClient;
    private final AuthSmsProperties properties;

    public TwilioSmsGateway(RestClient.Builder builder, AuthSmsProperties properties) {
        this.restClient = builder.build();
        this.properties = properties;
    }

    @Override
    public void send(String to, String message) {
        if (blank(properties.twilioAccountSid()) || blank(properties.twilioAuthToken()) || blank(properties.twilioFromNumber())) {
            throw new BusinessException(CommonErrorCode.PAYMENT_PROVIDER_DISABLED, "Twilio SMS gateway is not configured");
        }
        var form = new LinkedMultiValueMap<String, String>();
        form.add("To", to);
        form.add("From", properties.twilioFromNumber());
        form.add("Body", message);
        restClient.post()
                .uri("https://api.twilio.com/2010-04-01/Accounts/" + properties.twilioAccountSid() + "/Messages.json")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .headers(headers -> headers.setBasicAuth(properties.twilioAccountSid(), properties.twilioAuthToken()))
                .body(form)
                .retrieve()
                .toBodilessEntity();
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
