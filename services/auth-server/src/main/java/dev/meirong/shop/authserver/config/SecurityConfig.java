package dev.meirong.shop.authserver.config;

import dev.meirong.shop.authserver.service.DemoUserDirectory;
import dev.meirong.shop.authserver.service.JwtTokenService;
import dev.meirong.shop.authserver.domain.UserAccountEntity;
import dev.meirong.shop.authserver.domain.UserAccountRepository;
import dev.meirong.shop.contracts.api.AuthApi;
import java.util.List;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({AuthProperties.class, AuthOtpProperties.class, AuthSmsProperties.class})
public class SecurityConfig {

    private static final String DEMO_PASSWORD_HASH =
            "{bcrypt}$2a$10$lV1eEiDHx.5RvAkc5xs0bOYeh22uCNkTqcwn/4D5jJJ/WjgiuEU0u";
    private static final String LEGACY_DEMO_PASSWORD_HASH_PREFIX =
            "{bcrypt}$2a$10$dummyhashnotusedfordemousers";

    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    UserDetailsService userDetailsService(DemoUserDirectory userDirectory,
                                         UserAccountRepository userAccountRepository) {
        return username -> {
            UserAccountEntity account = userAccountRepository.findByUsername(username).orElse(null);
            if (account != null && hasUsablePasswordHash(account.getPasswordHash())) {
                return User.withUsername(account.getUsername())
                        .password(account.getPasswordHash())
                        .authorities(account.getRoleList().toArray(String[]::new))
                        .build();
            }
            DemoUserDirectory.UserProfile profile = userDirectory.requireProfile(username);
            return User.withUsername(profile.username())
                    .password(DEMO_PASSWORD_HASH)
                    .authorities(profile.roles().toArray(String[]::new))
                    .build();
        };
    }

    private static boolean hasUsablePasswordHash(String passwordHash) {
        return passwordHash != null
                && !passwordHash.isBlank()
                && !passwordHash.startsWith(LEGACY_DEMO_PASSWORD_HASH_PREFIX);
    }

    @Bean
    AuthenticationManager authenticationManager(HttpSecurity http,
                                                UserDetailsService userDetailsService,
                                                PasswordEncoder passwordEncoder) throws Exception {
        AuthenticationManagerBuilder builder = http.getSharedObject(AuthenticationManagerBuilder.class);
        builder.userDetailsService(userDetailsService).passwordEncoder(passwordEncoder);
        return builder.build();
    }

    @Bean
    JwtDecoder jwtDecoder(JwtTokenService jwtTokenService) {
        return jwtTokenService.jwtDecoder();
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, JwtDecoder jwtDecoder) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(
                                "/actuator/**",
                                "/swagger-ui.html",
                                "/swagger-ui/**",
                                "/v3/api-docs/**",
                                AuthApi.LOGIN,
                                AuthApi.GUEST,
                                AuthApi.OAUTH2_GOOGLE,
                                AuthApi.OAUTH2_APPLE,
                                AuthApi.BUYER_REGISTER,
                                AuthApi.OTP_SEND,
                                AuthApi.OTP_VERIFY).permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.decoder(jwtDecoder)))
                .httpBasic(Customizer.withDefaults());
        return http.build();
    }
}
