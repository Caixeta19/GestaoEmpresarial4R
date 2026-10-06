package com.vivo4redes.syscor.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableAsync
@EnableScheduling
public class FinanceiroConfig {

    /**
     * Cadeia isolada só para os webhooks: sem sessão e sem CSRF (quem chama é um banco, não um navegador).
     * A proteção real é a assinatura validada no BancoAdapter. A sua cadeia atual continua valendo para o resto.
     */
    @Bean
    @Order(1)
    SecurityFilterChain webhookChain(HttpSecurity http) throws Exception {
        http.securityMatcher("/api/webhooks/**")
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(a -> a.anyRequest().permitAll());
        return http.build();
    }
}