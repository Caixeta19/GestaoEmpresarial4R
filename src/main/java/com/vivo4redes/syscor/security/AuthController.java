package com.vivo4redes.syscor.security;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.vivo4redes.syscor.venda.model.Usuario;
import com.vivo4redes.syscor.venda.repository.UsuarioRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

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
            @NotBlank String novaSenha
    ) {}

    @PostMapping("/alterar-senha")
    public ResponseEntity<Void> alterarSenha(
            @AuthenticationPrincipal UserDetails principal,
            @RequestBody @Valid AlterarSenhaRequest req
    ) {
        Usuario usuario = usuarioRepository.findFirstByLoginIgnoreCase(principal.getUsername())
                .orElseThrow(() -> new RuntimeException("Usuário Não Encontrado"));

        if (!passwordEncoder.matches(req.senhaAtual(), usuario.getSenhaHash())) {
            throw new BadCredentialsException("Senha atual incorreta");
        }

        String novoHash = passwordEncoder.encode(req.novaSenha());

        usuario.setSenhaHash(novoHash);
        usuario.setPrimeiroAcesso(false);
        usuarioRepository.save(usuario);

        return ResponseEntity.ok().build();
    }

    public record LoginResponse(String token, String tipo, long expiraEmSegundos, boolean primeiroAcesso) {}

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