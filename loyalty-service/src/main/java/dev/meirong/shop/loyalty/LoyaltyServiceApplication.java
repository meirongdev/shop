package dev.meirong.shop.loyalty;

import dev.meirong.shop.loyalty.config.LoyaltyProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@EnableConfigurationProperties(LoyaltyProperties.class)
@SpringBootApplication(scanBasePackages = "dev.meirong.shop")
public class LoyaltyServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(LoyaltyServiceApplication.class, args);
    }
}
