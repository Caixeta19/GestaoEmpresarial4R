package com.vivo4redes.syscor.security;

import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.Optional;

/** Gera e valida tokens JWT (API do jjwt 0.12.x). */
@Service
public class JwtService {

    private final SecretKey chave;
    private final long expiracaoMs;

    public JwtService(@Value("${syscor.jwt.secret}") String secretBase64,
                      @Value("${syscor.jwt.expiracao-minutos:60}") long expiracaoMinutos) {
        // Falha na subida se a chave tiver menos de 256 bits (comportamento desejado).
        this.chave = Keys.hmacShaKeyFor(Decoders.BASE64.decode(secretBase64));
        this.expiracaoMs = expiracaoMinutos * 60_000;
    }

    public String gerar(UserDetails usuario) {
        Date agora = new Date();
        return Jwts.builder()
                .subject(usuario.getUsername())
                .claim("perfis", usuario.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList())
                .issuedAt(agora)
                .expiration(new Date(agora.getTime() + expiracaoMs))
                .signWith(chave)
                .compact();
    }

    /** Devolve o e-mail (subject) se o token for válido e não estiver expirado. */
    public Optional<String> usuarioDoToken(String token) {
        try {
            return Optional.ofNullable(
                    Jwts.parser().verifyWith(chave).build().parseSignedClaims(token).getPayload().getSubject());
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    public long getExpiracaoSegundos() {
        return expiracaoMs / 1000;
    }
}
