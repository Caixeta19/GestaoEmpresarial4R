package com.vivo4redes.syscor.security;

import com.vivo4redes.syscor.venda.model.Usuario;
import com.vivo4redes.syscor.venda.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.text.Normalizer;

/** Carrega o usuário do banco para o Spring Security. Aceita e-mail OU login, sem diferenciar maiúsculas. */
@Service
@RequiredArgsConstructor
public class UsuarioDetailsService implements UserDetailsService {

    private final UsuarioRepository repositorio;
    private final PasswordEncoder passwordEncoder;

    @Override
    public UserDetails loadUserByUsername(String identificador) throws UsernameNotFoundException {
        String id = identificador == null ? "" : identificador.trim();

        // 1. Tenta buscar por e-mail
        java.util.Optional<Usuario> optUsuario = repositorio.findByEmailIgnoreCase(id);

        // 2. Se não encontrar por e-mail, tenta buscar por login
        if (optUsuario.isEmpty()) {
            optUsuario = repositorio.findFirstByLoginIgnoreCase(id);
        }

        // 3. Obtém o usuário ou lança exceção se nenhum for encontrado
        Usuario u = optUsuario.orElseThrow(() -> new UsernameNotFoundException("Usuário não encontrado"));

        // 4. Validação de status (Inativo, cargo ou situação)
        boolean inativo = !u.isAtivo()
                || "INATIVO".equalsIgnoreCase(u.getCargo())
                || "INATIVO".equalsIgnoreCase(u.getSituacao());

        // 5. Tratativa para primeiro acesso (se for true, valida contra a senha temporária "00000000")
        boolean ehPrimeiroAcesso = u.isPrimeiroAcesso();
        String senhaParaValidar = ehPrimeiroAcesso ? passwordEncoder.encode("00000000") : u.getSenhaHash();

        // 6. Retorna o UserDetails configurado para o Spring Security
        return User.withUsername(u.getLogin())
                .password(senhaParaValidar)
                .roles("USER", papel(u.getCargo()))
                .disabled(inativo)
                .build();
    }

    /** "GERENTE DE ÁREA" -> "GERENTE_DE_AREA" (sem acento, sem espaço), para usar em hasRole(...). */
    static String papel(String cargo) {
        if (cargo == null || cargo.isBlank()) {
            return "SEM_CARGO";
        }
        String semAcento = Normalizer.normalize(cargo, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        String papel = semAcento.trim().toUpperCase().replaceAll("[^A-Z0-9]+", "_").replaceAll("^_+|_+$", "");
        return papel.isEmpty() ? "SEM_CARGO" : papel;
    }
}