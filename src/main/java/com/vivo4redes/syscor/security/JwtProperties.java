package com.vivo4redes.syscor.security;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Bind de {@code app.jwt.*} (ver application-{dev,test,prod}.yaml).
 * O secret nunca tem default em produção — sem {@code JWT_SECRET} setado,
 * a aplicação falha ao subir em vez de rodar com uma chave fraca conhecida.
 */
@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(String secret, Duration expiration) {
}