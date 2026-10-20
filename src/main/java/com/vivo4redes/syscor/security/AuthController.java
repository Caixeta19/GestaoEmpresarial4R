package com.vivo4redes.syscor.security;

import com.fasterxml.jackson.annotation.JsonAlias;
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

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthenticationManager manager;
    private final JwtService jwt;

    public record LoginRequest(
            @NotBlank
            @JsonAlias({"login", "email", "usuario"})
            String login,

            @NotBlank
            String senha
    ) {}

    public record LoginResponse(String token, String tipo, long expiraEmSegundos) {}

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest req) {
        Authentication auth = manager.authenticate(
                new UsernamePasswordAuthenticationToken(req.login(), req.senha()));
        UserDetails usuario = (UserDetails) auth.getPrincipal();
        return new LoginResponse(jwt.gerar(usuario), "Bearer", jwt.getExpiracaoSegundos());
    }
}