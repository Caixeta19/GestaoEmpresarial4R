package com.vivo4redes.syscor.security;

import com.vivo4redes.syscor.security.JwtService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.fasterxml.jackson.annotation.JsonProperty;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthenticationManager manager;
    private final JwtService jwt;

    // Atualize o record para aceitar "login" ou "email" vindos do front-end
    public record LoginRequest(
            @NotBlank @JsonProperty(value = "login", required = false) String login,
            @JsonProperty(value = "email", required = false) String email,
            @NotBlank String senha
    ) {
        // Método utilitário para pegar o identificador preenchido (seja login ou email)
        public String getIdentificador() {
            if (login != null && !login.isBlank()) {
                return login.trim();
            }
            return email != null ? email.trim() : "";
        }
    }

    public record LoginResponse(String token, String tipo, long expiraEmSegundos) {}

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest req) {
        // Usa o método getIdentificador() para passar ao Spring Security
        Authentication auth = manager.authenticate(
                new UsernamePasswordAuthenticationToken(req.getIdentificador(), req.senha()));
        UserDetails usuario = (UserDetails) auth.getPrincipal();
        return new LoginResponse(jwt.gerar(usuario), "Bearer", jwt.getExpiracaoSegundos());
    }
}