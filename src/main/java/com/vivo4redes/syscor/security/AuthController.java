package com.vivo4redes.syscor.security;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.vivo4redes.syscor.venda.model.Usuario;
import com.vivo4redes.syscor.venda.repository.UsuarioRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager manager;
    private final JwtService jwt;

    public record LoginRequest(
            @NotBlank
            @JsonAlias({"login", "email", "usuario"})
            String login,

            @NotBlank
            String senha
    ) {}

    public record AlterarSenhaRequest(
            @NotBlank String senhaAtual,
            @NotBlank
            @Size(min = 8, message = "A nova senha deve ter ao menos 8 caracteres")
            String novaSenha
    ) {}

    public record LoginResponse(String token, String tipo, long expiraEmSegundos, boolean primeiroAcesso) {}

    @PostMapping("/alterar-senha")
    public ResponseEntity<?> alterarSenha(
            @AuthenticationPrincipal UserDetails principal,
            @RequestBody @Valid AlterarSenhaRequest req
    ) {
        Usuario usuario = usuarioRepository.findFirstByLoginIgnoreCase(principal.getUsername())
                .orElse(null);
        if (usuario == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("mensagem", "Usuário não encontrado"));
        }

        if (!passwordEncoder.matches(req.senhaAtual(), usuario.getSenhaHash())) {
            // 400 (e não 401): o token está válido, só a senha atual informada está errada.
            // Um 401 faria o front tratar como sessão expirada.
            return ResponseEntity.badRequest().body(Map.of("mensagem", "Senha atual incorreta"));
        }

        if (passwordEncoder.matches(req.novaSenha(), usuario.getSenhaHash())) {
            return ResponseEntity.badRequest()
                    .body(Map.of("mensagem", "A nova senha deve ser diferente da atual"));
        }

        usuario.setSenhaHash(passwordEncoder.encode(req.novaSenha()));
        usuario.setPrimeiroAcesso(false);
        usuarioRepository.save(usuario);

        return ResponseEntity.ok().build();
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest req) {
        Authentication auth = manager.authenticate(
                new UsernamePasswordAuthenticationToken(req.login(), req.senha()));
        UserDetails usuarioDetails = (UserDetails) auth.getPrincipal();

        Usuario usuario = usuarioRepository.findFirstByLoginIgnoreCase(usuarioDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("Usuário não encontrado"));

        return new LoginResponse(
                jwt.gerar(usuarioDetails),
                "Bearer",
                jwt.getExpiracaoSegundos(),
                usuario.isPrimeiroAcesso()
        );
    }
}