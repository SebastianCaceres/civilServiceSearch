package com.civilService.search.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(authorize -> authorize
                .anyRequest().permitAll()
            )
            .headers(headers -> headers
                .contentSecurityPolicy(csp -> csp
                    .policyDirectives("default-src 'self'; " +
                                      "script-src 'self'; " +
                                      "worker-src 'self' blob:; " +
                                      "style-src 'self' 'unsafe-inline'; " +
                                      "img-src 'self' data:; " +
                                      "connect-src 'self';")
                )
                // 2. Prevent Clickjacking (X-Frame-Options)
                .frameOptions(frame -> frame.deny())
                // 3. Referrer Policy
                .referrerPolicy(referrer -> referrer.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
            )
            // Disable CSRF since all search and captcha endpoints are public read-only GET requests
            .csrf(csrf -> csrf.disable());

        return http.build();
    }
}
