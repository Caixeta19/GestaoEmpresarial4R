package com.vivo4redes.syscor.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Expõe apenas o utilitário de hash (BCrypt) usado na reautenticação do
 * vendedor (US-302). Depende só de spring-security-crypto no pom — NÃO é
 * spring-boot-starter-security, então não ativa filtro de autenticação,
 * JWT ou RBAC. Isso continua adiado para o Épico 0, por decisão do cliente.
 */
@Configuration
public class SegurancaVendasConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}